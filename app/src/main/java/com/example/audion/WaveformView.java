package com.example.audion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.Queue;

public class WaveformView extends View {
    private static final int MAX_LEVELS = 100;
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
        // thicker stroke and centered color
        paint.setStrokeWidth(8f);
        paint.setColor(getResources().getColor(R.color.primary, null));
    }

    /** Call this from your BroadcastReceiver to push in a new sample. */
    public void addLevel(float level) {
        // clamp to 0–1
        level = Math.max(0f, Math.min(1f, level));
        if (levels.size() >= MAX_LEVELS) {
            levels.poll();
        }
        levels.offer(level);
        // redraw on UI thread
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

        // snapshot levels into array and pad with zeros at start
        Float[] arr = levels.toArray(new Float[0]);
        int len = arr.length;

        for (int i = 0; i < MAX_LEVELS; i++) {
            float lvl = (i < len) ? arr[i] : 0f;
            float barHeight = lvl * (h / 2f);
            float x = i * step;
            // draw line centered vertically
            canvas.drawLine(x, midY - barHeight, x, midY + barHeight, paint);
        }
    }
}