package com.example.audion.diarization;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Utility class for reading WAV audio files.
 * Converted from the Kotlin ReadWaveFile class in the Sherpa-Onnx project.
 */
public class WaveFileReader {
    private static final String TAG = "WaveFileReader";
    
    private WaveFileReader() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Reads a WAV file from the given URI.
     * 
     * @param context Android context
     * @param uri URI of the WAV file
     * @return Array of audio samples (mono, float)
     * @throws IOException If the file cannot be read or is not a valid WAV file
     */
    public static float[] readWaveFile(Context context, Uri uri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for URI: " + uri);
        }
        
        try {
            byte[] data = readAllBytes(inputStream);
            return readWaveFile(data);
        } finally {
            inputStream.close();
        }
    }
    
    /**
     * Reads a WAV file from the given file path.
     * 
     * @param filePath Path to the WAV file
     * @return Array of audio samples (mono, float)
     * @throws IOException If the file cannot be read or is not a valid WAV file
     */
    public static float[] readWaveFile(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             DataInputStream dis = new DataInputStream(fis)) {
            
            byte[] data = readAllBytes(fis);
            return readWaveFile(data);
        }
    }
    
    /**
     * Reads a WAV file from the given byte array.
     * 
     * @param data Byte array containing WAV file data
     * @return Array of audio samples (mono, float)
     * @throws IOException If the data is not a valid WAV file
     */
    public static float[] readWaveFile(byte[] data) throws IOException {
        // Check minimum file size
        if (data.length < 44) {
            throw new IOException("WAV file too small");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        // WAV file format validation
        int chunkID = buffer.getInt(); // RIFF
        if (chunkID != 0x46464952) { // "RIFF" in little-endian
            throw new IOException("Not a RIFF file");
        }
        
        int chunkSize = buffer.getInt(); // File size - 8
        
        int format = buffer.getInt(); // WAVE
        if (format != 0x45564157) { // "WAVE" in little-endian
            throw new IOException("Not a WAVE file");
        }
        
        int subchunk1ID = buffer.getInt(); // fmt 
        if (subchunk1ID != 0x20746D66) { // "fmt " in little-endian
            throw new IOException("Invalid WAV format: missing fmt chunk");
        }
        
        int subchunk1Size = buffer.getInt(); // 16 for PCM
        if (subchunk1Size != 16) {
            throw new IOException("Only PCM format is supported");
        }
        
        int audioFormat = buffer.getShort() & 0xFFFF; // 1 for PCM
        if (audioFormat != 1) {
            throw new IOException("Only PCM format is supported");
        }
        
        int numChannels = buffer.getShort() & 0xFFFF;
        int sampleRate = buffer.getInt();
        int byteRate = buffer.getInt(); // SampleRate * NumChannels * BitsPerSample/8
        int blockAlign = buffer.getShort() & 0xFFFF; // NumChannels * BitsPerSample/8
        int bitsPerSample = buffer.getShort() & 0xFFFF; // 8, 16, etc.
        
        // Find the data chunk
        int subchunk2ID = buffer.getInt(); // data
        while (subchunk2ID != 0x61746164) { // "data" in little-endian
            int subchunk2Size = buffer.getInt();
            buffer.position(buffer.position() + subchunk2Size);
            
            if (buffer.position() >= buffer.capacity() - 8) {
                throw new IOException("No data chunk found in WAV file");
            }
            
            subchunk2ID = buffer.getInt();
        }
        
        int subchunk2Size = buffer.getInt(); // Data size
        
        Log.i(TAG, String.format(
                "WAV file: channels=%d, sample_rate=%d, bits_per_sample=%d",
                numChannels, sampleRate, bitsPerSample));
        
        // Process the audio data
        float[] samples;
        
        if (bitsPerSample == 16) {
            // Prepare a properly sized buffer to hold all samples as shorts
            ShortBuffer shortBuffer = buffer.asShortBuffer();
            int sampleCount = subchunk2Size / (bitsPerSample / 8);
            short[] shortSamples = new short[sampleCount];
            shortBuffer.get(shortSamples);
            
            // Convert to float and handle multiple channels (mix to mono)
            if (numChannels == 1) {
                samples = new float[sampleCount];
                for (int i = 0; i < sampleCount; i++) {
                    samples[i] = shortSamples[i] / 32768.0f;
                }
            } else {
                int monoSampleCount = sampleCount / numChannels;
                samples = new float[monoSampleCount];
                for (int i = 0; i < monoSampleCount; i++) {
                    float sum = 0;
                    for (int ch = 0; ch < numChannels; ch++) {
                        sum += shortSamples[i * numChannels + ch];
                    }
                    samples[i] = (sum / numChannels) / 32768.0f;
                }
            }
        } else if (bitsPerSample == 8) {
            // 8-bit samples
            int sampleCount = subchunk2Size;
            
            if (numChannels == 1) {
                samples = new float[sampleCount];
                for (int i = 0; i < sampleCount; i++) {
                    byte b = buffer.get();
                    samples[i] = ((b & 0xFF) - 128) / 128.0f;
                }
            } else {
                int monoSampleCount = sampleCount / numChannels;
                samples = new float[monoSampleCount];
                for (int i = 0; i < monoSampleCount; i++) {
                    float sum = 0;
                    for (int ch = 0; ch < numChannels; ch++) {
                        byte b = buffer.get();
                        sum += ((b & 0xFF) - 128);
                    }
                    samples[i] = (sum / numChannels) / 128.0f;
                }
            }
        } else {
            throw new IOException("Unsupported bits per sample: " + bitsPerSample);
        }
        
        return samples;
    }
    
    /**
     * Reads all bytes from an input stream.
     * 
     * @param is Input stream to read
     * @return Byte array containing all bytes from the input stream
     * @throws IOException If the stream cannot be read
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        
        return buffer.toByteArray();
    }
} 