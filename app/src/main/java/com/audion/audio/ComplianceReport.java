package com.audion.audio;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PHASE 4: Compliance reporting for ANSI S3.6-2018 and IEC 60118-7.
 * 
 * Generates JSON reports with:
 * - NAL-NL2/DSL v5 prescription accuracy
 * - UCL limiting events
 * - Audio quality metrics (THD, SNR, latency, balance)
 * - Tone validation results
 * - System configuration
 * 
 * Stores reports in app's internal storage for debugging and validation.
 */
public class ComplianceReport {
    private static final String TAG = "ComplianceReport";
    private static final String REPORT_DIR = "compliance_reports";
    private static final SimpleDateFormat DATE_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
    
    private final Context context;
    private JSONObject report;
    
    public ComplianceReport(Context context) {
        this.context = context;
        this.report = new JSONObject();
    }
    
    /**
     * Initialize report with timestamp and system info.
     */
    public void initialize() {
        try {
            report.put("reportVersion", "1.0");
            report.put("timestamp", DATE_FORMAT.format(new Date()));
            report.put("timestampMillis", System.currentTimeMillis());
            report.put("standard", "ANSI S3.6-2018 / IEC 60118-7");
            
            // System info
            JSONObject systemInfo = new JSONObject();
            systemInfo.put("sampleRate", 48000);
            systemInfo.put("frameSize", 480);
            systemInfo.put("bands", 5);
            systemInfo.put("bandRanges", new JSONArray()
                .put("250-750 Hz")
                .put("750-1500 Hz")
                .put("1500-3000 Hz")
                .put("3000-6000 Hz")
                .put("6000-8000 Hz"));
            
            report.put("systemInfo", systemInfo);
            
            Log.i(TAG, "[Phase 4] Compliance report initialized");
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to initialize report", e);
        }
    }
    
    /**
     * Add audiogram and fitting prescription data.
     * 
     * @param leftGains Left ear band gains (linear)
     * @param rightGains Right ear band gains (linear)
     * @param fittingMode NAL_NL2 or DSL_V5
     */
    public void addAudiogramData(float[] leftGains, float[] rightGains, String fittingMode) {
        try {
            JSONObject audiogram = new JSONObject();
            audiogram.put("fittingMode", fittingMode);
            
            JSONArray leftArray = new JSONArray();
            JSONArray leftDbArray = new JSONArray();
            for (int i = 0; i < leftGains.length; i++) {
                leftArray.put(String.format(Locale.US, "%.3f", leftGains[i]));
                leftDbArray.put(String.format(Locale.US, "%.2f", 20.0 * Math.log10(leftGains[i] + 1e-10)));
            }
            
            JSONArray rightArray = new JSONArray();
            JSONArray rightDbArray = new JSONArray();
            for (int i = 0; i < rightGains.length; i++) {
                rightArray.put(String.format(Locale.US, "%.3f", rightGains[i]));
                rightDbArray.put(String.format(Locale.US, "%.2f", 20.0 * Math.log10(rightGains[i] + 1e-10)));
            }
            
            audiogram.put("leftGainsLinear", leftArray);
            audiogram.put("leftGainsdB", leftDbArray);
            audiogram.put("rightGainsLinear", rightArray);
            audiogram.put("rightGainsdB", rightDbArray);
            
            report.put("audiogram", audiogram);
            
            Log.i(TAG, "[Phase 4] Audiogram data added to report");
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to add audiogram data", e);
        }
    }
    
    /**
     * Add calibration (MCL/UCL) data.
     * 
     * @param leftUCL Left ear UCL limits (dBFS)
     * @param rightUCL Right ear UCL limits (dBFS)
     */
    public void addCalibrationData(float[] leftUCL, float[] rightUCL) {
        try {
            JSONObject calibration = new JSONObject();
            
            JSONArray leftArray = new JSONArray();
            for (float ucl : leftUCL) {
                leftArray.put(String.format(Locale.US, "%.2f", ucl));
            }
            
            JSONArray rightArray = new JSONArray();
            for (float ucl : rightUCL) {
                rightArray.put(String.format(Locale.US, "%.2f", ucl));
            }
            
            calibration.put("leftUCLdBFS", leftArray);
            calibration.put("rightUCLdBFS", rightArray);
            
            report.put("calibration", calibration);
            
            Log.i(TAG, "[Phase 4] Calibration data added to report");
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to add calibration data", e);
        }
    }
    
    /**
     * Add audio quality metrics.
     * 
     * @param metrics Metrics snapshot from AudioQualityMetrics
     */
    public void addQualityMetrics(AudioQualityMetrics.MetricsSnapshot metrics) {
        try {
            JSONObject qualityMetrics = new JSONObject();
            
            qualityMetrics.put("avgTHD_percent", String.format(Locale.US, "%.3f", metrics.avgTHD));
            qualityMetrics.put("avgSNR_dB", String.format(Locale.US, "%.2f", metrics.avgSNR));
            qualityMetrics.put("avgLatency_ms", String.format(Locale.US, "%.3f", metrics.avgLatencyMs));
            qualityMetrics.put("avgChannelBalance_dB", String.format(Locale.US, "%.3f", metrics.avgChannelBalance));
            qualityMetrics.put("uclLimitingEvents", metrics.uclEvents);
            qualityMetrics.put("totalFramesProcessed", metrics.totalFrames);
            qualityMetrics.put("durationSeconds", String.format(Locale.US, "%.2f", metrics.totalFrames * 0.01));
            
            // Compliance checks
            JSONObject compliance = new JSONObject();
            compliance.put("THD_pass_3percent", metrics.avgTHD < 3.0);
            compliance.put("latency_pass_20ms", metrics.avgLatencyMs < 20.0);
            compliance.put("balance_pass_2dB", Math.abs(metrics.avgChannelBalance) < 2.0);
            
            qualityMetrics.put("compliance", compliance);
            
            report.put("qualityMetrics", qualityMetrics);
            
            Log.i(TAG, String.format("[Phase 4] Quality metrics added: THD=%.2f%%, SNR=%.1fdB, Latency=%.2fms",
                metrics.avgTHD, metrics.avgSNR, metrics.avgLatencyMs));
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to add quality metrics", e);
        }
    }
    
    /**
     * Add tone validation results.
     * 
     * @param results Array of validation results from ToneValidator
     */
    public void addToneValidation(ToneValidator.ValidationResult[] results) {
        try {
            JSONArray toneValidation = new JSONArray();
            
            int passCount = 0;
            for (ToneValidator.ValidationResult result : results) {
                JSONObject tone = new JSONObject();
                tone.put("frequency", String.format(Locale.US, "%.0f", result.detectedFrequency));
                tone.put("amplitudedBFS", String.format(Locale.US, "%.2f", result.detectedAmplitudeDbFS));
                tone.put("freqDeviationPercent", String.format(Locale.US, "%.3f", result.frequencyDeviationPercent));
                tone.put("ampDeviationdB", String.format(Locale.US, "%.3f", result.amplitudeDeviationDb));
                tone.put("pass", result.pass);
                tone.put("message", result.message);
                
                toneValidation.put(tone);
                
                if (result.pass) passCount++;
            }
            
            JSONObject toneReport = new JSONObject();
            toneReport.put("results", toneValidation);
            toneReport.put("totalTested", results.length);
            toneReport.put("totalPassed", passCount);
            toneReport.put("allPass", passCount == results.length);
            
            report.put("toneValidation", toneReport);
            
            Log.i(TAG, String.format("[Phase 4] Tone validation added: %d/%d passed", passCount, results.length));
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to add tone validation", e);
        }
    }
    
    /**
     * Add overall compliance summary.
     */
    public void finalize(boolean overallPass) {
        try {
            JSONObject summary = new JSONObject();
            summary.put("overallCompliance", overallPass);
            summary.put("generatedAt", DATE_FORMAT.format(new Date()));
            
            report.put("summary", summary);
            
            Log.i(TAG, String.format("[Phase 4] Report finalized: %s",
                overallPass ? "✓ COMPLIANT" : "✗ NON-COMPLIANT"));
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to finalize report", e);
        }
    }
    
    /**
     * Save report to internal storage.
     * 
     * @return File path or null if failed
     */
    public String save() {
        try {
            // Create reports directory
            File reportsDir = new File(context.getFilesDir(), REPORT_DIR);
            if (!reportsDir.exists() && !reportsDir.mkdirs()) {
                Log.e(TAG, "Failed to create reports directory");
                return null;
            }
            
            // Generate filename with timestamp
            String filename = "compliance_" + DATE_FORMAT.format(new Date()) + ".json";
            File reportFile = new File(reportsDir, filename);
            
            // Write JSON to file
            FileWriter writer = new FileWriter(reportFile);
            writer.write(report.toString(2));  // Pretty-print with 2-space indent
            writer.close();
            
            String filePath = reportFile.getAbsolutePath();
            Log.i(TAG, "════════════════════════════════════════════════════════");
            Log.i(TAG, "[Phase 4] Compliance report saved:");
            Log.i(TAG, filePath);
            Log.i(TAG, String.format("Size: %.2f KB", reportFile.length() / 1024.0));
            Log.i(TAG, "════════════════════════════════════════════════════════");
            
            return filePath;
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to save compliance report", e);
            return null;
        }
    }
    
    /**
     * Get report as JSON string.
     */
    public String getReportJson() {
        try {
            return report.toString(2);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to convert report to JSON", e);
            return "{}";
        }
    }
    
    /**
     * Clear old reports (keep only last 10).
     */
    public void cleanupOldReports() {
        File reportsDir = new File(context.getFilesDir(), REPORT_DIR);
        if (!reportsDir.exists()) return;
        
        File[] reports = reportsDir.listFiles();
        if (reports == null || reports.length <= 10) return;
        
        // Sort by modification time
        java.util.Arrays.sort(reports, (a, b) -> 
            Long.compare(a.lastModified(), b.lastModified()));
        
        // Delete oldest files beyond 10
        for (int i = 0; i < reports.length - 10; i++) {
            if (reports[i].delete()) {
                Log.i(TAG, "Deleted old report: " + reports[i].getName());
            }
        }
    }
}
