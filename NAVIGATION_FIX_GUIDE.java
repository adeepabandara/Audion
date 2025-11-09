// Fix for EarResultActivity.java - Add Navigation Guard to prevent bouncing

/**
 * Key changes to fix the navigation bouncing bug:
 * 
 * 1. Add navigation guard (isNavigating flag) to prevent multiple navigation calls
 * 2. Remove Intent.FLAG_ACTIVITY_CLEAR_TOP which can cause activity stack conflicts
 * 3. Add proper button state management to prevent multiple clicks
 * 4. Add error recovery with navigation flag reset
 */

public class EarResultActivity extends AppCompatActivity {
    
    // ADD THIS: Navigation guard to prevent double navigation
    private boolean isNavigating = false;
    
    private TextView earLabel;
    private Chip resultChip;
    private MaterialButton buttonNext;
    private TextView tickAnimation;
    
    private String currentEar;
    private String result;
    private int userId;
    private int profileId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ear_result);
        
        // ... existing setup code ...
        
        // Setup button click listener - MODIFY THIS: Add navigation guard
        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Prevent double navigation
                if (isNavigating) {
                    Log.d("EarResultActivity", "Navigation already in progress, ignoring click");
                    return;
                }
                
                Log.d("EarResultActivity", "Next button clicked for ear: " + currentEar);
                navigateNext();
            }
        });
    }
    
    private void navigateNext() {
        Log.d("EarResultActivity", "navigateNext called for ear: " + currentEar);
        
        // MODIFY THIS: Add navigation guard and button disable
        if (isNavigating) {
            Log.d("EarResultActivity", "Navigation already in progress, ignoring call");
            return;
        }
        isNavigating = true;
        
        // Disable button to prevent multiple clicks
        buttonNext.setEnabled(false);
        
        if (currentEar.equals("RIGHT")) {
            // Navigate to calibration instruction for LEFT ear
            Log.d("EarResultActivity", "Navigating to LEFT ear calibration instruction");
            
            // MODIFY THIS: Remove problematic FLAG_ACTIVITY_CLEAR_TOP
            Intent intent = new Intent(this, CalibrationInstructionActivity.class);
            intent.putExtra("EAR", "LEFT");
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", profileId);
            
            // Removed: intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            try {
                startActivity(intent);
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                finish();
                Log.d("EarResultActivity", "Successfully started LEFT ear calibration");
            } catch (Exception e) {
                Log.e("EarResultActivity", "Failed to navigate to LEFT ear calibration", e);
                // MODIFY THIS: Reset navigation flag on failure
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
                // MODIFY THIS: Reset navigation flag on failure
                isNavigating = false;
                buttonNext.setEnabled(true);
                // Show error dialog with retry option
                showNavigationErrorDialog();
            }
        }
    }
    
    // ADD THIS: Helper method for error handling
    private void showNavigationErrorDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Navigation Error");
        builder.setMessage("Unable to proceed. Would you like to try again or return to main menu?");
        builder.setPositiveButton("Try Again", (dialog, which) -> navigateNext());
        builder.setNegativeButton("Main Menu", (dialog, which) -> goToMainMenu());
        builder.setCancelable(false);
        builder.show();
    }
    
    @Override
    public void onBackPressed() {
        // Prevent back button from closing the results page unexpectedly
        Log.d("EarResultActivity", "onBackPressed - preventing default behavior");
        // Don't call super.onBackPressed() to prevent going back
    }
}
