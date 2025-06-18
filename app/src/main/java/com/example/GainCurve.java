package com.example.audion;

public class GainCurve {
    /** Convert dB to linear gain: amp = 10^(dB/20) */
    public static float dbToAmp(int dB) {
        return (float) Math.pow(10, dB / 20.0);
    }
}