/*
 * *****************************************************************
 *
 *  Copyright 2018 DEKRA Testing and Certification, S.A.U. All Rights Reserved.
 *
 *  ******************************************************************
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  ******************************************************************
 */
package org.openconnectivity.otgc.common.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.util.ArrayMap;

import org.iotivity.base.ErrorCode;
import org.iotivity.base.OcConnectivityType;
import org.iotivity.base.OcException;
import org.iotivity.base.OcHeaderOption;
import org.iotivity.base.OcPlatform;
import org.iotivity.base.OcProvisioning;
import org.iotivity.base.OcRepresentation;
import org.iotivity.base.OcResource;
import org.iotivity.base.OcSecureResource;
import org.iotivity.base.OxmType;

import org.iotivity.ca.CaInterface;
import org.iotivity.ca.OicCipher;
import org.openconnectivity.otgc.common.constant.OcfResourceType;
import org.openconnectivity.otgc.common.data.entity.DeviceEntity;
import org.openconnectivity.otgc.common.data.persistence.dao.DeviceDao;
import org.openconnectivity.otgc.common.domain.model.OcDevice;
import org.openconnectivity.otgc.devicelist.domain.model.Device;
import org.openconnectivity.otgc.devicelist.domain.model.DeviceType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import timber.log.Timber;

@Singleton
public class IotivityRepository {
    private static final String DISCOVERY_TIMEOUT_DEFAULT = "5";

    private static final EnumSet<OcConnectivityType> CONNECTIVITY_TYPES;
    static {
        CONNECTIVITY_TYPES = EnumSet.of(OcConnectivityType.CT_ADAPTER_IP);
    }

    private static final List<String> RESOURCE_TYPES_TO_FILTER;
    static {
        RESOURCE_TYPES_TO_FILTER = new ArrayList<>();

        RESOURCE_TYPES_TO_FILTER.add(OcfResourceType.DOXM);
        RESOURCE_TYPES_TO_FILTER.add("oic.r.pstat");
        RESOURCE_TYPES_TO_FILTER.add("oic.r.acl2");
        RESOURCE_TYPES_TO_FILTER.add("oic.r.cred");
        RESOURCE_TYPES_TO_FILTER.add("oic.r.crl");
        RESOURCE_TYPES_TO_FILTER.add("oic.r.csr");
        RESOURCE_TYPES_TO_FILTER.add("oic.r.roles");
        RESOURCE_TYPES_TO_FILTER.add(OcfResourceType.DEVICE);
        RESOURCE_TYPES_TO_FILTER.add(OcfResourceType.PLATFORM);
        RESOURCE_TYPES_TO_FILTER.add(OcfResourceType.INTROSPECTION);
    }

    private final Context ctx;
    private final DeviceDao deviceDao;

    private List<OcSecureResource> mUnownedDevices = new ArrayList<>();

    @Inject
    public IotivityRepository(Context ctx, DeviceDao deviceDao) {
        this.ctx = ctx;
        this.deviceDao = deviceDao;
    }

    public void setRandomPinCallbackListener(OcProvisioning.PinCallbackListener randomPinCallbackListener) {
        try {
            OcProvisioning.setOwnershipTransferCBdata(OxmType.OIC_RANDOM_DEVICE_PIN, randomPinCallbackListener);
        } catch (OcException e) {
            Timber.e("Exception setting random PIN callback listener: %s", e.getMessage());
        }
    }

    public void setDisplayPinListener(OcProvisioning.DisplayPinListener displayPinListener) {
        try {
            OcProvisioning.setDisplayPinListener(displayPinListener);
        } catch (OcException e) {
            Timber.e("Exception setting callback to display PIN: %s", e.getMessage());
        }
    }

    public Observable<Device> scanUnownedDevices() {
        return Observable.create(emitter -> {
            try {
                mUnownedDevices = OcProvisioning.discoverUnownedDevices(getDiscoveryTimeout());

                for (OcSecureResource ocSecureResource : mUnownedDevices) {
                    emitter.onNext(new Device(DeviceType.UNOWNED,
                            ocSecureResource.getDeviceID(),
                            new OcDevice(),
                            ocSecureResource));
                }
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
            emitter.onComplete();
        });
    }

    public Observable<Device> scanOwnedDevices() {
        return findObsResources("")
                .timeout(getDiscoveryTimeout() + 5_00L, TimeUnit.SECONDS)
                .map(ocResources -> ocResources.get(0))
                .filter(ocResource -> {
                    boolean isNotUnowned = true;
                    for (OcSecureResource secureResource : mUnownedDevices) {
                        if (secureResource.getDeviceID().equals(ocResource.getServerId())) {
                            isNotUnowned = false;
                        }
                    }
                    return isNotUnowned;
                })
                .filter(ocResource -> !ocResource.getServerId().equals(getDeviceId()))
                .flatMapSingle(ocResource -> findDeviceInUnicast(ocResource.getServerId()));
            }

            private Observable<List<OcResource>> findObsResources(String host) {
                return Observable.create(emitter -> {
                    try {
                        OcPlatform.findResources(host,
                                OcPlatform.WELL_KNOWN_QUERY,
                                CONNECTIVITY_TYPES,
                        new OcPlatform.OnResourcesFoundListener() {

                            @Override
                            public void onResourcesFound(OcResource[] ocResources) {
                                DeviceEntity device = deviceDao.findById(ocResources[0].getServerId()).blockingGet();
                                List<String> hosts = new ArrayList<>();
                                for (OcResource ocResource : ocResources) {
                                    for (String host : ocResource.getAllHosts()) {
                                        if (!hosts.contains(host)) {
                                            hosts.add(host);
                                        }
                                    }
                                }

                                if (device == null) {
                                    deviceDao.insert(new DeviceEntity(ocResources[0].getServerId(), "", hosts));
                                } else {
                                    deviceDao.update(new DeviceEntity(device.getId(), device.getName(), hosts));
                                }
                                emitter.onNext(Arrays.asList(ocResources));
                            }

                            @Override
                            public void onFindResourcesFailed(Throwable throwable, String s) {
                                if (throwable instanceof OcException) {
                                    OcException ex = (OcException) throwable;
                                    if (ex.getErrorCode() == ErrorCode.ADAPTER_NOT_ENABLED) {
                                        Timber.w(throwable.getLocalizedMessage());
                                    } else {
                                        Timber.e(throwable.getLocalizedMessage());
                                    }
                                } else {
                                    Timber.e(throwable.getLocalizedMessage());
                                }
                            }
                        });
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }

            try {
                Thread.sleep(getDiscoveryTimeout() * 1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Timber.e(e);
            }

            emitter.onComplete();
        });
    }

    public Completable scanHosts() {
        return findObsResources("").ignoreElements();
    }

    private String getDeviceId() {
        ByteBuffer bb = ByteBuffer.wrap(OcPlatform.getDeviceId());
        UUID uuid = new UUID(bb.getLong(), bb.getLong());
        return uuid.toString();
    }

    private OcSecureResource getOcSecureResource(@NonNull String deviceId, boolean filterOwnedByMe) {
        try {
            String host = getDeviceCoapsIpv6Host(deviceId).blockingGet();
            if (host != null) {
                String address;
                int port;
                EnumSet<OcConnectivityType> connType = EnumSet.of(OcConnectivityType.CT_ADAPTER_IP);

                if (host.contains(".")) {
                    connType.add(OcConnectivityType.CT_IP_USE_V4);
                    address = host.split("//")[1].split(":")[0];
                    port = Integer.valueOf(host.split("//")[1].split(":")[1]);
                } else {
                    connType.add(OcConnectivityType.CT_IP_USE_V6);
                    address = host.split("\\[")[1].split("]")[0].replace("%25", "%");
                    port = Integer.valueOf(host.split("]")[1].split(":")[1]);
                }

                return OcProvisioning.discoverSingleDeviceInUnicast(getDiscoveryTimeout(), deviceId, address, OcConnectivityType.CT_ADAPTER_IP);
            }
        } catch (OcException|NullPointerException e) {
            Timber.e(e);
            return null;
        }

        return null;
    }

    public void setPreferredCiphersuite(OicCipher cipher) {
        CaInterface.setCipherSuite(cipher, OcConnectivityType.CT_ADAPTER_IP);
    }

    private Single<OcSecureResource> retrieveOcSecureResource(@NonNull String deviceId, @NonNull OicCipher cipher) {
        return Single.create(emitter -> {
            setPreferredCiphersuite(cipher);
            OcSecureResource ocSecureResource = getOcSecureResource(deviceId, false);
            if (ocSecureResource != null) {
                emitter.onSuccess(ocSecureResource);
            } else {
                emitter.onError(new Exception("Find OcSecureResource has failed"));
            }
        });
    }

    public Single<OcSecureResource> findOcSecureResource(@NonNull String deviceId) {
        return retrieveOcSecureResource(deviceId, OicCipher.TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256)
            .onErrorResumeNext(error -> retrieveOcSecureResource(deviceId, OicCipher.TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8));
    }

    public Single<Device> findDeviceInUnicast(@NonNull String deviceId) {
        return Single.create(emitter -> {
            OcSecureResource ocSecureResource = getOcSecureResource(deviceId, true);
            if (ocSecureResource != null) {
                emitter.onSuccess(new Device(DeviceType.OWNED_BY_SELF, ocSecureResource.getDeviceID(), new OcDevice(), ocSecureResource));
            } else {
                emitter.onSuccess(new Device(DeviceType.OWNED_BY_OTHER, deviceId, new OcDevice(), null));
            }
        });
    }

    public Single<OcSecureResource> findOcSecureResource(@NonNull String deviceId, @NonNull String host) {
        return Single.create(emitter -> {
            try {
                OcSecureResource ocSecureResource =
                        OcProvisioning.discoverSingleDeviceInUnicast(getDiscoveryTimeout(),
                                deviceId, host, OcConnectivityType.CT_ADAPTER_IP);
                emitter.onSuccess(ocSecureResource);
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
        });
    }

    public Single<String> getDeviceCoapIpv6Host(String deviceId) {
        return getDeviceIpv6Host(deviceId, false);
    }

    public Single<String> getDeviceCoapsIpv6Host(String deviceId) {
        return getDeviceIpv6Host(deviceId, true);
    }

    private Single<String> getDeviceIpv6Host(String deviceId, boolean secure) {
        return deviceDao.findById(deviceId)
                .toSingle()
                .map(device -> {
                    String host = null;
                    for (String deviceHost : device.getHosts()) {
                        if (deviceHost.startsWith(secure ? "coaps://" : "coap://")
                                && !deviceHost.contains(".")) {
                            host = deviceHost;
                            break;
                        } else if (deviceHost.startsWith(secure ? "coaps://" : "coap://")) {
                            host = deviceHost;
                        }
                    }

                    return host;
                });
    }

    public Single<OcRepresentation> getDeviceInfo(String host) {
        return Single.create(emitter -> {
            try {
                OcPlatform.getDeviceInfo(host,
                        OcPlatform.WELL_KNOWN_DEVICE_QUERY,
                        CONNECTIVITY_TYPES,
                        emitter::onSuccess);
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
        });
    }

    public Single<OcRepresentation> getPlatformInfo(String host) {
        return Single.create(emitter -> {
            try {
                OcPlatform.getPlatformInfo(host,
                        OcPlatform.WELL_KNOWN_PLATFORM_QUERY,
                        CONNECTIVITY_TYPES,
                        emitter::onSuccess);
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
        });
    }

    public Single<List<OcResource>> findResources(String host) {
        return Single.create(emitter -> {
            try {
                OcPlatform.findResources(host,
                        OcPlatform.WELL_KNOWN_QUERY,
                        CONNECTIVITY_TYPES,
                        new OcPlatform.OnResourcesFoundListener() {
                            @Override
                            public void onResourcesFound(OcResource[] ocResources) {
                                emitter.onSuccess(Arrays.asList(ocResources));
                            }

                            @Override
                            public void onFindResourcesFailed(Throwable throwable, String s) {
                                if (throwable instanceof OcException
                                        && ((OcException)throwable).getErrorCode().equals(ErrorCode.COMM_ERROR)) {
                                    Timber.d(throwable);
                                } else {
                                    Timber.e(throwable);
                                }
                                emitter.onError(throwable);
                            }
                        });
            } catch (OcException ex) {
                Timber.e(ex);
                emitter.onError(ex);
            }
        });
    }


    public Single<OcResource> findResource(String host, String resourceType) {
        return Single.create(emitter -> {
            try {
                OcPlatform.findResource(host,
                        OcPlatform.WELL_KNOWN_QUERY + "?rt=" + resourceType,
                        CONNECTIVITY_TYPES,
                        new OcPlatform.OnResourceFoundListener() {
                            @Override
                            public void onResourceFound(OcResource ocResource) {
                                emitter.onSuccess(ocResource);
                            }

                            @Override
                            public void onFindResourceFailed(Throwable throwable, String s) {
                                if (throwable instanceof OcException
                                        && ((OcException)throwable).getErrorCode().equals(ErrorCode.COMM_ERROR)) {
                                    Timber.w(throwable);
                                } else {
                                    Timber.e(throwable);
                                    emitter.onError(throwable);
                                }
                            }
                        });
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
        });
    }

    public Single<OcRepresentation> get(OcResource ocResource, boolean secured) {
        return Single.create(emitter -> {
            if (secured) {
                String securedHost = getCoapsIpv6Host(ocResource.getAllHosts());
                if (securedHost != null) {
                    ocResource.setHost(securedHost);
                }
            }
            try {
                ocResource.get(
                        new ArrayMap<>(),
                        new OcResource.OnGetListener() {

                            @Override
                            public void onGetCompleted(List<OcHeaderOption> list, OcRepresentation ocRepresentation) {
                                emitter.onSuccess(ocRepresentation);
                            }

                            @Override
                            public void onGetFailed(Throwable throwable) {
                                Timber.e(throwable);
                                emitter.onError(throwable);
                            }
                        }
                );
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
        });
    }

    public Single<OcRepresentation> post(OcResource ocResource, boolean secured, OcRepresentation rep) {
        return Single.create(emitter -> {
            if (secured) {
                String securedHost = getCoapsIpv6Host(ocResource.getAllHosts());
                if (securedHost != null) {
                    ocResource.setHost(securedHost);
                }
            }
            try {
                ocResource.post(
                        rep,
                        new ArrayMap<>(),
                        new OcResource.OnPostListener() {

                            @Override
                            public void onPostCompleted(List<OcHeaderOption> list, OcRepresentation ocRepresentation) {
                                emitter.onSuccess(ocRepresentation);
                            }

                            @Override
                            public void onPostFailed(Throwable throwable) {
                                Timber.e(throwable);
                                emitter.onError(throwable);
                            }
                        }
                );
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
        });
    }

    public Single<OcRepresentation> put(OcResource ocResource, boolean secured, OcRepresentation rep) {
        return Single.create(emitter -> {
            if (secured) {
                String securedHost = getCoapsIpv6Host(ocResource.getAllHosts());
                if (securedHost != null) {
                    ocResource.setHost(securedHost);
                }
            }
            try {
                ocResource.put(
                        rep,
                        new ArrayMap<>(),
                        new OcResource.OnPutListener() {

                            @Override
                            public void onPutCompleted(List<OcHeaderOption> list, OcRepresentation ocRepresentation) {
                                emitter.onSuccess(ocRepresentation);
                            }

                            @Override
                            public void onPutFailed(Throwable throwable) {
                                Timber.e(throwable);
                                emitter.onError(throwable);
                            }
                        }
                );
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }
        });
    }

    public Single<OcResource> constructResource(String host,
                                                String uri,
                                                List<String> resourceTypes,
                                                List<String> interfaceList) {
        return Single.create(emitter -> {
            OcResource ocResource = null;
            try {
                ocResource = OcPlatform.constructResourceObject(host,
                        uri,
                        CONNECTIVITY_TYPES,
                        false,
                        resourceTypes,
                        interfaceList);
            } catch (OcException e) {
                Timber.e(e);
                emitter.onError(e);
            }

            emitter.onSuccess(ocResource);
        });
    }

    public Single<List<String>> getDeviceTypes(String ipAddress) {
        return Single.create(emitter -> {
            try {
                OcPlatform.getDeviceInfo(ipAddress,
                        OcPlatform.WELL_KNOWN_DEVICE_QUERY,
                        CONNECTIVITY_TYPES,
                        ocRepresentation -> {
                            Map<String, Object> values = ocRepresentation.getValues();
                            if(!values.isEmpty()) {
                                List<String> deviceTypes = ocRepresentation.getResourceTypes();
                                for (Iterator<String> iter = deviceTypes.listIterator(); iter.hasNext(); ) {
                                    String deviceType = iter.next();
                                    if (deviceType.equals(OcfResourceType.DEVICE)) {
                                        iter.remove();
                                    }
                                }
                                emitter.onSuccess(deviceTypes);
                            }
                        });
            } catch (OcException e) {
                emitter.onError(e);
            }
        });
    }

    public Single<List<OcResource>> getResourceTypes(String ipAddress) {
        return Single.create(emitter -> {
            try {
                OcPlatform.findResources(ipAddress,
                        OcPlatform.WELL_KNOWN_QUERY,
                        CONNECTIVITY_TYPES,
                        new OcPlatform.OnResourcesFoundListener() {
                            @Override
                            public void onResourcesFound(OcResource[] resources) {
                                List<OcResource> resourceList = new ArrayList<>();
                                for (OcResource resource : resources) {
                                    for (String resourceType : resource.getResourceTypes()) {
                                        if (!RESOURCE_TYPES_TO_FILTER.contains(resourceType)
                                                && !resourceType.startsWith("oic.d.")) {
                                            resourceList.add(resource);
                                        }
                                    }
                                }

                                emitter.onSuccess(resourceList);
                            }

                            @Override
                            public void onFindResourcesFailed(Throwable ex, String uri) {
                                // TODO
                            }
                        });
            } catch (OcException e) {
                if (e.getErrorCode() == ErrorCode.ADAPTER_NOT_ENABLED) {
                    Timber.w(e.getLocalizedMessage());
                } else {
                    Timber.e(e.getLocalizedMessage());
                }
            }
        });
    }

    public Single<String> getDeviceName(String deviceId) {
        return deviceDao.findById(deviceId)
                .toSingle()
                .map(DeviceEntity::getName);
    }

    public Completable setDeviceName(String deviceId, String deviceName) {
        return Completable.fromAction(() -> {
            deviceDao.updateDeviceName(deviceId, deviceName);
        });
    }

    private String getCoapsIpv6Host(@NonNull List<String> hosts) {
        String coapsIpv6Host = null;
        for (String host : hosts) {
            if (host.startsWith("coaps") && !host.contains(".")) {
                coapsIpv6Host = host;
                break;
            }
        }

        return coapsIpv6Host;
    }

    public int getDiscoveryTimeout() {
        SharedPreferences wmbPreference = PreferenceManager.getDefaultSharedPreferences(ctx);

        return Integer.parseInt(
                wmbPreference.getString("discovery_timeout", DISCOVERY_TIMEOUT_DEFAULT));
    }
}
