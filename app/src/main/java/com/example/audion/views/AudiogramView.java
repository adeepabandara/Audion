package com.example.audion.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import com.example.audion.data.HearingTestResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Custom view that displays a clinical audiogram following ANSI S3.6 standards.
 * Features:
 * - Inverted Y-axis (0 dB at top, 120 dB at bottom)
 * - Standard audiometric frequencies on X-axis
 * - Gridlines every 10 dB and at each frequency
 * - Symbols: "O" for right ear (red), "X" for left ear (blue)
 * - Smooth connecting lines between points
 */
public class AudiogramView extends View {

    private static final String TAG = "AudiogramView";
    
    // Standard audiometric frequencies in Hz
    private static final int[] FREQUENCIES = {250, 500, 1000, 2000, 4000, 8000};
    
    // dB HL range
    private static final int MIN_DB_HL = 0;
    private static final int MAX_DB_HL = 120;
    private static final int DB_GRID_STEP = 10;
    
    // Colors
    private static final int GRID_COLOR = Color.parseColor("#E0E0E0");
    private static final int AXIS_COLOR = Color.parseColor("#424242");
    private static final int TEXT_COLOR = Color.parseColor("#212121");
    private static final int LEFT_EAR_COLOR = Color.parseColor("#3498DB");  // Blue
    private static final int RIGHT_EAR_COLOR = Color.parseColor("#E74C3C"); // Red
    
    // Paints
    private Paint gridPaint;
    private Paint axisPaint;
    private Paint textPaint;
    private Paint symbolPaint;
    private Paint linePaint;
    private Paint labelPaint;
    
    // Data
    private List<HearingTestResult> testResults;
    private List<HearingTestResult> leftEarResults;
    private List<HearingTestResult> rightEarResults;
    private String earSide = "LEFT";
    private int earColor = LEFT_EAR_COLOR;
    private boolean showBothEars = false;
    
    // Margins and dimensions
    private float leftMargin = 80f;
    private float rightMargin = 40f;
    private float topMargin = 60f;
    private float bottomMargin = 80f;
    
    private float chartWidth;
    private float chartHeight;

    public AudiogramView(Context context) {
        super(context);
        init();
    }

    public AudiogramView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudiogramView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Grid paint (dashed lines)
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));
        
        // Axis paint (solid lines)
        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(AXIS_COLOR);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setStyle(Paint.Style.STROKE);
        
        // Text paint for labels
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        // Label paint for axis labels
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(TEXT_COLOR);
        labelPaint.setTextSize(32f);
        labelPaint.setFakeBoldText(true);
        
        // Symbol paint
        symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        symbolPaint.setStyle(Paint.Style.STROKE);
        symbolPaint.setStrokeWidth(3f);
        symbolPaint.setTextSize(36f);
        symbolPaint.setTextAlign(Paint.Align.CENTER);
        
        // Line paint
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f);
        
        testResults = new ArrayList<>();
        leftEarResults = new ArrayList<>();
        rightEarResults = new ArrayList<>();
    }

    public void setTestResults(List<HearingTestResult> results) {
        this.testResults = results != null ? results : new ArrayList<>();
        this.showBothEars = false;
        invalidate();
    }

    public void setLeftEarResults(List<HearingTestResult> results) {
        this.leftEarResults = results != null ? results : new ArrayList<>();
        this.showBothEars = true;
        Log.d(TAG, "setLeftEarResults: " + this.leftEarResults.size() + " results");
        invalidate();
    }

    public void setRightEarResults(List<HearingTestResult> results) {
        this.rightEarResults = results != null ? results : new ArrayList<>();
        this.showBothEars = true;
        Log.d(TAG, "setRightEarResults: " + this.rightEarResults.size() + " results");
        invalidate();
    }

    public void setEarSide(String earSide) {
        this.earSide = earSide;
        invalidate();
    }

    public void setEarColor(int color) {
        this.earColor = color;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        chartWidth = w - leftMargin - rightMargin;
        chartHeight = h - topMargin - bottomMargin;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (chartWidth <= 0 || chartHeight <= 0) return;
        
        // Draw title only if single ear mode
        if (!showBothEars) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Audiogram - " + earSide + " Ear", 
                getWidth() / 2f, topMargin - 20, labelPaint);
        }
        
        // Draw grid
        drawGrid(canvas);
        
        // Draw axes
        drawAxes(canvas);
        
        // Draw data points and lines
        if (showBothEars) {
            drawBothEarsData(canvas);
        } else {
            drawData(canvas);
        }
        
        // Draw legend
        if (!showBothEars) {
            drawLegend(canvas);
        }
    }

    private void drawGrid(Canvas canvas) {
        // Horizontal grid lines (every 10 dB)
        for (int db = MIN_DB_HL; db <= MAX_DB_HL; db += DB_GRID_STEP) {
            float y = dbToY(db);
            canvas.drawLine(leftMargin, y, leftMargin + chartWidth, y, gridPaint);
        }
        
        // Vertical grid lines (at each frequency)
        for (int i = 0; i < FREQUENCIES.length; i++) {
            float x = freqToX(i);
            canvas.drawLine(x, topMargin, x, topMargin + chartHeight, gridPaint);
        }
    }

    private void drawAxes(Canvas canvas) {
        // Y-axis (left side)
        canvas.drawLine(leftMargin, topMargin, leftMargin, topMargin + chartHeight, axisPaint);
        
        // X-axis (bottom)
        canvas.drawLine(leftMargin, topMargin + chartHeight, 
            leftMargin + chartWidth, topMargin + chartHeight, axisPaint);
        
        // Y-axis labels (dB HL)
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int db = MIN_DB_HL; db <= MAX_DB_HL; db += 20) {
            float y = dbToY(db);
            canvas.drawText(String.valueOf(db), leftMargin - 10, y + 10, textPaint);
        }
        
        // Y-axis title
        canvas.save();
        canvas.rotate(-90, 25, getHeight() / 2f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Hearing Level (dB HL)", 25, getHeight() / 2f, labelPaint);
        canvas.restore();
        
        // X-axis labels (Frequency)
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < FREQUENCIES.length; i++) {
            float x = freqToX(i);
            canvas.drawText(String.valueOf(FREQUENCIES[i]), x, topMargin + chartHeight + 30, textPaint);
        }
        
        // X-axis title
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Frequency (Hz)", getWidth() / 2f, getHeight() - 10, labelPaint);
    }

    private void drawData(Canvas canvas) {
        if (testResults.isEmpty()) {
            // No data message
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(32f);
            canvas.drawText("No test data available", 
                getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextSize(28f);
            return;
        }
        
        // Sort results by frequency
        List<HearingTestResult> sortedResults = new ArrayList<>(testResults);
        Collections.sort(sortedResults, new Comparator<HearingTestResult>() {
            @Override
            public int compare(HearingTestResult o1, HearingTestResult o2) {
                return Integer.compare(o1.getFrequency(), o2.getFrequency());
            }
        });
        
        // Set colors
        symbolPaint.setColor(earColor);
        linePaint.setColor(earColor);
        
        // Draw connecting lines first
        Path linePath = new Path();
        boolean firstPoint = true;
        
        for (HearingTestResult result : sortedResults) {
            int freqIndex = getFrequencyIndex(result.getFrequency());
            if (freqIndex >= 0) {
                float x = freqToX(freqIndex);
                float y = dbToY(result.getThresholdDbHL());
                
                if (firstPoint) {
                    linePath.moveTo(x, y);
                    firstPoint = false;
                } else {
                    linePath.lineTo(x, y);
                }
            }
        }
        canvas.drawPath(linePath, linePaint);
        
        // Draw symbols
        for (HearingTestResult result : sortedResults) {
            int freqIndex = getFrequencyIndex(result.getFrequency());
            if (freqIndex >= 0) {
                float x = freqToX(freqIndex);
                float y = dbToY(result.getThresholdDbHL());
                
                drawSymbol(canvas, x, y, earSide, earColor);
                
                // Draw threshold value below symbol
                textPaint.setTextSize(24f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(earColor);
                canvas.drawText(String.format(Locale.getDefault(), "%.0f", result.getThresholdDbHL()), 
                    x, y + 30, textPaint);
                textPaint.setTextSize(28f);
                textPaint.setColor(TEXT_COLOR);
            }
        }
    }

    private void drawBothEarsData(Canvas canvas) {
        Log.d(TAG, "drawBothEarsData: left=" + leftEarResults.size() + ", right=" + rightEarResults.size());
        
        if (leftEarResults.isEmpty() && rightEarResults.isEmpty()) {
            // No data message
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(32f);
            canvas.drawText("No test data available", 
                getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextSize(28f);
            return;
        }
        
        // Draw left ear (X, blue)
        if (!leftEarResults.isEmpty()) {
            Log.d(TAG, "Drawing left ear data");
            drawEarData(canvas, leftEarResults, "LEFT", LEFT_EAR_COLOR);
        }
        
        // Draw right ear (O, red)
        if (!rightEarResults.isEmpty()) {
            Log.d(TAG, "Drawing right ear data");
            drawEarData(canvas, rightEarResults, "RIGHT", RIGHT_EAR_COLOR);
        }
    }

    private void drawEarData(Canvas canvas, List<HearingTestResult> results, String ear, int color) {
        // Sort results by frequency
        List<HearingTestResult> sortedResults = new ArrayList<>(results);
        Collections.sort(sortedResults, new Comparator<HearingTestResult>() {
            @Override
            public int compare(HearingTestResult o1, HearingTestResult o2) {
                return Integer.compare(o1.getFrequency(), o2.getFrequency());
            }
        });
        
        // Set colors
        symbolPaint.setColor(color);
        linePaint.setColor(color);
        
        // Draw connecting lines first
        Path linePath = new Path();
        boolean firstPoint = true;
        
        for (HearingTestResult result : sortedResults) {
            int freqIndex = getFrequencyIndex(result.getFrequency());
            if (freqIndex >= 0) {
                float x = freqToX(freqIndex);
                float y = dbToY(result.getThresholdDbHL());
                
                if (firstPoint) {
                    linePath.moveTo(x, y);
                    firstPoint = false;
                } else {
                    linePath.lineTo(x, y);
                }
            }
        }
        canvas.drawPath(linePath, linePaint);
        
        // Draw symbols
        for (HearingTestResult result : sortedResults) {
            int freqIndex = getFrequencyIndex(result.getFrequency());
            if (freqIndex >= 0) {
                float x = freqToX(freqIndex);
                float y = dbToY(result.getThresholdDbHL());
                
                drawSymbol(canvas, x, y, ear, color);
            }
        }
    }

    private void drawSymbol(Canvas canvas, float cx, float cy, String ear, int color) {
        float radius = 12f;
        
        symbolPaint.setColor(color);
        
        if ("LEFT".equals(ear)) {
            // Draw X for left ear
            canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, symbolPaint);
            canvas.drawLine(cx - radius, cy + radius, cx + radius, cy - radius, symbolPaint);
        } else {
            // Draw O for right ear
            canvas.drawCircle(cx, cy, radius, symbolPaint);
        }
    }

    private void drawLegend(Canvas canvas) {
        float legendX = leftMargin + 20;
        float legendY = topMargin + 20;
        
        // Draw symbol
        symbolPaint.setColor(earColor);
        drawSymbol(canvas, legendX, legendY, earSide, earColor);
        
        // Draw label
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(earColor);
        textPaint.setTextSize(28f);
        canvas.drawText(earSide + " Ear", legendX + 25, legendY + 8, textPaint);
    }

    private float dbToY(float db) {
        // Invert Y-axis: 0 dB at top, 120 dB at bottom
        float ratio = db / (float)(MAX_DB_HL - MIN_DB_HL);
        return topMargin + (ratio * chartHeight);
    }

    private float freqToX(int freqIndex) {
        if (FREQUENCIES.length <= 1) return leftMargin;
        float spacing = chartWidth / (FREQUENCIES.length - 1);
        return leftMargin + (freqIndex * spacing);
    }

    private int getFrequencyIndex(int frequency) {
        for (int i = 0; i < FREQUENCIES.length; i++) {
            if (FREQUENCIES[i] == frequency) {
                return i;
            }
        }
        return -1;
    }
}
