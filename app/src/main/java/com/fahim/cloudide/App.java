package com.fahim.cloudide;

import android.app.Application;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyTheme(this);
    }

    static void applyTheme(Context c) {
        int m = Prefs.sp(c).getInt("night", 2);
        AppCompatDelegate.setDefaultNightMode(m == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : m == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
