/**
 * FINAL FIX FOR NAVIGATION BOUNCING BUG
 * 
 * Problem: After LEFT ear calibration completion, app bounces between 
 * LEFT ear calibration instruction and RIGHT ear pure tone test instruction.
 * 
 * Solution: The issue is most likely caused by multiple navigation attempts
 * and the Intent.FLAG_ACTIVITY_CLEAR_TOP flag causing activity stack conflicts.
 */

// ============================================================================
// FILE 1: EarResultActivity.java - Add these changes
// ============================================================================

// 1. ADD NAVIGATION GUARD FIELD (add to class fields):
private boolean isNavigating = false;

// 2. MODIFY THE navigateNext() METHOD:
private void navigateNext() {
    Log.d("EarResultActivity", "navigateNext called for ear: " + currentEar);
    
    // ADD THIS: Prevent double navigation
    if (isNavigating) {
        Log.d("EarResultActivity", "Navigation already in progress, ignoring call");
        return;
    }
    isNavigating = true;
    
    // ADD THIS: Disable button to prevent multiple clicks
    buttonNext.setEnabled(false);
    
    if (currentEar.equals("RIGHT")) {
        // Navigate to calibration instruction for LEFT ear
        Log.d("EarResultActivity", "Navigating to LEFT ear calibration instruction");
        
        Intent intent = new Intent(this, CalibrationInstructionActivity.class);
        intent.putExtra("EAR", "LEFT");
        intent.putExtra("USER_ID", userId);
        intent.putExtra("HEARING_PROFILE_ID", profileId);
        
        // REMOVE THIS LINE: intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        try {
            startActivity(intent);
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            finish();
            Log.d("EarResultActivity", "Successfully started LEFT ear calibration");
        } catch (Exception e) {
            Log.e("EarResultActivity", "Failed to navigate to LEFT ear calibration", e);
            // ADD THIS: Reset navigation flag on failure
            isNavigating = false;
            buttonNext.setEnabled(true);
            buttonNext.setText("Try Again");
        }
    } else {
        // LEFT ear calibration completed - navigate to General Instruction
        Log.d("EarResultActivity", "LEFT ear completed, navigating to General Instruction");
        
        try {
            Intent intent = new Intent(this, GeneralInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", profileId);
            
            startActivity(intent);
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            finish();
            Log.d("EarResultActivity", "Successfully navigated to General Instruction");
        } catch (Exception e) {
            Log.e("EarResultActivity", "Failed to navigate to General Instruction", e);
            // ADD THIS: Reset navigation flag on failure
            isNavigating = false;
            buttonNext.setEnabled(true);
            // Show error dialog or retry option
        }
    }
}

// 3. MODIFY THE BUTTON CLICK LISTENER in onCreate():
buttonNext.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        // ADD THIS: Check navigation guard
        if (isNavigating) {
            Log.d("EarResultActivity", "Navigation already in progress, ignoring click");
            return;
        }
        
        Log.d("EarResultActivity", "Next button clicked for ear: " + currentEar);
        navigateNext();
    }
});

// ============================================================================
// FILE 2: BaselineCalibrationActivity.java - Remove problematic flag
// ============================================================================

// In the finishBaseline() method, MODIFY this section:
Intent resultIntent = new Intent(this, EarResultActivity.class);
resultIntent.putExtra("EAR", currentEar);
resultIntent.putExtra("RESULT", "Calibration completed successfully!");
resultIntent.putExtra("USER_ID", userId);
resultIntent.putExtra("HEARING_PROFILE_ID", profileId);

// REMOVE THIS LINE: resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

startActivity(resultIntent);
overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);

// ============================================================================
// TESTING THE FIX
// ============================================================================

/*
To test the fix:
1. Complete RIGHT ear calibration - should smoothly go to LEFT ear calibration
2. Complete LEFT ear calibration - should smoothly go to pure tone test WITHOUT bouncing
3. Test rapid button clicks - should be prevented by navigation guard
4. Test back button behavior - should be handled properly

Expected result: No more bouncing between activities, smooth navigation flow.
*/
