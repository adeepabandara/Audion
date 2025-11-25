package com.audion.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

public class RotatingBorderView extends FrameLayout {
    private Paint borderPaint;
    private Path borderPath;
    private RectF rectF;
    private float rotationAngle = 0f;
    private ValueAnimator rotationAnimator;
    private float borderWidth = 12f;
    private float cornerRadius = 48f; // 12dp * 4 = 48px for proper rounding
    private Matrix gradientMatrix;
    private LinearGradient gradient;
    
    // Gradient colors
    private int[] gradientColors = new int[]{
        Color.parseColor("#ffc700"),
        Color.parseColor("#088e96"),
        Color.parseColor("#ffc700")
    };

    public RotatingBorderView(Context context) {
        super(context);
        init();
    }

    public RotatingBorderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RotatingBorderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Enable drawing on this ViewGroup
        setWillNotDraw(false);
        
        // Border paint with gradient
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setStrokeCap(Paint.Cap.ROUND);
        
        borderPath = new Path();
        rectF = new RectF();
        gradientMatrix = new Matrix();
        
        // Create rotation animator (0 to 360 degrees)
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(3000);
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.addUpdateListener(animation -> {
            rotationAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    public void startAnimation() {
        if (!rotationAnimator.isRunning()) {
            rotationAnimator.start();
        }
    }

    public void stopAnimation() {
        if (rotationAnimator.isRunning()) {
            rotationAnimator.cancel();
        }
        rotationAngle = 0f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        float halfBorder = borderWidth / 2f;
        rectF.set(halfBorder, halfBorder, w - halfBorder, h - halfBorder);
        
        // Create the border path
        borderPath.reset();
        borderPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
        
        // Create diagonal gradient across the view
        float centerX = w / 2f;
        float centerY = h / 2f;
        float radius = (float) Math.sqrt(centerX * centerX + centerY * centerY);
        
        gradient = new LinearGradient(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius,
            gradientColors,
            null,
            Shader.TileMode.CLAMP
        );
        borderPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (rectF.width() <= 0 || rectF.height() <= 0 || gradient == null) {
            return;
        }
        
        // Rotate the gradient shader around the center
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        
        gradientMatrix.reset();
        gradientMatrix.setRotate(rotationAngle, centerX, centerY);
        gradient.setLocalMatrix(gradientMatrix);
        
        // Draw the full border with rotating gradient
        canvas.drawPath(borderPath, borderPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }
}
