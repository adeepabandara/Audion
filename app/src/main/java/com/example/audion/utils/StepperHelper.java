package com.example.audion.utils;

import android.view.View;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.audion.psap.R;

public class StepperHelper {
    
    public static void updateStepperProgress(View stepperView, int completedSteps) {
        // Get stepper components
        ConstraintLayout step1Container = stepperView.findViewById(R.id.step1Container);
        ConstraintLayout step2Container = stepperView.findViewById(R.id.step2Container);
        ConstraintLayout step3Container = stepperView.findViewById(R.id.step3Container);
        ConstraintLayout step4Container = stepperView.findViewById(R.id.step4Container);
        
        TextView step1Number = stepperView.findViewById(R.id.step1Number);
        TextView step2Number = stepperView.findViewById(R.id.step2Number);
        TextView step3Number = stepperView.findViewById(R.id.step3Number);
        TextView step4Number = stepperView.findViewById(R.id.step4Number);
        
        View line1 = stepperView.findViewById(R.id.line1);
        View line2 = stepperView.findViewById(R.id.line2);
        View line3 = stepperView.findViewById(R.id.line3);
        
        // Reset all to inactive
        resetStep(step1Container, step1Number);
        resetStep(step2Container, step2Number);
        resetStep(step3Container, step3Number);
        resetStep(step4Container, step4Number);
        
        line1.setBackgroundColor(0xFFE0E0E0);
        line2.setBackgroundColor(0xFFE0E0E0);
        line3.setBackgroundColor(0xFFE0E0E0);
        
        // Activate completed steps
        if (completedSteps >= 1) {
            activateStep(step1Container, step1Number);
        }
        if (completedSteps >= 2) {
            activateStep(step2Container, step2Number);
            line1.setBackgroundColor(0xFF0F766E);
        }
        if (completedSteps >= 3) {
            activateStep(step3Container, step3Number);
            line2.setBackgroundColor(0xFF0F766E);
        }
        if (completedSteps >= 4) {
            activateStep(step4Container, step4Number);
            line3.setBackgroundColor(0xFF0F766E);
        }
    }
    
    private static void resetStep(ConstraintLayout container, TextView numberView) {
        container.setBackgroundResource(R.drawable.stepper_inactive);
        numberView.setTextColor(0xFFFFFFFF);
    }
    
    private static void activateStep(ConstraintLayout container, TextView numberView) {
        container.setBackgroundResource(R.drawable.stepper_active);
        numberView.setTextColor(0xFFFFFFFF);
    }
}
