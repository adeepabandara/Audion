package com.example.audion;

public class BiquadFilter {
    public enum Type { BANDPASS }
    private final float b0, b1, b2, a1, a2;
    private float z1, z2;

    public BiquadFilter(Type type, float sampleRate, float freq, float Q) {
        // see Cookbook formula by Robert Bristow-Johnson
        float w0  = 2f * (float)Math.PI * freq / sampleRate;
        float alpha = (float)Math.sin(w0) / (2f * Q);

        float cosw0 = (float)Math.cos(w0);
        float norm;
        switch(type) {
            case BANDPASS:
                norm = 1f / (1f + alpha);
                b0 =   alpha * norm;
                b1 =   0f;
                b2 =  -alpha * norm;
                a1 =  -2f * cosw0 * norm;
                a2 =   (1f - alpha) * norm;
                break;
            default:
                throw new IllegalArgumentException("Unsupported type");
        }
        z1 = z2 = 0f;
    }

    /** process one sample */
    public float process(float in) {
        float out = b0*in + b1*z1 + b2*z2 - a1*z1 - a2*z2;
        z2 = z1;
        z1 = in;
        return out;
    }
}
