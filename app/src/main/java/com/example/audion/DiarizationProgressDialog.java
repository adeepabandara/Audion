package com.example.audion;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class DiarizationProgressDialog extends DialogFragment {
    private static final long INTERVAL_MS = 2000;
    private final int[] images = {
        R.drawable.ic_drink,
        R.drawable.ic_home,
        R.drawable.ic_school,
        R.drawable.ic_glass_cocktail
    };
    private final String[] captions = {
        "Initializing…",
        "Analyzing audio…",
        "Building speaker model…",
        "Finalizing…"
    };

    private ImageView imageView;
    private TextView captionView;
    private ProgressBar progressBar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int stage = 0;

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        View v = requireActivity().getLayoutInflater()
                       .inflate(R.layout.dialog_diarization_progress, null);
        imageView   = v.findViewById(R.id.diarizationImage);
        captionView = v.findViewById(R.id.diarizationCaption);
        progressBar = v.findViewById(R.id.diarizationProgressBar);

        b.setView(v);
        setCancelable(false);
        handler.post(this::cycleStage);
        return b.create();
    }

    private void cycleStage() {
        imageView.setImageResource(images[stage]);
        captionView.setText(captions[stage]);
        stage = (stage + 1) % images.length;
        handler.postDelayed(this::cycleStage, INTERVAL_MS);
    }

    /** Called from FocusActivity to update the bar. */
    public void updateProgress(float progress) {
        if (progressBar != null) {
            int p = (int)(progress * 100);
            progressBar.setProgress(p);
            if (p >= 100) dismissAllowingStateLoss();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }
}
