package org.openconnectivity.otgc.remote;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.openconnectivity.otgc.R;
import org.openconnectivity.otgc.devicelist.presentation.view.DeviceListActivity;
import org.openconnectivity.otgc.splash.presentation.view.SplashActivity;

import java.text.DateFormat;
import java.util.Date;

import androidx.annotation.RequiresApi;

public class MainActivity extends Activity implements SensorEventListener {

    private final static String NOT_SUPPORTED_MESSAGE = "Sorry, sensor not available for this device.";

    private SensorManager mSensorManager;
    private Sensor pressure;
    private TextView pressureView;
    private Sensor mTemperature;
    private TextView temperature;
    private TextView setTemperature;
    private Float setTempRoom = new Float(20.0);
    private int bright = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        pressureView = (TextView) findViewById(R.id.pressure);

        final LinearLayout linearLayout = (LinearLayout) findViewById(R.id.right);

        final TextView time = (TextView) findViewById(R.id.time);

        final SeekBar seekBar1 = (SeekBar) findViewById(R.id.seek_bar_1);

        final ImageButton brightness = (ImageButton) findViewById(R.id.brightness);

        final ImageButton settings = (ImageButton) findViewById(R.id.settings);

        final ImageButton more = (ImageButton) findViewById(R.id.more);


        temperature = (TextView) findViewById(R.id.temperature);
        setTemperature = (TextView) findViewById(R.id.set_temperature);
        setTemperature.setText("Set to: " + setTempRoom.toString());
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mTemperature = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE); // requires API level 14.
        }
        if (mTemperature == null) {
            temperature.setTextSize(20);
            temperature.setText(NOT_SUPPORTED_MESSAGE);
        }

        seekBar1.incrementProgressBy(5);
        seekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                i /= 5;
                i *= 5;
                Float progress = Float.valueOf(i);
                setTempRoom = progress / 10;
                setTemperature.setText("Set to: " + setTempRoom.toString());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        brightness.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                Context context = getApplicationContext();

                boolean settingsCanWrite = hasWriteSettingsPermission(context);

                if (!settingsCanWrite) {
                    changeWriteSettingsPermission(context);
                } else {
                    if (bright < 250) {
                        bright += 60;
                        changeScreenBrightness(context, bright);
                    } else {
                        bright = 10;
                        changeScreenBrightness(context, bright);
                    }

                }

            }

        });

        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent otgc = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivity(otgc);
            }
        });
        settings.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View v) {
                Intent settings = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settings);
            }
        });

        final String[] currentDateTimeString = {DateFormat.getDateTimeInstance().format(new Date())};
        time.setText(currentDateTimeString[0]);

        Thread t = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentDateTimeString[0] = DateFormat.getDateTimeInstance().format(new Date());
                                time.setText(currentDateTimeString[0]);
                            }
                        });
                        Thread.sleep(1000);  //1000ms = 1 sec
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        t.start();

        mSensorManager = (SensorManager)

                getSystemService(Context.SENSOR_SERVICE);

        pressure = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        pressureView.setText(String.format("%.3f mbar", pressure.getPower()));

    }

    private void changeScreenBrightness(Context context, int screenBrightnessValue) {
        Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        // Apply the screen brightness value to the system, this will change the value in Settings ---> Display ---> Brightness level.
        // It will also change the screen brightness for the device.
        Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, screenBrightnessValue);

        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.screenBrightness = screenBrightnessValue / 255f;
        window.setAttributes(layoutParams);
    }


    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mTemperature, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        String tempStr;
        float ambient_temperature = event.values[0];
        if (Math.abs(ambient_temperature) < 10)
            tempStr = String.valueOf(ambient_temperature).substring(0, 3) + getResources().getString(R.string.celsius);
        else
            tempStr = String.valueOf(ambient_temperature).substring(0, 4) + getResources().getString(R.string.celsius);

        temperature.setText("" + tempStr);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @TargetApi(Build.VERSION_CODES.M)
    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean hasWriteSettingsPermission(Context context) {
        boolean ret = true;
        // Get the result from below code.
        ret = Settings.System.canWrite(context);
        return ret;
    }

    private void changeWriteSettingsPermission(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        context.startActivity(intent);
    }


}
