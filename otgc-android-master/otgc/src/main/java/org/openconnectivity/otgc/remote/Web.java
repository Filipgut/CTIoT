package org.openconnectivity.otgc.remote;/*
package com.apk.remote;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.apk.remote.temperature.GetCpuTempAndroid;

public class MainActivity extends AppCompatActivity {

    GetCpuTempAndroid tempAndroid;
    String tempStr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.web);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);


        final WebView webView = (WebView)findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        String url = "file:///android_asset/html/index.html?temp=" + tempStr;
        webView.loadUrl(url);

            Thread t = new Thread() {
                @Override
                public void run() {
                    while (!isInterrupted()) {
                        try {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tempAndroid = new GetCpuTempAndroid();
                                    Float temp = tempAndroid.getCpuTemp();
                                    tempStr = temp.toString();
                                    String url = "file:///android_asset/html/index.html?temp=" + tempStr;
                                    webView.loadUrl(url);

                                }
                            });
                            Thread.sleep(60000);  //1000ms = 1 sec
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            t.start();


    }
}
*/
