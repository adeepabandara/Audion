package com.example.audion;

public class Volume {
    /** Convert 0–100% into 0.0–1.0 amplitude */
    public static float fromPercent(int pct) {
        return Math.max(0f, Math.min(1f, pct / 100f));
    }
}