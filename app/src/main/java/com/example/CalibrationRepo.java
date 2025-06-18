package com.example.audion;

import android.content.Context;
import android.content.SharedPreferences;

public class CalibrationRepo {
    // Give PREF_NAME a real string literal
    private static final String PREF_NAME = "CalibrationPrefs";

    // Lazily fetch SharedPreferences each time
    public static int getBaseline(Context ctx, String ear) {
        SharedPreferences prefs =
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // default to 50 if no value saved yet
        return prefs.getInt(ear + "_baseline", 50);
    }

    public static int getThreshold(Context ctx, String ear, int freq) {
        // for now we just return the baseline; replace with your real logic
        return getBaseline(ctx, ear);
    }
}
