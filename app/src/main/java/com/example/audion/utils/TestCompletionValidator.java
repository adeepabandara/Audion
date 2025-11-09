package com.example.audion.utils;

import android.content.Context;
import android.util.Log;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationProfileDao;
import com.example.audion.data.CalibrationProfileEntity;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to validate test completion status across the app
 * Ensures proper navigation flow and prevents infinite loops
 */
public class TestCompletionValidator {
    
    private static final String TAG = "TestCompletionValidator";
    
    /**
     * Test completion status result
     */
    public static class CompletionStatus {
        public final boolean hasRightPureTone;
        public final boolean hasLeftPureTone;
        public final boolean hasRightCalibration;
        public final boolean hasLeftCalibration;
        public final boolean allTestsComplete;
        public final String nextRequiredTest; // "PURE_TONE_RIGHT", "PURE_TONE_LEFT", "CALIBRATION_LEFT", "CALIBRATION_RIGHT", "COMPLETE"
        
        public CompletionStatus(boolean hasRightPureTone, boolean hasLeftPureTone, 
                              boolean hasRightCalibration, boolean hasLeftCalibration) {
            this.hasRightPureTone = hasRightPureTone;
            this.hasLeftPureTone = hasLeftPureTone;
            this.hasRightCalibration = hasRightCalibration;
            this.hasLeftCalibration = hasLeftCalibration;
            this.allTestsComplete = hasRightPureTone && hasLeftPureTone && hasRightCalibration && hasLeftCalibration;
            
            // Determine next required test based on intended clinical flow: Pure Tone → Calibration
            if (!hasRightPureTone) {
                this.nextRequiredTest = "PURE_TONE_RIGHT";
            } else if (!hasLeftPureTone) {
                this.nextRequiredTest = "PURE_TONE_LEFT";
            } else if (!hasLeftCalibration) {
                this.nextRequiredTest = "CALIBRATION_LEFT";
            } else if (!hasRightCalibration) {
                this.nextRequiredTest = "CALIBRATION_RIGHT";
            } else {
                this.nextRequiredTest = "COMPLETE";
            }
        }
    }
    
    /**
     * Check completion status for all tests for a given user and hearing profile
     * @param context Application context
     * @param userId User ID
     * @param hearingProfileId Hearing profile ID
     * @return CompletionStatus object with detailed status
     */
    public static CompletionStatus checkAllTestsCompletionStatus(Context context, int userId, int hearingProfileId) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            
            // Check Pure Tone test completion
            HearingTestResultDao hearingTestDao = db.hearingTestResultDao();
            List<HearingTestResult> allResults = hearingTestDao.getResultsForUserAndProfile(userId, hearingProfileId);
            
            // Filter results by ear
            List<HearingTestResult> rightEarResults = new ArrayList<>();
            List<HearingTestResult> leftEarResults = new ArrayList<>();
            
            for (HearingTestResult result : allResults) {
                if ("RIGHT".equals(result.getEarSide())) {
                    rightEarResults.add(result);
                } else if ("LEFT".equals(result.getEarSide())) {
                    leftEarResults.add(result);
                }
            }
            
            boolean hasRightPureTone = !rightEarResults.isEmpty();
            boolean hasLeftPureTone = !leftEarResults.isEmpty();
            
            // Check Calibration completion
            CalibrationProfileDao calibrationProfileDao = db.calibrationProfileDao();
            CalibrationProfileEntity rightEarCalibration = 
                calibrationProfileDao.getLatestProfileForEar(userId, "RIGHT", hearingProfileId);
            CalibrationProfileEntity leftEarCalibration = 
                calibrationProfileDao.getLatestProfileForEar(userId, "LEFT", hearingProfileId);
            
            boolean hasRightCalibration = (rightEarCalibration != null);
            boolean hasLeftCalibration = (leftEarCalibration != null);
            
            CompletionStatus status = new CompletionStatus(hasRightPureTone, hasLeftPureTone, 
                                                         hasRightCalibration, hasLeftCalibration);
            
            Log.i(TAG, "=== TEST COMPLETION STATUS ===");
            Log.i(TAG, "User ID: " + userId + ", Hearing Profile ID: " + hearingProfileId);
            Log.i(TAG, "Right ear Pure Tone: " + (hasRightPureTone ? rightEarResults.size() + " frequencies" : "None"));
            Log.i(TAG, "Left ear Pure Tone: " + (hasLeftPureTone ? leftEarResults.size() + " frequencies" : "None"));
            Log.i(TAG, "Right ear Calibration: " + (hasRightCalibration ? "Complete" : "None"));
            Log.i(TAG, "Left ear Calibration: " + (hasLeftCalibration ? "Complete" : "None"));
            Log.i(TAG, "All tests complete: " + status.allTestsComplete);
            Log.i(TAG, "Next required test: " + status.nextRequiredTest);
            Log.i(TAG, "==============================");
            
            return status;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking test completion status: " + e.getMessage(), e);
            // Return conservative status in case of error
            return new CompletionStatus(false, false, false, false);
        }
    }
    
    /**
     * Check if user needs to complete any tests
     * @param context Application context
     * @param userId User ID
     * @param hearingProfileId Hearing profile ID
     * @return true if any tests are missing, false if all complete
     */
    public static boolean hasIncompleteTests(Context context, int userId, int hearingProfileId) {
        CompletionStatus status = checkAllTestsCompletionStatus(context, userId, hearingProfileId);
        return !status.allTestsComplete;
    }
    
    /**
     * Get the next activity class name that should be launched based on completion status
     * @param context Application context
     * @param userId User ID
     * @param hearingProfileId Hearing profile ID
     * @return Activity class name or null if all tests are complete
     */
    public static String getNextRequiredActivityClass(Context context, int userId, int hearingProfileId) {
        CompletionStatus status = checkAllTestsCompletionStatus(context, userId, hearingProfileId);
        
        switch (status.nextRequiredTest) {
            case "PURE_TONE_RIGHT":
                return "RightEarInstructionActivity";
            case "PURE_TONE_LEFT":
                return "LeftEarInstructionActivity";
            case "CALIBRATION_LEFT":
                return "CalibrationInstructionActivity"; // with EAR=LEFT
            case "CALIBRATION_RIGHT":
                return "CalibrationInstructionActivity"; // with EAR=RIGHT
            case "COMPLETE":
                return "TestResultsActivity";
            default:
                return null;
        }
    }
}