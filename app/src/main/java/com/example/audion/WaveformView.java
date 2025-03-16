package com.example.audion;

import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class WaveformView extends View {
    private Paint wavePaint, borderPaint;
    private List<Float> amplitudes;
    private int maxAmplitudes = 100;
    private float maxAmplitude = 32767f;

    // Custom attributes
    private int waveColor;
    private int borderColor;
    private float borderRadius;
    private int bgColor;

    public WaveformView(Context context) {
        super(context);
        init(null);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        // Set default values
        int defaultWaveColor = ContextCompat.getColor(getContext(), R.color.primary);
        int defaultBorderColor = ContextCompat.getColor(getContext(), R.color.card_bg);
        float defaultBorderRadius = 20f;  // You can also define a dimension resource if needed.
        int defaultBgColor = ContextCompat.getColor(getContext(), R.color.card_bg);

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.WaveformView);
            waveColor = a.getColor(R.styleable.WaveformView_waveColor, defaultWaveColor);
            borderColor = a.getColor(R.styleable.WaveformView_borderColor, defaultBorderColor);
            borderRadius = a.getDimension(R.styleable.WaveformView_borderRadius, defaultBorderRadius);
            bgColor = a.getColor(R.styleable.WaveformView_bgColor, defaultBgColor);
            a.recycle();
        } else {
            waveColor = defaultWaveColor;
            borderColor = defaultBorderColor;
            borderRadius = defaultBorderRadius;
            bgColor = defaultBgColor;
        }

        // Initialize the paint for the waveform
        wavePaint = new Paint();
        wavePaint.setColor(waveColor);
        wavePaint.setStrokeWidth(2f);
        wavePaint.setAntiAlias(true);

        // Initialize the paint for the border
        borderPaint = new Paint();
        borderPaint.setColor(borderColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);  // Adjust as needed

        // Set the view's background color
        setBackgroundColor(bgColor);

        amplitudes = new ArrayList<>();
    }

    // Call this method to add a new amplitude value
    public void addAmplitude(float amplitude) {
        amplitudes.add(amplitude);
        if (amplitudes.size() > maxAmplitudes) {
            amplitudes.remove(0);
        }
        invalidate();
    }

@Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    // Draw the border with rounded corners
    RectF rect = new RectF(0, 0, getWidth(), getHeight());
    canvas.drawRoundRect(rect, borderRadius, borderRadius, borderPaint);

    // If no amplitude data is available, draw a placeholder text
    if (amplitudes.isEmpty()) {
        Paint textPaint = new Paint();
        textPaint.setColor(Color.GRAY);  // Use a color from resources if needed
        textPaint.setTextSize(16 * getResources().getDisplayMetrics().density); // 16dp text size
        textPaint.setTextAlign(Paint.Align.CENTER);
        // Center the text vertically
        float yPos = (getHeight() / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
        canvas.drawText("Audio Stream", getWidth() / 2f, yPos, textPaint);
    } else {
        // Draw the waveform lines
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2f;
        int size = amplitudes.size();
        float gap = width / (float) maxAmplitudes;

        for (int i = 0; i < size; i++) {
            float amplitude = amplitudes.get(i);
            float scaledAmplitude = (amplitude / maxAmplitude) * centerY;
            float x = i * gap;
            canvas.drawLine(x, centerY - scaledAmplitude, x, centerY + scaledAmplitude, wavePaint);
        }
    }
}


}
