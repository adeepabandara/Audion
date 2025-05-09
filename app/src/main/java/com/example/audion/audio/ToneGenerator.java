package com.example.audion.audio;

public class ToneGenerator {

    /**
     * Generates a short PCM buffer of a pure sine wave at a given amplitude.
     *
     * @param sampleRate   e.g., 44100 Hz
     * @param chunkMs      how long this chunk is, in milliseconds (e.g., 50)
     * @param frequency    the sine wave frequency in Hz (e.g., 1000)
     * @param amplitude    the amplitude [0.0..1.0] for this chunk
     * @return short[]     16-bit PCM buffer
     */
    public static short[] generateSineWaveChunk(int sampleRate, int chunkMs, int frequency, double amplitude) {
        int numSamples = (int) ((chunkMs / 1000.0) * sampleRate);
        short[] buffer = new short[numSamples];
        double twoPiF = 2.0 * Math.PI * frequency;

        for (int i = 0; i < numSamples; i++) {
            double angle = twoPiF * i / sampleRate;
            double sampleVal = amplitude * Math.sin(angle);
            buffer[i] = (short) (sampleVal * Short.MAX_VALUE);
        }
        return buffer;
    }
}
