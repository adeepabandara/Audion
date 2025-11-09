package com.example.audion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.AudiometryResult;
import com.example.audion.data.CalibrationProfileEntity;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingTestResult;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TestResultsActivity extends AppCompatActivity {

    private static final String TAG = "TestResultsActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MaterialButton exportPdfButton;
    private MaterialButton btnProceedToHome;
    private TextView userNameText, testDateText;
    
    private int userId;
    private int hearingProfileId;
    private String userName;
    
    // Data containers
    private List<HearingTestResult> leftEarResults;
    private List<HearingTestResult> rightEarResults;
    private CalibrationProfileEntity leftEarCalibration;
    private CalibrationProfileEntity rightEarCalibration;
    
    private EarResultsAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_results);

        // Get data from intent
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);

        initializeViews();
        loadUserData();
        loadTestResults();
        setupViewPager();
        setupFab();
    }

    private void initializeViews() {
        userNameText = findViewById(R.id.userNameText);
        testDateText = findViewById(R.id.testDateText);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        exportPdfButton = findViewById(R.id.exportPdfButton);
        btnProceedToHome = findViewById(R.id.btnProceedToHome);
        
        // Set current date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        testDateText.setText("Test Date: " + dateFormat.format(new Date()));
    }

    private void loadUserData() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                HearingProfile profile = db.hearingProfileDao().getHearingProfileById(hearingProfileId);
                
                runOnUiThread(() -> {
                    if (profile != null) {
                        userName = profile.getName();
                        userNameText.setText(userName);
                    } else {
                        userName = "Unknown User";
                        userNameText.setText(userName);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading user data", e);
                runOnUiThread(() -> userName = "Unknown User");
            }
        }).start();
    }

    private void loadTestResults() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                
                // Load Pure Tone test results
                List<HearingTestResult> allResults = db.hearingTestResultDao()
                    .getResultsByUserAndProfile(userId, hearingProfileId);
                
                leftEarResults = new ArrayList<>();
                rightEarResults = new ArrayList<>();
                
                for (HearingTestResult result : allResults) {
                    if ("LEFT".equals(result.getEarSide())) {
                        leftEarResults.add(result);
                    } else if ("RIGHT".equals(result.getEarSide())) {
                        rightEarResults.add(result);
                    }
                }
                
                // Load Calibration data
                List<CalibrationProfileEntity> calibrations = db.calibrationProfileDao()
                    .getForUserProfile(userId, hearingProfileId);
                
                for (CalibrationProfileEntity calib : calibrations) {
                    if ("LEFT".equals(calib.getEarSide())) {
                        leftEarCalibration = calib;
                    } else if ("RIGHT".equals(calib.getEarSide())) {
                        rightEarCalibration = calib;
                    }
                }
                
                runOnUiThread(() -> {
                    if (pagerAdapter != null) {
                        pagerAdapter.notifyDataSetChanged();
                    }
                    Log.d(TAG, "Loaded " + leftEarResults.size() + " LEFT results, " + 
                          rightEarResults.size() + " RIGHT results");
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading test results", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading test results", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void setupViewPager() {
        pagerAdapter = new EarResultsAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Left Ear");
            } else {
                tab.setText("Right Ear");
            }
        }).attach();
    }

    private void setupFab() {
        exportPdfButton.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                generatePdfReport();
            } else {
                requestStoragePermission();
            }
        });
        
        btnProceedToHome.setOnClickListener(v -> {
            // Navigate directly to HomeActivity (the actual home screen with audio controls)
            Intent intent = new Intent(TestResultsActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private boolean checkStoragePermission() {
        // For Android 13+ (API 33+), we don't need WRITE_EXTERNAL_STORAGE for app-specific directories
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true; // No permission needed for getExternalFilesDir()
        }
        // For Android 10-12, check for WRITE_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, 
            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // No permission needed for Android 13+
            generatePdfReport();
            return;
        }
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
            PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                generatePdfReport();
            } else {
                Toast.makeText(this, "Storage permission required to export PDF", 
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void generatePdfReport() {
        new Thread(() -> {
            try {
                runOnUiThread(() -> exportPdfButton.setEnabled(false));
                
                PdfDocument pdfDocument = new PdfDocument();
                
                // Create page 1 - Header and Left Ear
                createPdfPage(pdfDocument, 1, "LEFT", leftEarResults, leftEarCalibration);
                
                // Create page 2 - Right Ear
                createPdfPage(pdfDocument, 2, "RIGHT", rightEarResults, rightEarCalibration);
                
                // Save PDF
                String fileName = "Audion_Results_" + userName.replaceAll("\\s+", "_") + 
                    "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".pdf";
                
                File documentsDir = new File(getExternalFilesDir(null), "Audion");
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs();
                }
                
                File pdfFile = new File(documentsDir, fileName);
                FileOutputStream fos = new FileOutputStream(pdfFile);
                pdfDocument.writeTo(fos);
                pdfDocument.close();
                fos.close();
                
                runOnUiThread(() -> {
                    exportPdfButton.setEnabled(true);
                    Toast.makeText(this, "PDF exported successfully: " + pdfFile.getAbsolutePath(), 
                        Toast.LENGTH_LONG).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating PDF", e);
                runOnUiThread(() -> {
                    exportPdfButton.setEnabled(true);
                    Toast.makeText(this, "Error generating PDF report", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void createPdfPage(PdfDocument pdfDocument, int pageNumber, String ear, 
                              List<HearingTestResult> results, CalibrationProfileEntity calibration) {
        
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        
        int yPos = 50;
        
        // Header (only on first page)
        if (pageNumber == 1) {
            paint.setTextSize(24f);
            paint.setColor(Color.BLACK);
            paint.setFakeBoldText(true);
            canvas.drawText("Audion - Hearing Test Report", 50, yPos, paint);
            yPos += 40;
            
            paint.setTextSize(14f);
            paint.setFakeBoldText(false);
            canvas.drawText("Name: " + userName, 50, yPos, paint);
            yPos += 20;
            canvas.drawText("Date: " + new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date()), 50, yPos, paint);
            yPos += 40;
        }
        
        // Ear Title
        paint.setTextSize(18f);
        paint.setFakeBoldText(true);
        int earColor = "LEFT".equals(ear) ? Color.parseColor("#3498DB") : Color.parseColor("#E74C3C");
        paint.setColor(earColor);
        canvas.drawText(ear + " EAR RESULTS", 50, yPos, paint);
        yPos += 30;
        
        // Audiogram (simplified for PDF)
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(false);
        paint.setTextSize(16f);
        canvas.drawText("Pure Tone Audiogram:", 50, yPos, paint);
        yPos += 25;
        
        // Audiogram table header
        paint.setTextSize(12f);
        paint.setFakeBoldText(true);
        canvas.drawText("Frequency (Hz)", 70, yPos, paint);
        canvas.drawText("Threshold (dB HL)", 200, yPos, paint);
        canvas.drawText("Status", 350, yPos, paint);
        yPos += 20;
        
        // Draw audiogram data
        paint.setFakeBoldText(false);
        int[] frequencies = {250, 500, 1000, 2000, 4000, 8000};
        for (int freq : frequencies) {
            HearingTestResult result = findResultForFrequency(results, freq);
            if (result != null) {
                canvas.drawText(String.valueOf(freq), 70, yPos, paint);
                canvas.drawText(String.format(Locale.getDefault(), "%.1f", result.getThresholdDbHL()), 200, yPos, paint);
                
                // Categorize hearing level
                float threshold = result.getThresholdDbHL();
                String status;
                if (threshold <= 25) status = "Normal";
                else if (threshold <= 40) status = "Mild Loss";
                else if (threshold <= 55) status = "Moderate Loss";
                else if (threshold <= 70) status = "Moderately Severe";
                else if (threshold <= 90) status = "Severe Loss";
                else status = "Profound Loss";
                
                canvas.drawText(status, 350, yPos, paint);
                yPos += 18;
            }
        }
        
        yPos += 20;
        
        // Calibration data
        paint.setTextSize(16f);
        paint.setFakeBoldText(true);
        canvas.drawText("Comfortable Listening Levels:", 50, yPos, paint);
        yPos += 25;
        
        if (calibration != null && calibration.getMclPerFrequencyJson() != null) {
            paint.setTextSize(12f);
            paint.setFakeBoldText(true);
            canvas.drawText("Frequency (Hz)", 70, yPos, paint);
            canvas.drawText("MCL (dB HL)", 200, yPos, paint);
            canvas.drawText("UCL (dB HL)", 350, yPos, paint);
            yPos += 20;
            
            paint.setFakeBoldText(false);
            
            // Parse JSON calibration data
            Map<String, Float> mclData = parseCalibrationJson(calibration.getMclPerFrequencyJson());
            Map<String, Float> uclData = parseCalibrationJson(calibration.getUclPerFrequencyJson());
            
            int[] calFreqs = {500, 1000, 2000};
            for (int freq : calFreqs) {
                Float mcl = mclData.get(String.valueOf(freq));
                Float ucl = uclData.get(String.valueOf(freq));
                
                if (mcl != null) {
                    canvas.drawText(String.valueOf(freq), 70, yPos, paint);
                    canvas.drawText(String.format(Locale.getDefault(), "%.1f", mcl), 200, yPos, paint);
                    if (ucl != null) {
                        canvas.drawText(String.format(Locale.getDefault(), "%.1f", ucl), 350, yPos, paint);
                    }
                    yPos += 18;
                }
            }
        } else {
            paint.setTextSize(12f);
            paint.setFakeBoldText(false);
            canvas.drawText("Not calibrated", 70, yPos, paint);
            yPos += 20;
        }
        
        // Footer
        paint.setTextSize(10f);
        paint.setColor(Color.GRAY);
        canvas.drawText("Generated by Audion - Personalized Hearing Analysis", 50, 810, paint);
        canvas.drawText("This report is for screening purposes only. Consult an audiologist for diagnosis.", 50, 825, paint);
        
        pdfDocument.finishPage(page);
    }

    private HearingTestResult findResultForFrequency(List<HearingTestResult> results, int frequency) {
        for (HearingTestResult result : results) {
            if (result.getFrequency() == frequency) {
                return result;
            }
        }
        return null;
    }

    private Map<String, Float> parseCalibrationJson(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Gson gson = new Gson();
            return gson.fromJson(json, new TypeToken<Map<String, Float>>(){}.getType());
        } catch (Exception e) {
            Log.e(TAG, "Error parsing calibration JSON", e);
            return new HashMap<>();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ViewPager Adapter
    private class EarResultsAdapter extends FragmentStateAdapter {
        
        public EarResultsAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                // Left Ear
                return ResultsEarFragment.newInstance("LEFT", leftEarResults, leftEarCalibration);
            } else {
                // Right Ear
                return ResultsEarFragment.newInstance("RIGHT", rightEarResults, rightEarCalibration);
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Left and Right ear
        }
    }
}
