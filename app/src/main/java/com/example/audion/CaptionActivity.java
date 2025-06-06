// File: app/src/main/java/com/example/audion/CaptionActivity.java
package com.example.audion;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CaptionActivity extends AppCompatActivity implements RecognitionListener {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private final String[] permissions = { Manifest.permission.RECORD_AUDIO };

    private TextView captionTextView;
    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caption);

        // 1) Bind the TextView for displaying captions
        captionTextView = findViewById(R.id.captionTextView);

        // 2) Request RECORD_AUDIO permission at runtime
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        // User's response will arrive in onRequestPermissionsResult(...)
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted =
                (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
        if (!permissionToRecordAccepted) {
            // If the user denies, show a toast and close this activity
            Toast.makeText(this, "Audio permission is required for captions", Toast.LENGTH_LONG).show();
            finish();
        } else {
            // Permission granted: initialize the Vosk model and start recognition
            initCaptioning();
        }
    }

    /**
     * Copy a folder (and all its contents) from assets into destDir on internal storage.
     * 
     * @param assetFolderName  The path under assets (e.g., "vosk-model-small-en-us-0.15")
     * @param destDir          The File object pointing to the destination directory
     * @throws IOException     If any I/O error occurs during copying
     */
    private void copyAssetFolder(String assetFolderName, File destDir) throws IOException {
        String[] assets = getAssets().list(assetFolderName);
        if (assets == null) {
            // No assets to copy (shouldn't happen if folder exists)
            return;
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Cannot create destination directory: " + destDir.getAbsolutePath());
        }
        for (String asset : assets) {
            String fullAssetPath = assetFolderName + "/" + asset;
            String[] subAssets = getAssets().list(fullAssetPath);
            if (subAssets != null && subAssets.length > 0) {
                // It's a folder; recurse
                File nextDestDir = new File(destDir, asset);
                copyAssetFolder(fullAssetPath, nextDestDir);
            } else {
                // It's a file; copy its contents
                try (InputStream in = getAssets().open(fullAssetPath);
                     FileOutputStream out = new FileOutputStream(new File(destDir, asset))) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }
            }
        }
    }

    /**
     * Loads the Vosk model from assets into internal storage, then initializes the Recognizer.
     * Finally, starts the SpeechService on the UI thread.
     */
    private void initCaptioning() {
        new Thread(() -> {
            try {
                // 1) Destination on internal storage
                File modelDir = new File(getFilesDir(), "vosk-model-small-en-us-0.15");

                // 2) Copy from assets if it doesn't already exist
                if (!modelDir.exists()) {
                    copyAssetFolder("vosk-model-small-en-us-0.15", modelDir);
                }

                // 3) Initialize Vosk Model using the absolute path
                model = new Model(modelDir.getAbsolutePath());
                recognizer = new Recognizer(model, 16000.0f);

                // 4) On successful load, start SpeechService on UI thread
                runOnUiThread(this::startSpeechService);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    captionTextView.setText("Model initialization failed: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Starts Vosk's SpeechService, which opens the microphone and begins recognition.
     * Catches any Exception, including IOException if the underlying setup fails.
     */
    private void startSpeechService() {
        try {
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);
            captionTextView.setText("Listening for speech...");
        } catch (Exception e) {
            e.printStackTrace();
            captionTextView.setText("SpeechService failed: " + e.getMessage());
        }
    }

    // -----------------------------------------
    // The five methods required by RecognitionListener
    // -----------------------------------------

    /** Interim (partial) transcription result; JSON: {"partial":"..."} */
    @Override
    public void onPartialResult(String hypothesis) {
        try {
            JSONObject obj = new JSONObject(hypothesis);
            String partial = obj.getString("partial");
            runOnUiThread(() -> captionTextView.setText(partial));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** Final result for a segment; JSON: {"text":"..."} */
    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject obj = new JSONObject(hypothesis);
            String text = obj.getString("text");
            runOnUiThread(() -> captionTextView.setText(text));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** Called when the recognizer produces a fully final transcription; JSON: {"text":"..."} */
    @Override
    public void onFinalResult(String hypothesis) {
        try {
            JSONObject obj = new JSONObject(hypothesis);
            String text = obj.getString("text");
            runOnUiThread(() -> captionTextView.setText(text));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** Called if any exception occurs inside Vosk or SpeechService */
    @Override
    public void onError(Exception e) {
        e.printStackTrace();
        runOnUiThread(() -> captionTextView.setText("Recognition error: " + e.getMessage()));
    }

    /** Called when Vosk times out (no speech detected within a certain time) */
    @Override
    public void onTimeout() {
        runOnUiThread(() -> captionTextView.setText("No speech detected."));
    }

    // -----------------------------------------
    // Clean up all resources in onDestroy()
    // -----------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.cancel();
            speechService.shutdown();
            speechService = null;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
    }
}
