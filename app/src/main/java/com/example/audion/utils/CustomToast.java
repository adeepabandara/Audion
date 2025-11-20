package com.example.audion.utils;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.audion.psap.R;

/**
 * Custom Toast utility for showing success and error messages
 * Appears from the top of the screen with green (success) or red (error) styling
 */
public class CustomToast {
    
    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = Toast.LENGTH_LONG;
    
    /**
     * Show success toast message (green)
     */
    public static void showSuccess(Context context, String message) {
        showSuccess(context, message, LENGTH_SHORT);
    }
    
    /**
     * Show success toast message (green) with custom duration
     */
    public static void showSuccess(Context context, String message, int duration) {
        showCustomToast(context, message, duration, true);
    }
    
    /**
     * Show error toast message (red)
     */
    public static void showError(Context context, String message) {
        showError(context, message, LENGTH_SHORT);
    }
    
    /**
     * Show error toast message (red) with custom duration
     */
    public static void showError(Context context, String message, int duration) {
        showCustomToast(context, message, duration, false);
    }
    
    /**
     * Internal method to show custom toast
     */
    private static void showCustomToast(Context context, String message, int duration, boolean isSuccess) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(
            isSuccess ? R.layout.custom_toast_success : R.layout.custom_toast_error,
            null
        );
        
        TextView text = layout.findViewById(R.id.toast_message);
        text.setText(message);
        
        // Wrap in FrameLayout to control width with margins
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = (int) (8 * context.getResources().getDisplayMetrics().density);
        params.setMargins(margin, 0, margin, 0);
        layout.setLayoutParams(params);
        container.addView(layout);
        
        Toast toast = new Toast(context);
        // Add top margin (16dp converted to pixels)
        int topMargin = (int) (16 * context.getResources().getDisplayMetrics().density);
        toast.setGravity(Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, topMargin);
        toast.setDuration(duration);
        toast.setView(container);
        toast.show();
    }
}
