package org.openconnectivity.otgc.remote;

import android.os.Bundle;

import org.openconnectivity.otgc.R;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if(findViewById(R.id.settings_fragment) != null){
            if(savedInstanceState !=null)
                return;

            getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
        }
    }
}
