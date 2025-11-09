package com.example.audion.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.example.audion.data.CalibrationProfileEntity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Custom view that displays calibration MCL/UCL data as horizontal bars.
 * Shows comfortable listening levels (MCL) and uncomfortable levels (UCL) per frequency.
 */
public class CalibrationSummaryView extends View {

    private static final String TAG = "CalibrationSummaryView";
    
    // Standard frequencies for calibration
    private static final int[] FREQUENCIES = {500, 1000, 2000};
    
    // Colors
    private static final int MCL_COLOR = Color.parseColor("#2ECC71"); // Green
    private static final int UCL_COLOR = Color.parseColor("#E67E22"); // Orange
    private static final int TEXT_COLOR = Color.parseColor("#212121");
    private static final int LABEL_COLOR = Color.parseColor("#616161");
    
    // Paints
    private Paint barPaint;
    private Paint textPaint;
    private Paint labelPaint;
    
    // Data
    private Map<String, Float> mclData;
    private Map<String, Float> uclData;
    private String earSide = "LEFT";
    
    // Dimensions
    private float leftMargin = 100f;
    private float rightMargin = 40f;
    private float topMargin = 20f;
    private float rowHeight = 70f;

    public CalibrationSummaryView(Context context) {
        super(context);
        init();
    }

    public CalibrationSummaryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CalibrationSummaryView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(LABEL_COLOR);
        labelPaint.setTextSize(32f);
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        
        mclData = new HashMap<>();
        uclData = new HashMap<>();
    }

    public void setCalibrationData(CalibrationProfileEntity calibration, String earSide) {
        this.earSide = earSide;
        
        if (calibration != null) {
            // Parse JSON data
            if (calibration.getMclPerFrequencyJson() != null) {
                mclData = parseJson(calibration.getMclPerFrequencyJson());
            }
            if (calibration.getUclPerFrequencyJson() != null) {
                uclData = parseJson(calibration.getUclPerFrequencyJson());
            }
        }
        
        invalidate();
    }

    private Map<String, Float> parseJson(String json) {
        try {
            Gson gson = new Gson();
            return gson.fromJson(json, new TypeToken<Map<String, Float>>(){}.getType());
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate height based on number of frequencies
        int height = (int)(topMargin + (FREQUENCIES.length * rowHeight) + 40);
        setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (mclData.isEmpty() && uclData.isEmpty()) {
            // No data message
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Calibration data not available", 
                getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }
        
        float y = topMargin;
        
        // Draw each frequency row
        for (int freq : FREQUENCIES) {
            String freqKey = String.valueOf(freq);
            Float mcl = mclData.get(freqKey);
            Float ucl = uclData.get(freqKey);
            
            // Draw frequency label
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(freq + " Hz", leftMargin - 15, y + 35, labelPaint);
            
            // Calculate bar widths (scale from 0-100 dB HL)
            float maxBarWidth = getWidth() - leftMargin - rightMargin;
            
            if (mcl != null) {
                // Draw MCL bar
                float mclWidth = (mcl / 100f) * maxBarWidth;
                barPaint.setColor(MCL_COLOR);
                RectF mclRect = new RectF(leftMargin, y + 5, leftMargin + mclWidth, y + 25);
                canvas.drawRoundRect(mclRect, 8f, 8f, barPaint);
                
                // Draw MCL value
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setColor(MCL_COLOR);
                canvas.drawText(String.format(Locale.getDefault(), "MCL: %.0f dB HL", mcl), 
                    leftMargin + mclWidth + 10, y + 20, textPaint);
            }
            
            if (ucl != null) {
                // Draw UCL bar
                float uclWidth = (ucl / 100f) * maxBarWidth;
                barPaint.setColor(UCL_COLOR);
                RectF uclRect = new RectF(leftMargin, y + 35, leftMargin + uclWidth, y + 55);
                canvas.drawRoundRect(uclRect, 8f, 8f, barPaint);
                
                // Draw UCL value
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setColor(UCL_COLOR);
                canvas.drawText(String.format(Locale.getDefault(), "UCL: %.0f dB HL", ucl), 
                    leftMargin + uclWidth + 10, y + 50, textPaint);
            }
            
            y += rowHeight;
        }
        
        // Draw legend at bottom
        y += 10;
        
        // MCL legend
        barPaint.setColor(MCL_COLOR);
        canvas.drawRoundRect(new RectF(leftMargin, y, leftMargin + 30, y + 15), 4f, 4f, barPaint);
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Most Comfortable Level (MCL)", leftMargin + 40, y + 12, textPaint);
        
        // UCL legend
        y += 25;
        barPaint.setColor(UCL_COLOR);
        canvas.drawRoundRect(new RectF(leftMargin, y, leftMargin + 30, y + 15), 4f, 4f, barPaint);
        canvas.drawText("Uncomfortable Level (UCL)", leftMargin + 40, y + 12, textPaint);
    }
}
