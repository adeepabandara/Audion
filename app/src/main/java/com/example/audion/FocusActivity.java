package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;
import android.content.Intent;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.airbnb.lottie.LottieAnimationView;

import com.example.audion.diarization.DirectDiarizationManager;
import com.example.audion.diarization.SpeakerDiarizationManager;
import com.google.android.material.button.MaterialButton;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FocusActivity extends AppCompatActivity
        implements DirectDiarizationManager.DiarizationListener,
                   GlobalSpeakersAdapter.SpeakerSelectionListener {

    private static final String TAG = "FocusActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
    private static final int BUFFER_SIZE_IN = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    private static final int BUFFER_SIZE_OUT = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    private static final int ENROLLMENT_DURATION_SECONDS = 30;
    private static final int ENROLLMENT_SAMPLE_RATE = 16000;
    private static final int ENROLLMENT_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            ENROLLMENT_SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    private static final int ENROLLMENT_SAMPLE_COUNT =
            ENROLLMENT_SAMPLE_RATE * ENROLLMENT_DURATION_SECONDS;

    // rotating scan messages/images
    private final String[] scanMessages = {
        "Calibrating microphone…",
        "Analyzing ambient noise…",
        "Mapping speaker profiles…",
        "Finalizing scan…"
    };
    private final int[] scanImages = {
        R.drawable.scan1,
        R.drawable.scan1,
        R.drawable.scan1,
        R.drawable.scan1
    };

    private final Handler scanTextHandler = new Handler(Looper.getMainLooper());
    private Runnable scanTextRunnable;
    private int scanTextIndex = 0;

    private RNNoise rnnoise;
    private DirectDiarizationManager diarizationManager;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isProcessing = false;
    private ProcessThread processThread;

    private boolean speakerIsolationEnabled = false;
    private EnrollmentActivity.EnrolledSpeaker selectedEnrolledSpeaker = null;
    private int currentChunkId = 0;

    private AudioRecord enrollmentRecorder;
    private boolean isEnrolling = false;
    private float[] enrollmentBuffer = new float[ENROLLMENT_SAMPLE_COUNT];
    private int recordedSamples = 0;

    private List<EnrollmentActivity.EnrolledSpeaker> enrolledSpeakers = new ArrayList<>();
    private RecyclerView enrolledSpeakersRecyclerView;
    private EnrolledSpeakersAdapter enrolledSpeakersAdapter;

    private RecyclerView globalSpeakersRecyclerView;
    private GlobalSpeakersAdapter globalSpeakersAdapter;

    private Handler mainHandler;
    private TextView statusText;
    private MaterialButton enrollButton;

    // inline UI
    private FrameLayout defaultPanel;
    private View defaultView, scanView, diarizationView;
    private ImageView scanImageView, diarizationImageInline;
    private TextView scanTextView, diarizationStatusInline;
    private ProgressBar scanProgressInline, diarizationProgressInline;

    // amplification
    private SeekBar amplificationSeekBar;
    private float amplificationFactor = 1.0f;

    // start/stop button
    private MaterialButton toggleButton;

    // Normal vs Focus toggle
    private MaterialButton normalButton, focusButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_focus);

        // 1) Bind our loading overlay and show it immediately
        View loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingOverlay.setVisibility(View.VISIBLE);

        // 2) Bind the rest of the UI and disable interactions until init finishes
        defaultPanel                 = findViewById(R.id.defaultPanel);
        enrolledSpeakersRecyclerView = findViewById(R.id.enrolledSpeakersRecyclerView);
        globalSpeakersRecyclerView   = findViewById(R.id.globalSpeakersRecyclerView);
        toggleButton                 = findViewById(R.id.toggleButton);
        enrollButton                 = findViewById(R.id.enrollButton);
        normalButton                 = findViewById(R.id.normal);
        focusButton                  = findViewById(R.id.focus);
        statusText                   = findViewById(R.id.statusText);
        amplificationSeekBar         = findViewById(R.id.seekBar);

        showDefaultPanel();

        // Amplification seekbar
        amplificationSeekBar.setMax(100);
        amplificationSeekBar.setProgress(50);
        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                amplificationFactor = p / 50f;
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        toggleButton.setVisibility(View.INVISIBLE);
        toggleButton.setEnabled(false);
        enrollButton.setEnabled(false);

        mainHandler = new Handler(Looper.getMainLooper());

        // 3) Kick off initialization in the background
        new Thread(() -> {
            rnnoise = new RNNoise();
            rnnoise.initialize();

            initializeDiarizationManager();

            runOnUiThread(() -> {
                loadingOverlay.setVisibility(View.GONE);
                toggleButton.setEnabled(true);
                enrollButton.setEnabled(true);
                statusText.setText("");
                bindAdaptersAndListeners();
            });
        }).start();
    }

    private void bindAdaptersAndListeners() {
        // — Global speakers list
        globalSpeakersAdapter = new GlobalSpeakersAdapter(this, new HashMap<>());
        globalSpeakersAdapter.setSelectionListener(this);
        globalSpeakersAdapter.setDiarizationManager(diarizationManager);
        globalSpeakersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        globalSpeakersRecyclerView.setAdapter(globalSpeakersAdapter);
        globalSpeakersRecyclerView.setVisibility(View.GONE);

        // — Enrolled speakers list
        enrolledSpeakersAdapter = new EnrolledSpeakersAdapter(this, enrolledSpeakers);
        enrolledSpeakersAdapter.setOnSpeakerSelectListener((speaker, pos) -> {
            selectedEnrolledSpeaker = speaker;
            defaultPanel.removeAllViews();
            toggleButton.setVisibility(View.VISIBLE);
            toggleButton.setEnabled(true);
            updateToggleUi(false);
        });
        enrolledSpeakersAdapter.setSpeakerActionListener(position -> {
            enrolledSpeakers.remove(position);
            enrolledSpeakersAdapter.notifyItemRemoved(position);
        });
        enrolledSpeakersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        enrolledSpeakersRecyclerView.setAdapter(enrolledSpeakersAdapter);
        loadEnrolledSpeakers();

        // — Scan/Enrollment button
        enrollButton.setOnClickListener(v -> {
            if (!isEnrolling) startEnrollment();
            else               stopEnrollment();
        });

        // — Start/Stop processing toggle
        toggleButton.setOnClickListener(v -> toggleProcessing());

        // — Normal vs Focus mode buttons
        normalButton.setOnClickListener(v -> {
            updateToggleUi(true);
            startActivity(new Intent(this, HomeActivity.class));
        });
        focusButton.setOnClickListener(v -> updateToggleUi(false));
    }

    private void showDefaultPanel() {
        defaultPanel.removeAllViews();
        defaultView = getLayoutInflater().inflate(R.layout.default_inline, defaultPanel, false);
        defaultPanel.addView(defaultView);
    }

    private void showScanInline() {
        defaultPanel.removeAllViews();
        scanView = getLayoutInflater().inflate(R.layout.scan_inline, defaultPanel, false);
        LottieAnimationView scanAnim = scanView.findViewById(R.id.scanAnimation);
        scanProgressInline = scanView.findViewById(R.id.scanProgress);
        scanTextView = scanView.findViewById(R.id.scanText);
        defaultPanel.addView(scanView);

        scanTextIndex = 0;
        scanTextView.setText(scanMessages[scanTextIndex]);
        scanProgressInline.setProgress((scanTextIndex + 1) * 100 / scanMessages.length);
        long interval = ENROLLMENT_DURATION_SECONDS * 1000L / scanMessages.length;
        scanTextRunnable = new Runnable() {
            @Override public void run() {
                scanTextIndex = (scanTextIndex + 1) % scanMessages.length;
                scanTextView.setText(scanMessages[scanTextIndex]);
                scanProgressInline.setProgress((scanTextIndex + 1) * 100 / scanMessages.length);
                scanTextHandler.postDelayed(this, interval);
            }
        };
        scanTextHandler.postDelayed(scanTextRunnable, interval);
    }

    private void showDiarizationInline() {
        defaultPanel.removeAllViews();
        diarizationView = getLayoutInflater()
                .inflate(R.layout.diarization_inline, defaultPanel, false);
        diarizationProgressInline = diarizationView.findViewById(R.id.diarizationProgress);
        diarizationStatusInline = diarizationView.findViewById(R.id.diarizationStatus);
        defaultPanel.addView(diarizationView);
    }

    private void initializeDiarizationManager() {
        try {
            diarizationManager = new DirectDiarizationManager(this);
            diarizationManager.setListener(this);
            boolean inited = false;
            for (int i = 0; i < 3 && !inited; i++) {
                inited = diarizationManager.initialize();
                if (!inited) Thread.sleep(100);
            }
            if (!inited) Log.w(TAG, "Diarization initialization failed");
        } catch (Exception e) {
            Log.e(TAG, "Error init diarization", e);
        }
    }

    private void toggleProcessing() {
        if (isProcessing) stopProcessing();
        else {
            if (selectedEnrolledSpeaker == null) {
                Toast.makeText(this,
                    "Please select an enrolled speaker first",
                    Toast.LENGTH_SHORT).show();
                return;
            }
            startProcessing();
        }
    }

    private void startProcessing() {
        if (!hasPermissions()) { requestPermissions(); return; }
        if (isProcessing) return;

        try {
            if (diarizationManager != null && !diarizationManager.isInitialized())
                diarizationManager.initialize();

            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, BUFFER_SIZE_IN);
            audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .build())
                .setBufferSizeInBytes(BUFFER_SIZE_OUT)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

            audioRecord.startRecording();
            audioTrack.play();
            isProcessing = true;
            speakerIsolationEnabled = true;
            updateToggleUi(true);
            statusText.setText("Focusing on " + selectedEnrolledSpeaker.getName());

            processThread = new ProcessThread();
            processThread.setPriority(Thread.MAX_PRIORITY);
            processThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting processing", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProcessing() {
        isProcessing = false;
        speakerIsolationEnabled = false;
        if (processThread != null) {
            try { processThread.join(1000); } catch (Exception ignored) {}
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
        updateToggleUi(false);
    }

    private void updateToggleUi(boolean streaming) {
        if (streaming) {
            toggleButton.setText("Stop");
            toggleButton.setIconResource(R.drawable.ic_stop);
            toggleButton.setBackgroundTintList(
                getResources().getColorStateList(R.color.red_circle)
            );
        } else {
            toggleButton.setText("Start");
            toggleButton.setIconResource(R.drawable.ic_play);
            toggleButton.setBackgroundTintList(
                getResources().getColorStateList(R.color.green_circle)
            );
        }
    }

    // ----------------------------------------------------------------
    //  Diarization callbacks
    // ----------------------------------------------------------------

    @Override public void onSpeakerSelected(int speakerId) { }
    @Override public void onSpeakerDeselected() { }

    @Override
    public void onSpeakersDetected(List<DirectDiarizationManager.SpeakerInfo> s) {
        // no-op
    }

    @Override
    public void onSpeakersHistoryUpdated(List<List<DirectDiarizationManager.SpeakerInfo>> history) {
        runOnUiThread(() -> {
            // ** FIX: hand the new history over to the adapter **
            globalSpeakersAdapter.updateGlobalSpeakers(history);
            // only show if there’s at least one entry
            globalSpeakersRecyclerView.setVisibility(
                history.isEmpty() ? View.GONE : View.VISIBLE
            );
        });
    }

    @Override public void onBufferFillProgress(float p) { }

    @Override
    public void onProcessingProgress(float p) {
        if (diarizationProgressInline != null) {
            int percent = Math.round(p * 100);
            runOnUiThread(() -> diarizationProgressInline.setProgress(percent));
        }
    }

    @Override
    public void onDiarizationError(String m) {
        mainHandler.post(() ->
            Toast.makeText(this, "Diarization error: " + m, Toast.LENGTH_SHORT).show()
        );
    }

    private class ProcessThread extends Thread {
        private static final int FRAME_SIZE = RNNoise.FRAME_SIZE;
        @Override public void run() {
            float[] inBuf  = new float[BUFFER_SIZE_IN/4];
            float[] outBuf = new float[BUFFER_SIZE_IN/4];
            while (isProcessing) {
                int r = audioRecord.read(inBuf, 0, inBuf.length, AudioRecord.READ_BLOCKING);
                if (r < 0) break;
                System.arraycopy(inBuf, 0, outBuf, 0, r);
                for (int i = 0; i < r; i += FRAME_SIZE) {
                    if (r - i >= FRAME_SIZE) {
                        float[] fb = new float[FRAME_SIZE];
                        System.arraycopy(outBuf, i, fb, 0, FRAME_SIZE);
                        RNNoise.ProcessResult res = rnnoise.processFrame(fb);
                        System.arraycopy(res.audio, 0, outBuf, i, FRAME_SIZE);
                    }
                }
                if (diarizationManager != null) {
                    diarizationManager.processAudioFrame(outBuf);
                    currentChunkId = diarizationManager.getCurrentChunkId();
                }
                if (speakerIsolationEnabled && selectedEnrolledSpeaker != null) {
                    isolateEnrolledSpeaker(outBuf, r);
                }
                for (int i = 0; i < r; i++) {
                    outBuf[i] *= amplificationFactor;
                }
                audioTrack.write(outBuf, 0, r, AudioTrack.WRITE_BLOCKING);
            }
        }
    }

    private void isolateEnrolledSpeaker(float[] buf, int size) {
        try {
            for (var s : diarizationManager.getActiveSpeakers(currentChunkId)) {
                if (diarizationManager.isSpeakerMatchingEnrollment(
                        s.getGlobalId(), selectedEnrolledSpeaker.getEmbedding())) {
                    return;
                }
            }
            for (int i = 0; i < size; i++) buf[i] = 0f;
        } catch (Exception ignored) {}
    }

    private void startEnrollment() {
        if (!hasPermissions()) { requestPermissions(); return; }

        selectedEnrolledSpeaker = null;
        toggleButton.setVisibility(View.INVISIBLE);
        toggleButton.setEnabled(false);
        enrolledSpeakers.clear();
        enrolledSpeakersAdapter.notifyDataSetChanged();

        recordedSamples = 0;
        isEnrolling = true;
        enrollButton.setText("Stop Scan");
        showScanInline();

        enrollmentRecorder = new AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            ENROLLMENT_SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, ENROLLMENT_BUFFER_SIZE);
        enrollmentRecorder.startRecording();

        new Thread(() -> {
            float[] buf = new float[ENROLLMENT_BUFFER_SIZE/4];
            while (isEnrolling && recordedSamples < ENROLLMENT_SAMPLE_COUNT) {
                int r = enrollmentRecorder.read(buf, 0, buf.length, AudioRecord.READ_BLOCKING);
                if (r > 0) {
                    int toCopy = Math.min(r, ENROLLMENT_SAMPLE_COUNT - recordedSamples);
                    System.arraycopy(buf, 0, enrollmentBuffer, recordedSamples, toCopy);
                    recordedSamples += toCopy;
                }
            }
            scanTextHandler.removeCallbacks(scanTextRunnable);
            runOnUiThread(this::showDiarizationInline);
            stopEnrollment();
        }).start();
    }

    private void stopEnrollment() {
        if (!isEnrolling) return;
        isEnrolling = false;
        scanTextHandler.removeCallbacks(scanTextRunnable);
        if (enrollmentRecorder != null) {
            enrollmentRecorder.stop();
            enrollmentRecorder.release();
            enrollmentRecorder = null;
        }
        new Thread(this::processEnrollmentAudio).start();
    }

    private void processEnrollmentAudio() {
        float[] audio = new float[recordedSamples];
        System.arraycopy(enrollmentBuffer, 0, audio, 0, recordedSamples);

        OfflineSpeakerDiarizationSegment[] segs =
            SpeakerDiarizationManager.processSpeakerDiarization(
                audio,
                (proc, tot, unused) -> {
                    int pct = (int)(proc * 100f / tot);
                    runOnUiThread(() -> {
                        if (diarizationProgressInline != null)
                            diarizationProgressInline.setProgress(pct);
                        if (diarizationStatusInline != null)
                            diarizationStatusInline.setText("Diarizing… " + pct + "%");
                    });
                    return 0;
                });

        Map<Integer, List<OfflineSpeakerDiarizationSegment>> bySp = new HashMap<>();
        if (segs != null) {
            for (var s : segs) {
                bySp.computeIfAbsent(s.getSpeakerId(), k -> new ArrayList<>()).add(s);
            }
        }

        List<EnrollmentActivity.EnrolledSpeaker> found = new ArrayList<>();
        for (var e : bySp.entrySet()) {
            float[] clip = extractSpeakerAudio(audio, e.getValue());
            if (clip.length < ENROLLMENT_SAMPLE_RATE) continue;
            float[] emb = SpeakerDiarizationManager.extractSpeakerEmbedding(clip);
            if (emb == null) continue;
            float dur = 0;
            for (var seg : e.getValue()) dur += seg.getEndTime() - seg.getStartTime();
            found.add(new EnrollmentActivity.EnrolledSpeaker(
                "Speaker " + (enrolledSpeakers.size() + found.size() + 1),
                emb, dur, clip));
        }

        runOnUiThread(() -> {
            if (found.isEmpty()) {
                showPromptNoSpeakersInline();
                statusText.setText("No valid speakers.");
            } else {
                enrolledSpeakers.addAll(found);
                enrolledSpeakersAdapter.notifyDataSetChanged();
                statusText.setText("Found " + found.size() + " speaker(s).");
                showPromptSelectSpeakerInline();
            }
            enrollButton.setText("Scan Environment");
        });
    }

    private void loadEnrolledSpeakers() {
        enrolledSpeakers.clear();
        SharedPreferences prefs = getSharedPreferences("SpeakerEnrollment", MODE_PRIVATE);
        int count = prefs.getInt("speaker_count", 0);
        for (int i = 0; i < count; i++) {
            String name   = prefs.getString("speaker_" + i + "_name", "Unknown");
            String embStr = prefs.getString("speaker_" + i + "_embedding", "");
            float dur     = prefs.getFloat("speaker_" + i + "_duration", 0f);
            float[] emb   = stringToFloatArray(embStr);
            if (emb != null && emb.length > 0) {
                enrolledSpeakers.add(new EnrollmentActivity.EnrolledSpeaker(name, emb, dur, null));
            }
        }
        enrolledSpeakersAdapter.notifyDataSetChanged();
    }

    private float[] stringToFloatArray(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split(",");
        float[] res = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { res[i] = Float.parseFloat(parts[i]); }
            catch (Exception e) { return null; }
        }
        return res;
    }

    private float[] extractSpeakerAudio(
            float[] all, List<OfflineSpeakerDiarizationSegment> segs) {
        int total = 0;
        for (var s : segs) {
            int st = (int) (s.getStartTime() * ENROLLMENT_SAMPLE_RATE);
            int en = (int) (s.getEndTime() * ENROLLMENT_SAMPLE_RATE);
            total += en - st;
        }
        float[] out = new float[total];
        int ptr = 0;
        for (var s : segs) {
            int st = (int) (s.getStartTime() * ENROLLMENT_SAMPLE_RATE);
            int en = (int) (s.getEndTime() * ENROLLMENT_SAMPLE_RATE);
            int len = en - st;
            System.arraycopy(all, st, out, ptr, len);
            ptr += len;
        }
        if (ptr < total) {
            float[] t = new float[ptr];
            System.arraycopy(out, 0, t, 0, ptr);
            return t;
        }
        return out;
    }

    public float getAmplificationFactor() {
        return amplificationFactor;
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onDestroy() {
        if (isProcessing) stopProcessing();
        if (rnnoise != null) { rnnoise.destroy(); rnnoise = null; }
        if (diarizationManager != null) {
            diarizationManager.release();
            diarizationManager = null;
        }
        super.onDestroy();
    }

    /** Called by adapters for inline status updates */
    public void updateStatus(String message) {
        if (mainHandler != null) {
            mainHandler.post(() -> statusText.setText(message));
        }
    }

    private void showPromptSelectSpeakerInline() {
        defaultPanel.removeAllViews();
        View v = getLayoutInflater()
                .inflate(R.layout.prompt_select_speaker_inline, defaultPanel, false);
        defaultPanel.addView(v);
    }

    private void showPromptNoSpeakersInline() {
        defaultPanel.removeAllViews();
        View v = getLayoutInflater()
                .inflate(R.layout.prompt_no_speakers_inline, defaultPanel, false);
        defaultPanel.addView(v);
    }
}
