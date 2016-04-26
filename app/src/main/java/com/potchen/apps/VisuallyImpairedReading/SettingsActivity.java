package com.potchen.apps.VisuallyImpairedReading;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.widget.ListView;

/**
 * Created by Joe on 4/18/16.
 */
public class SettingsActivity extends PreferenceActivity {

    ListView listView;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        //getFragmentManager().beginTransaction().replace(R.id.customContent, new MyPreferenceFragment());

        addPreferencesFromResource(R.xml.preferences);

    }
    /*
    public static class MyPreferenceFragment extends PreferenceFragment{
        @Override
        public void onCreate(final Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
*/
}
