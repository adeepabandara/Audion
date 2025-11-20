package com.example.audion;

import com.audion.psap.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.Queue;

public class WaveformView extends View {
    private static final int MAX_LEVELS = 50;  // Reduced from 100 for more spacing
    final Queue<Float> levels = new LinkedList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WaveformView(Context ctx) {
        super(ctx);
        init();
    }
    public WaveformView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }
    public WaveformView(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        init();
    }

    private void init() {
        // Thinner stroke for more defined bars
        paint.setStrokeWidth(8f);  // Reduced from 16f
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(getResources().getColor(R.color.primary, null));
    }

    /** Call this from your BroadcastReceiver to push in a new sample. */
    public void addLevel(float level) {
        // clamp to 0–1
        level = Math.max(0f, Math.min(1f, level));
        
        // Apply slight amplification to make variations more visible (square root for more dynamic range)
        // This enhances quiet sounds while preserving loud peaks
        level = (float) Math.sqrt(level);
        
        if (levels.size() >= MAX_LEVELS) {
            levels.poll();
        }
        levels.offer(level);
        
        // redraw on UI thread
        postInvalidate();
    }
    
    /** Initialize with zero levels for smooth animation start */
    public void reset() {
        levels.clear();
        for (int i = 0; i < MAX_LEVELS; i++) {
            levels.offer(0f);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // full width across MAX_LEVELS
        float step = w / (MAX_LEVELS - 1f);
        float midY = h / 2f;

        // snapshot levels into array
        Float[] arr = levels.toArray(new Float[0]);
        int len = arr.length;

        // If we have fewer than MAX_LEVELS, start from the right side
        int startIndex = Math.max(0, MAX_LEVELS - len);
        
        for (int i = 0; i < MAX_LEVELS; i++) {
            float lvl;
            if (i < startIndex) {
                lvl = 0f;  // No data yet, draw zero
            } else {
                int dataIndex = i - startIndex;
                lvl = (dataIndex < len && arr[dataIndex] != null) ? arr[dataIndex] : 0f;
            }
            
            float barHeight = lvl * (h / 2f) * 1.5f;  // Use 150% for dynamic, tall bars
            float x = i * step;
            
            // Draw bars based on actual level
            if (barHeight > 3f) {
                // Draw waveform bars when there's signal
                canvas.drawLine(x, midY - barHeight, x, midY + barHeight, paint);
            } else if (barHeight > 0.5f) {
                // Draw small bars for quiet sounds
                canvas.drawLine(x, midY - barHeight, x, midY + barHeight, paint);
            } else {
                // Draw a minimal center line when no signal
                canvas.drawLine(x, midY - 3f, x, midY + 3f, paint);
            }
        }
    }
}