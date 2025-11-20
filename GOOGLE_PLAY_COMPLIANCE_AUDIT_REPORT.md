# GOOGLE PLAY COMPLIANCE AUDIT – AUDION

**Audit Date:** November 19, 2025  
**App Name:** Audion  
**Package ID:** com.example.audion  
**Target SDK:** 34  
**Min SDK:** 27  
**Version:** 1.0 (versionCode 1)  
**Category:** Consumer Hearing Support / Personal Sound Amplification Product (PSAP)

---

## 📋 **SUMMARY VERDICT**

### ⚠️ **NOT READY – CRITICAL BLOCKERS PRESENT**

**Key Findings:**
- ❌ **CRITICAL:** No Privacy Policy implemented or referenced anywhere in the app
- ❌ **CRITICAL:** No medical disclaimers present despite handling hearing-related health data
- ❌ **CRITICAL:** No age verification or 18+ gate implemented
- ❌ **CRITICAL:** Missing `FOREGROUND_SERVICE_MICROPHONE` permission type declaration (required for Android 14+)
- ⚠️ **MAJOR:** Internal code comments contain "hearing aid" terminology that could trigger medical device classification
- ⚠️ **MAJOR:** INTERNET permission declared but no network usage found (potentially flagged as unused permission)

**Positive Aspects:**
- ✅ 64-bit native libraries present (arm64-v8a)
- ✅ Target SDK 34 meets current Play requirements
- ✅ Foreground services properly implemented with notifications
- ✅ Permissions requested at runtime with user-facing explanations
- ✅ No monetization/ads/IAP detected
- ✅ Health data stored locally only (no network transmission detected)

---

## 🚨 **CRITICAL BLOCKING ISSUES (Must Fix Before Submission)**

### **CRIT-01: Privacy Policy Not Implemented**
**Severity:** `CRITICAL – SUBMISSION BLOCKER`

**Issue:**  
The app collects and stores potentially health-related data (hearing test results, audiograms, calibration profiles) but does not provide or reference a Privacy Policy anywhere in the app or manifest.

**Files Affected:**
- `AndroidManifest.xml` – No privacy policy meta-data
- `WhatsYourNameActivity.java:43-44` – Legal text references "Terms of Service and Privacy Policy" but no implementation exists
- All onboarding/settings screens – No Privacy Policy links or screens

**Policy Violation:**  
Google Play requires all apps that collect user data to provide a privacy policy accessible from the app and listed in the Play Console. This is mandatory for health-related data collection.

**Evidence:**
```java
// WhatsYourNameActivity.java line 43-44
getSharedPreferences("AudionPrefs", MODE_PRIVATE)
    .edit()
    .putString("user_name", name)
```

```bash
# grep search for Privacy Policy implementation
grep -r "Privacy Policy|PrivacyPolicy|privacy_policy" **/*.java
# Result: No matches found (only hardcoded UI text with no links)
```

**Fix Required:**
1. Create a Privacy Policy document hosted at a publicly accessible URL
2. Add Privacy Policy link in:
   - `WhatsYourNameActivity.java` – Make the legal text clickable with Intent to browser
   - Settings/Profile screen – Add "Privacy Policy" menu item
   - Play Store listing – Data Safety section
3. Add meta-data to AndroidManifest.xml:
   ```xml
   <meta-data
       android:name="com.google.android.gms.ads.PRIVACY_POLICY_URL"
       android:value="https://yourwebsite.com/privacy" />
   ```

**Data Safety Declaration Required:**
- User names stored locally
- Hearing test results (audiogram data – health-related)
- Calibration profiles (hearing sensitivity data – health-related)
- No data shared with third parties
- No data transmitted off device
- User can delete data (implement in settings)

---

### **CRIT-02: Missing Medical Disclaimers**
**Severity:** `CRITICAL – POLICY VIOLATION RISK`

**Issue:**  
The app performs hearing tests, creates audiograms, and provides personalized audio amplification based on hearing profiles, but contains NO disclaimers that:
1. This is not a medical device
2. Does not provide diagnosis or treatment
3. Should not replace professional hearing care

**Files Affected:**
- `OnboardingCarouselActivity.java:75-96` – Onboarding flow with no disclaimers
- `StartTestActivity.java` – Hearing test entry with no disclaimers
- `GeneralInstructionActivity.java` – Test instructions with no disclaimers
- `RightEarInstructionActivity.java` / `LeftEarInstructionActivity.java` – Pure tone test instructions with no disclaimers
- `CalibrationInstructionActivity.java` – Calibration instructions with no disclaimers
- `TestResultsActivity.java` – Results display with no disclaimers
- `HearingProfileActivity.java` – Profile/audiogram display with no disclaimers

**Policy Violation:**  
Google Play's Health Apps policy (Developer Program Policies § Health) requires that apps handling health-related functionality clearly disclose:
- The app is not a substitute for professional medical advice
- The app does not diagnose or treat medical conditions
- Users should consult healthcare professionals for medical concerns

**Internal Code Evidence of Medical-Adjacent Functionality:**
```java
// CalibrationProfile.java:7
* Calibration profile for storing personalized hearing aid settings

// WDRCCompressor.java:4
* Wide Dynamic Range Compressor (WDRC) for clinical hearing aid DSP.

// PersonalizedGainMapper.java:91
* This creates adaptive compression curves similar to commercial hearing aids.

// AudioConfig.java:23
// OUTPUT: STEREO (for binaural hearing aid output - we'll duplicate mono input)
```

**Fix Required:**

1. **Add disclaimer to OnboardingCarouselActivity** (before "Get Started"):
   ```
   ⚠️ Important Notice
   
   Audion is a personal sound amplification tool, not a medical device.
   
   This app does not diagnose, treat, cure, or prevent any medical condition.
   If you suspect hearing loss or have concerns about your hearing,
   please consult a licensed audiologist or healthcare provider.
   ```

2. **Add disclaimer to StartTestActivity** (before "Get Started"):
   ```
   Note: This hearing check is for personal use only and should not
   replace professional hearing evaluations. Results are estimates.
   ```

3. **Add persistent footer to HearingProfileActivity / TestResultsActivity**:
   ```
   ℹ️ For informational purposes only. Not a medical diagnosis.
   Consult an audiologist for professional hearing care.
   ```

4. **Update internal code comments** to remove "hearing aid" terminology:
   - Change "hearing aid" → "hearing support" or "audio amplification"
   - Change "clinical" → "consumer-grade"
   - Ensure positioning as PSAP (Personal Sound Amplification Product), not medical device

---

### **CRIT-03: No Age Verification Implemented**
**Severity:** `CRITICAL – POLICY REQUIREMENT`

**Issue:**  
The app collects health-related data (hearing test results, audiograms) but does not verify user age or implement 18+ age gate. This is problematic because:
1. Children's data requires parental consent (COPPA compliance)
2. Health data from minors requires guardian authorization
3. Hearing tests on minors without professional supervision could be misleading/harmful

**Files Affected:**
- `OnboardingCarouselActivity.java` – First run screen, no age verification
- `WhatsYourNameActivity.java` – Name entry, no age gate
- All user onboarding flows

**Policy Violation:**  
Google Play Developer Program Policies require age-appropriate content and data collection practices. Apps targeting general audiences but collecting health data should implement age gates or clearly state "18+ only" in Play Store listing.

**Fix Required:**

1. **Add age verification screen after OnboardingCarouselActivity**:
   ```java
   // New AgeVerificationActivity
   "Are you 18 years or older?"
   [Yes, I'm 18+]  [No]
   
   If "No": "Audion is designed for adults 18 and older. Please use
   under supervision of a parent or guardian."
   ```

2. **Update Play Store listing:**
   - Set age rating appropriately
   - Add "18+ recommended" to description
   - Disclose in Data Safety that health data is collected

3. **Alternative approach** (if targeting all ages):
   - Implement parental consent flow for users under 18
   - Add "Guardian Mode" with guardian email verification
   - Restrict data collection for minors

---

### **CRIT-04: Missing FOREGROUND_SERVICE_MICROPHONE Permission**
**Severity:** `CRITICAL – ANDROID 14+ REQUIREMENT`

**Issue:**  
The app uses foreground services (`AudioStreamingService`, `SimpleAudioStreamingService`) that record audio for real-time amplification, but the manifest does not declare `FOREGROUND_SERVICE_MICROPHONE` permission type.

**Files Affected:**
- `AndroidManifest.xml:7` – Has `FOREGROUND_SERVICE` but missing `FOREGROUND_SERVICE_MICROPHONE`
- `AndroidManifest.xml:178-181` – Services declare `foregroundServiceType="mediaPlayback"` but should include `microphone`
- `AudioStreamingService.java:119` – Calls `startForeground()` while using AudioRecord
- `SimpleAudioStreamingService.java:144` – Calls `startForeground()` while using AudioRecord

**Policy Violation:**  
Android 14 (API 34) requires explicit foreground service type declarations for microphone usage. Without this, the app will crash on Android 14+ devices when attempting to start the service.

**Current Manifest:**
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<service
    android:name=".AudioStreamingService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

**Fix Required:**
```xml
<!-- Add microphone permission type -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- Update service declarations -->
<service
    android:name=".AudioStreamingService"
    android:exported="false"
    android:foregroundServiceType="microphone|mediaPlayback" />

<service
    android:name=".SimpleAudioStreamingService"
    android:exported="false"
    android:foregroundServiceType="microphone|mediaPlayback" />
```

**Test on Android 14+ devices** after fix to ensure services start correctly.

---

### **CRIT-05: Sensitive Health Data Storage Without Encryption**
**Severity:** `CRITICAL – DATA SAFETY RISK`

**Issue:**  
The app stores sensitive hearing-related health data in a Room database without encryption. While local-only storage is good, sensitive health data should be encrypted at rest.

**Files Affected:**
- `AppDatabase.java:233-236` – Room database builder with no encryption
- `HearingTestResult.java` – Stores audiogram thresholds (health data)
- `CalibrationEntry.java` – Stores hearing sensitivity data
- `CalibrationProfileEntity.java` – Stores MCL/UCL data (hearing comfort/discomfort levels)

**Evidence:**
```java
// AppDatabase.java lines 233-236
instance = Room.databaseBuilder(
    context.getApplicationContext(),
    AppDatabase.class,
    "app_database"
)
// No .openHelperFactory(SafeHelperFactory...) call
```

**Policy Concern:**  
While not explicitly required by Google Play, storing health data unencrypted could be flagged during security review. Best practice for health apps is to encrypt sensitive data at rest.

**Fix Required:**

1. **Add SQLCipher encryption** (recommended):
   ```gradle
   // app/build.gradle
   implementation "net.zetetic:android-database-sqlcipher:4.5.4"
   implementation "androidx.sqlite:sqlite:2.3.1"
   ```

   ```java
   // AppDatabase.java
   import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;
   
   byte[] passphrase = SQLiteDatabase.getBytes(getEncryptionKey(context).toCharArray());
   SupportFactory factory = new SupportFactory(passphrase);
   
   instance = Room.databaseBuilder(...)
       .openHelperFactory(factory)
       .build();
   ```

2. **Store encryption key securely** in Android Keystore

3. **Update Data Safety declaration** in Play Console to reflect encryption

**Alternative:** If encryption is not feasible, add prominent disclaimer that data is stored locally on device and recommend device encryption.

---

## ⚠️ **MAJOR ISSUES (High Priority)**

### **MAJ-01: INTERNET Permission Declared But Not Used**
**Severity:** `MAJOR – POLICY SCRUTINY RISK`

**Issue:**  
`AndroidManifest.xml` declares `INTERNET` permission, but no network code exists in the codebase (no HTTP/HTTPS calls, no analytics, no crash reporting, no Firebase, no ads).

**Files Affected:**
- `AndroidManifest.xml:13` – `<uses-permission android:name="android.permission.INTERNET" />`

**Evidence:**
```bash
# grep search for network usage
grep -r "http://|https://|HttpURLConnection|OkHttp|Retrofit|analytics|Firebase" **/*.java
# Result: Only documentation URLs in comments, no actual network code
```

**Policy Concern:**  
Google Play scrutinizes unused permissions, especially INTERNET. Declaring permissions without using them can:
1. Raise red flags during review (why does a hearing app need internet?)
2. Concern privacy-conscious users
3. Delay approval if reviewers request justification

**Fix Required:**

**Option A (Recommended):** Remove the permission if truly unused:
```xml
<!-- Remove this line from AndroidManifest.xml -->
<!-- <uses-permission android:name="android.permission.INTERNET" /> -->
```

**Option B:** If you plan to add network features (future analytics, cloud sync, updates), document the intended use:
- Add a TODO comment explaining future use
- Update Privacy Policy to reflect planned network usage
- Implement the feature before submission OR remove permission until ready

**Option C:** Check if third-party libraries require it:
- `vosk-android:0.3.32+` (speech recognition) – May use internet for model downloads
- If library requires it, keep permission but document in Privacy Policy

---

### **MAJ-02: "Hearing Aid" Terminology in Internal Code**
**Severity:** `MAJOR – CLASSIFICATION RISK`

**Issue:**  
While user-facing text correctly positions Audion as a consumer tool, internal code comments repeatedly use "hearing aid" terminology. If Google's automated or manual review scans code, this could trigger medical device classification.

**Files Affected:**
- `CalibrationProfile.java:7` – "personalized hearing aid settings"
- `WDRCCompressor.java:4` – "clinical hearing aid DSP"
- `PersonalizedGainMapper.java:91, 200` – "commercial hearing aids"
- `AudioConfig.java:23` – "binaural hearing aid output"

**Evidence:**
```java
// CalibrationProfile.java line 7
/**
 * Calibration profile for storing personalized hearing aid settings
 */

// WDRCCompressor.java line 4
/**
 * Wide Dynamic Range Compressor (WDRC) for clinical hearing aid DSP.
 */
```

**Policy Risk:**  
FDA and international regulators classify "hearing aids" as medical devices requiring certification. While Audion positions itself as a PSAP (Personal Sound Amplification Product), code comments suggesting "hearing aid" functionality could:
1. Trigger FDA scrutiny if flagged by Google
2. Require medical device certification
3. Block Play Store approval pending regulatory compliance proof

**Fix Required:**

**Search and replace across all code:**
- "hearing aid" → "hearing support device" or "audio amplification device"
- "clinical" → "consumer-grade" or "personalized"
- "medical" → "personal" or "assistive"

**Specific fixes:**
```java
// CalibrationProfile.java line 7
/**
 * Calibration profile for storing personalized audio amplification settings
 */

// WDRCCompressor.java line 4
/**
 * Wide Dynamic Range Compressor (WDRC) for consumer hearing support DSP.
 */

// PersonalizedGainMapper.java line 91
* This creates adaptive compression curves similar to consumer PSAPs.
```

**Add package-level disclaimer:**
```java
/**
 * Audion Audio Amplification System
 * 
 * IMPORTANT: This software is a Personal Sound Amplification Product (PSAP)
 * for consumer use only. It is NOT a medical device, hearing aid, or intended
 * to diagnose, treat, or cure any medical condition.
 * 
 * Intended for adults 18+ with mild to moderate hearing challenges in
 * specific listening environments. Not a substitute for professional hearing care.
 */
package com.audion.audio;
```

---

### **MAJ-03: MediaProjection Dialog Wording Could Confuse Reviewers**
**Severity:** `MAJOR – REJECTION RISK`

**Issue:**  
The MediaProjection permission dialog attempts to explain that "screen recording" is only for audio, but the wording might confuse reviewers or users. The phrase "Android will show a 'Screen Recording' permission - this is normal!" could be seen as trying to mislead users.

**Files Affected:**
- `HomeActivity.java:584-592` – MediaProjection permission request dialog

**Current Dialog:**
```java
.setMessage("To amplify phone media (music, videos, calls), Audion needs to capture your device's audio output.\n\n" +
    "⚠️ Android will show a 'Screen Recording' permission - this is normal!\n\n" +
    "Audion ONLY captures audio, never your screen. This is Android's security requirement for any app accessing system audio.\n\n" +
    "Your privacy is protected - no screen recording occurs.")
```

**Policy Concern:**  
Google Play reviewers may flag this as:
1. Attempting to downplay a serious permission request
2. Confusing users about what the app actually does
3. Potentially accessing screen content (even if you don't)

**Fix Required:**

**Revised wording (clearer, more honest):**
```java
.setTitle("Audio Capture Permission")
.setMessage("To amplify music, videos, and calls from your phone, Audion needs to access your device's internal audio.\n\n" +
    "Android will ask for 'Screen Recording' permission because internal audio capture uses the same system.\n\n" +
    "What Audion does:\n" +
    "✅ Captures audio only (music, videos, calls)\n" +
    "✅ Applies personalized amplification\n" +
    "✅ Plays enhanced audio to your earbuds\n\n" +
    "What Audion does NOT do:\n" +
    "❌ Record your screen\n" +
    "❌ Capture visuals or text\n" +
    "❌ Store or transmit any data\n\n" +
    "All processing happens locally on your device.")
```

**Additional safeguards:**
- Add Privacy Policy section explaining MediaProjection usage
- Implement one-time consent with "Don't ask again" option
- Add in-app FAQ explaining the permission

---

### **MAJ-04: No Data Deletion Option in Settings**
**Severity:** `MAJOR – GDPR/PRIVACY REQUIREMENT`

**Issue:**  
The app collects and stores user data (name, hearing test results, calibration profiles) but provides no way for users to delete this data from within the app.

**Files Affected:**
- `SettingsActivity.java` – No delete data option
- `HearingProfileActivity.java` – No delete profile option
- All data storage without corresponding deletion

**Policy Violation:**  
Google Play Data Safety requirements and GDPR mandate that users must be able to:
1. View what data is collected
2. Delete their data
3. Export their data (optional but recommended)

**Fix Required:**

1. **Add "Delete All Data" option in SettingsActivity:**
   ```java
   MaterialButton deleteDataButton = findViewById(R.id.deleteDataButton);
   deleteDataButton.setOnClickListener(v -> {
       new AlertDialog.Builder(this)
           .setTitle("Delete All Data?")
           .setMessage("This will permanently delete:\n" +
               "• Your hearing test results\n" +
               "• Calibration profiles\n" +
               "• Personal settings\n\n" +
               "This action cannot be undone.")
           .setNegativeButton("Cancel", null)
           .setPositiveButton("Delete Everything", (dialog, which) -> {
               deleteAllUserData();
           })
           .show();
   });
   
   private void deleteAllUserData() {
       new Thread(() -> {
           AppDatabase db = AppDatabase.getInstance(this);
           db.clearAllTables();
           getSharedPreferences("AudionPrefs", MODE_PRIVATE)
               .edit().clear().apply();
           runOnUiThread(() -> {
               Toast.makeText(this, "All data deleted", Toast.LENGTH_SHORT).show();
               // Restart onboarding
               startActivity(new Intent(this, OnboardingCarouselActivity.class));
               finish();
           });
       }).start();
   }
   ```

2. **Add "Delete This Profile" option in HearingProfileActivity** for individual profile deletion

3. **Add "Export My Data" option** (recommended for GDPR compliance):
   - Export hearing test results as CSV or PDF
   - Allow users to save audiogram charts
   - Already have PDF generation library (`com.itextpdf:itextg:5.5.10`) in dependencies

---

### **MAJ-05: No Explicit Consent for Data Collection**
**Severity:** `MAJOR – PRIVACY REQUIREMENT`

**Issue:**  
The app starts collecting data (name, hearing test results) without explicit user consent. The legal text in `WhatsYourNameActivity` mentions terms/privacy policy, but there's no checkbox or explicit opt-in.

**Files Affected:**
- `WhatsYourNameActivity.java:43-44` – Stores user name without explicit consent
- `PureToneTestActivity.java` – Records hearing data without consent dialog
- No consent tracking mechanism

**Policy Requirement:**  
Google Play Data Safety and GDPR require explicit, informed consent before collecting personal or health data.

**Fix Required:**

1. **Add consent checkbox in WhatsYourNameActivity:**
   ```xml
   <CheckBox
       android:id="@+id/consentCheckbox"
       android:layout_width="wrap_content"
       android:layout_height="wrap_content"
       android:text="I consent to Audion storing my hearing test data locally on this device. I understand this data is for personal use only and is not shared or transmitted." />
   ```

   ```java
   btnContinue.setEnabled(false);
   consentCheckbox.setOnCheckedChangeListener((btn, checked) -> {
       btnContinue.setEnabled(checked);
   });
   ```

2. **Store consent timestamp:**
   ```java
   getSharedPreferences("AudionPrefs", MODE_PRIVATE)
       .edit()
       .putBoolean("data_consent_granted", true)
       .putLong("data_consent_timestamp", System.currentTimeMillis())
       .apply();
   ```

3. **Check consent before data operations:**
   ```java
   if (!getSharedPreferences("AudionPrefs", MODE_PRIVATE)
           .getBoolean("data_consent_granted", false)) {
       // Show consent dialog or redirect to onboarding
   }
   ```

---

## 📝 **MINOR / UX / COPY ISSUES (Nice to Fix)**

### **MIN-01: Inconsistent Hearing Loss Terminology**

**Issue:**  
`CalibrationTestActivity.java` toasts reference "hearing loss" without proper context:
- "Maximum level reached for [X]Hz - possible hearing loss"
- "High tolerance detected for [X]Hz"

**Recommendation:**  
Soften language to avoid alarming users:
- "Maximum level reached for [X] Hz. Consider consulting an audiologist."
- "High tolerance detected for [X] Hz. This may indicate reduced sensitivity at this frequency."

---

### **MIN-02: Amplitude Warnings Too Technical**

**Issue:**  
HomeActivity amplification dialogs use clinical language:
- "Moderate Amplification" / "High Amplification"
- "moderate hearing loss" / "severe hearing loss"

**Recommendation:**  
Use consumer-friendly wording:
- "Strong Amplification" / "Very Strong Amplification"
- "This level is quite high. Ensure it feels comfortable."

*(Note: This has been partially addressed in the updated documentation but should be reflected in code)*

---

### **MIN-03: No Earbuds Detection Warning**

**Issue:**  
The app performs hearing tests and amplification but doesn't consistently verify earbuds are connected. Users might perform tests through phone speakers, yielding invalid results.

**Current Implementation:**
- `EarbudsChecker` class exists but not consistently used
- `EarbudsRequiredBottomSheet` implemented but not triggered in all test flows

**Recommendation:**  
Add earbuds check in:
- `StartTestActivity.onCreate()`
- `RightEarInstructionActivity` before starting test
- `HomeActivity` when enabling amplification

---

### **MIN-04: Calibration Instruction Wording**

**Issue:**  
Current text: "We'll play 3 standard levels for your right ear calibration"

**Recommendation:**  
User documentation update suggests: "We'll play three tones to fine-tune your right ear."

**Action:** Implement this wording change in code (already updated in documentation).

---

### **MIN-05: Missing Help/FAQ Content**

**Issue:**  
`HelpActivity.java` exists but contains placeholder FAQ content:
- "How do I adjust volume?" / "Use the slider under Volume."

**Recommendation:**  
Expand help content to cover:
- What is Audion? (PSAP, not hearing aid)
- How accurate are the hearing tests?
- Why does Android ask for "Screen Recording"?
- How to delete my data
- Link to Privacy Policy and Terms of Service

---

## 📊 **PERMISSIONS & DATA HANDLING ANALYSIS**

| Permission / Data | Where Used | Purpose | User Explanation | Comment |
|-------------------|------------|---------|------------------|---------|
| **RECORD_AUDIO** | `AudioStreamingService.java:66-69`<br>`SimpleAudioStreamingService.java:147-150`<br>`PureToneTestActivity.java`<br>`FocusActivity.java` | Capture environmental audio for amplification; Record audio for hearing tests; Speaker diarization in Focus mode | ✅ Yes – Requested at runtime with dialogs in `RightEarInstructionActivity:61-69`, `FocusActivity:1484-1490` | **Appropriate use.** Permission is essential for core functionality. |
| **MODIFY_AUDIO_SETTINGS** | `AudioStreamingService.java`<br>`SimpleAudioStreamingService.java` | Adjust audio routing, volume levels, and audio session parameters for amplification | ⚠️ Implicit – No explicit user dialog, but purpose is obvious from feature use | **Acceptable.** Normal permission, no runtime dialog needed. |
| **FOREGROUND_SERVICE** | `AudioStreamingService.java:119`<br>`SimpleAudioStreamingService.java:144` | Keep audio processing running when app is in background | ✅ Yes – Notification shown: "Audio Streaming" / "Running…" | **Compliant.** Proper notification implemented. |
| **FOREGROUND_SERVICE_MEDIA_PLAYBACK** | `AndroidManifest.xml:11` | Declared for media playback service type | ✅ Yes – Implicit through notification | **Compliant.** Appropriate service type. |
| **FOREGROUND_SERVICE_MICROPHONE** | ❌ **MISSING** | Required for microphone use in foreground services on Android 14+ | ❌ **NOT DECLARED** | ⚠️ **CRITICAL ISSUE** – See CRIT-04 above. |
| **READ_EXTERNAL_STORAGE** (maxSdk=32) | `MusicPlayerActivity.java:166-170` | Access music files for amplified playback | ✅ Yes – Permission dialog: "We need audio access to list your music files." | **Appropriate.** Deprecated on Android 13+, properly scoped. |
| **READ_MEDIA_AUDIO** | `MusicPlayerActivity.java` | Access audio files on Android 13+ | ✅ Yes – Same dialog as above | **Compliant.** Proper Android 13+ replacement for READ_EXTERNAL_STORAGE. |
| **VIBRATE** | Not observed in code | Likely for haptic feedback (UI interactions) | ⚠️ No explicit explanation | **Low risk.** Normal permission, minimal privacy impact. |
| **INTERNET** | ❌ **NOT USED** | No network code found in codebase | ❌ **NOT EXPLAINED** | ⚠️ **ISSUE** – See MAJ-01. Remove or justify. |
| **User Name** | `WhatsYourNameActivity.java:43`<br>`AppDatabase` | Personalize app experience, profile management | ⚠️ Weak – Legal text mentions policy but no explicit consent | **FIX NEEDED** – See MAJ-05. Add consent checkbox. |
| **Hearing Test Data** | `HearingTestResult` entity<br>`AppDatabase.java` | Store audiogram thresholds for personalized amplification | ❌ No explicit consent or explanation | **CRITICAL FIX NEEDED** – See CRIT-02, MAJ-05. Add disclaimers and consent. |
| **Calibration Data** | `CalibrationEntry` entity<br>`CalibrationProfileEntity` | Store MCL/UCL (comfortable/uncomfortable levels) for amplification | ❌ No explicit consent or explanation | **CRITICAL FIX NEEDED** – Add disclaimer that data is for personal use only. |
| **MediaProjection Data** | `SimpleAudioStreamingService.java:462-490` | Capture phone audio (music, videos, calls) for amplification | ⚠️ Partial – Dialog explains but could be clearer | **FIX RECOMMENDED** – See MAJ-03. Improve dialog wording. |

---

## 🏥 **HEALTH & HEARING-SPECIFIC COMPLIANCE**

### **Positioning Analysis: Is Audion a Medical Device?**

**Based on codebase review:**

| Medical Device Indicator | Present? | Evidence |
|--------------------------|----------|----------|
| Claims to diagnose hearing conditions | ❌ No | No diagnostic claims in user-facing text |
| Claims to treat/cure hearing loss | ❌ No | No treatment claims found |
| Prescribes medical interventions | ❌ No | User-controlled amplification only |
| Requires healthcare provider involvement | ❌ No | Consumer self-administered |
| FDA/CE marking required | ❌ No | PSAP classification intended |
| Performs clinical-grade audiometry | ⚠️ **Borderline** | Pure tone audiometry is clinical-grade, but positioned as "hearing check" |
| Uses medical terminology internally | ⚠️ **Yes** | "hearing aid", "clinical", "WDRC" in code comments (see MAJ-02) |
| Stores health data | ✅ **Yes** | Audiogram data is considered health information |
| Targets medical conditions | ❌ No | Consumer hearing support for adults |

**Verdict:**  
**Audion DOES NOT behave like a medical device in practice**, BUT:
- Internal code terminology needs cleanup (MAJ-02)
- Must add explicit disclaimers (CRIT-02)
- Should position clearly as PSAP, not hearing aid

---

### **PSAP vs. Hearing Aid Compliance**

**Personal Sound Amplification Products (PSAPs):**
- ✅ Consumer devices for mild hearing challenges
- ✅ Self-administered, no prescription needed
- ✅ Amplify specific sounds in specific environments
- ✅ Not FDA-regulated as medical devices
- ⚠️ Must NOT claim to treat hearing loss

**Audion's Current Positioning:**

✅ **Strengths:**
- User-facing text avoids medical claims
- Self-administered tests and calibration
- Consumer-grade amplification (0-40 dB limit)
- No prescription required
- Onboarding emphasizes "personalize your hearing" not "fix your hearing"

⚠️ **Risks:**
- Pure tone audiometry is clinical test methodology
- MCL/UCL calibration is hearing aid fitting procedure
- WDRC compression is medical-grade DSP
- Internal code uses "hearing aid" terminology

**Recommendation:**  
Audion is positioned as a PSAP but uses clinical methods. To stay compliant:
1. Add disclaimers everywhere (CRIT-02)
2. Clean up code terminology (MAJ-02)
3. Emphasize "hearing support" over "hearing test"
4. Make clear: "For informational purposes, not medical advice"

---

### **Google Play Health App Requirements Checklist**

| Requirement | Status | Evidence/Action |
|-------------|--------|-----------------|
| Clearly state not a medical device | ❌ **Missing** | **CRIT-02:** Add disclaimers |
| Do not claim to diagnose | ✅ Pass | No diagnostic claims found |
| Do not claim to treat/cure | ✅ Pass | No treatment claims found |
| Recommend professional consultation | ❌ **Missing** | **CRIT-02:** Add to disclaimers |
| Accurate & non-misleading claims | ⚠️ **Risk** | "Pure tone test" sounds clinical – soften to "hearing check" |
| Privacy Policy for health data | ❌ **Missing** | **CRIT-01:** Implement privacy policy |
| Secure storage of health data | ⚠️ **Weak** | **CRIT-05:** Add encryption recommended |
| User consent for data collection | ❌ **Missing** | **MAJ-05:** Add consent mechanism |
| Data deletion capability | ❌ **Missing** | **MAJ-04:** Add delete option |
| Age-appropriate safeguards | ❌ **Missing** | **CRIT-03:** Add 18+ gate |

---

## 🏗️ **TECHNICAL COMPLIANCE**

### **64-bit Native Libraries Requirement**

✅ **COMPLIANT**

**Evidence:**
- `app/src/main/jniLibs/arm64-v8a/libonnxruntime.so` – Present
- `app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so` – Present
- `CMakeLists.txt:34-36` – RNNoise compiled as shared library (will produce .so for all ABIs)

**Also present (good for compatibility):**
- `armeabi-v7a/` – 32-bit ARM (optional but good for older devices)
- `x86/`, `x86_64/` – Emulator support

✅ **No 64-bit blocker.** App meets Google Play's 64-bit requirement.

---

### **Target SDK Compliance**

✅ **COMPLIANT**

**Current Configuration:**
```gradle
// app/build.gradle
compileSdk 34
targetSdk 34
minSdk 27
```

**Google Play Requirement (2024-2025):**
- New apps: targetSdk 34 (Android 14) ✅
- App updates: targetSdk 33+ ✅

✅ **Target SDK meets current and future Play Store requirements.**

---

### **ProGuard / R8 Configuration**

⚠️ **NEEDS ATTENTION**

**Current State:**
```gradle
// app/build.gradle line 27
buildTypes {
    release {
        minifyEnabled false  // ⚠️ Code shrinking disabled
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

**Issues:**
1. `minifyEnabled false` – No code shrinking or obfuscation in release builds
2. `proguard-rules.pro` – Minimal rules, mostly commented out

**Recommendation:**
1. Enable minification for release builds:
   ```gradle
   release {
       minifyEnabled true
       shrinkResources true
   ```

2. Add rules for Room, Gson, ONNX Runtime:
   ```proguard
   # Room
   -keep class * extends androidx.room.RoomDatabase
   -keep @androidx.room.Entity class *
   
   # Gson
   -keepattributes Signature
   -keep class com.example.audion.data.** { *; }
   
   # ONNX Runtime
   -keep class com.k2fsa.sherpa.onnx.** { *; }
   ```

**Not a blocker** for initial submission, but recommended for production.

---

### **Export Restrictions & Security**

✅ **COMPLIANT**

**Manifest Security:**
```xml
android:allowBackup="true"  // ✅ Acceptable for consumer app
android:dataExtractionRules="@xml/data_extraction_rules"  // ✅ Android 12+ rules present
android:fullBackupContent="@xml/backup_rules"  // ✅ Backup rules present
```

**Services properly secured:**
```xml
<service android:name=".AudioStreamingService" android:exported="false" />
<service android:name=".SimpleAudioStreamingService" android:exported="false" />
```
✅ **No exported services that could be exploited.**

**Activities:**
```xml
<activity android:name=".MainActivity" android:exported="true" />
<activity android:name=".FocusActivity" android:exported="true" />
```
⚠️ **Minor concern:** Two activities exported without explicit `intent-filter`. Likely intended for deep linking, but verify intent or set `exported="false"`.

---

## ✅ **RELEASE READINESS CHECKLIST**

Use this checklist before Play Store submission:

### **Critical (Must Fix):**
- [ ] **CRIT-01:** Privacy Policy implemented and linked in app + Play Console
- [ ] **CRIT-02:** Medical disclaimers added to onboarding, test screens, and results
- [ ] **CRIT-03:** Age verification (18+) implemented in onboarding flow
- [ ] **CRIT-04:** `FOREGROUND_SERVICE_MICROPHONE` permission added to manifest
- [ ] **CRIT-05:** Database encryption implemented OR disclaimer added about local storage

### **Major (High Priority):**
- [ ] **MAJ-01:** INTERNET permission removed OR justified with network feature implementation
- [ ] **MAJ-02:** "Hearing aid" terminology removed from all code comments
- [ ] **MAJ-03:** MediaProjection dialog wording improved for clarity
- [ ] **MAJ-04:** Data deletion option added to Settings
- [ ] **MAJ-05:** Explicit data collection consent added to onboarding

### **Data Safety Declaration (Play Console):**
- [ ] Data types collected: Name (identity), Hearing test results (health), Calibration data (health)
- [ ] Data sharing: None (all data stored locally)
- [ ] Data encryption: [Encrypted / Stored in plain text on device]
- [ ] Data deletion: Users can request deletion via in-app settings
- [ ] Privacy Policy URL: [Add your URL]

### **Store Listing Requirements:**
- [ ] App title does NOT contain "hearing aid" or medical claims
- [ ] Short description clarifies: "Personal sound amplification tool (not a medical device)"
- [ ] Full description includes: "Audion is a PSAP for adults 18+, not a substitute for professional hearing care"
- [ ] Age rating set appropriately (Teen 13+ or Mature 17+ recommended)
- [ ] Category: Health & Fitness OR Tools (NOT Medical)
- [ ] Content rating questionnaire completed accurately (no medical claims)

### **Technical Requirements:**
- [x] 64-bit native libraries present (arm64-v8a) ✅
- [x] Target SDK 34 ✅
- [x] Foreground services use proper notification channels ✅
- [ ] Manifest: `FOREGROUND_SERVICE_MICROPHONE` permission added
- [ ] Test on Android 14+ devices with microphone permission enforcement
- [ ] Test MediaProjection flow on Android 10-14

### **Legal & Compliance:**
- [ ] Privacy Policy hosted at public URL
- [ ] Terms of Service document created
- [ ] PSAP positioning consistent throughout app and code
- [ ] No medical claims in any user-facing text
- [ ] No implied diagnosis, treatment, or cure promises

### **Testing Before Submission:**
- [ ] Test full onboarding flow with new disclaimers
- [ ] Verify age gate functions correctly
- [ ] Test hearing test + calibration flow end-to-end
- [ ] Verify data deletion works completely
- [ ] Test MediaProjection dialog and permission flow
- [ ] Test on devices with Android 10, 12, 13, 14
- [ ] Verify foreground services work on Android 14+
- [ ] Check all permissions are requested at appropriate times

---

## 📋 **RECOMMENDATIONS FOR POST-LAUNCH**

### **Phase 1: Immediate Post-Launch (Week 1-2)**
1. Monitor crash reports for permission-related crashes (Android 14 foreground service)
2. Track user drop-off at age gate / consent screens
3. Monitor Play Console for policy violation flags
4. Gather user feedback on MediaProjection permission clarity

### **Phase 2: Enhancements (Month 1-3)**
1. Implement data export feature (audiogram PDF, CSV results)
2. Add "What's My Hearing Age?" feature (gamification, not medical)
3. Improve Help/FAQ with video tutorials
4. Add "Safe Listening" tips and warnings
5. Implement hearing test quality metrics (reliability scores)

### **Phase 3: Advanced Features (Month 3-6)**
1. Multi-user profiles for family use (with separate age gates)
2. Hearing test history and trend visualization
3. Environmental sound classifier ("You're in a: Restaurant / Quiet room / Street")
4. Accessibility improvements (TalkBack support, high contrast mode)
5. Integration with hearing health providers (referral network, NOT diagnosis)

---

## 🔐 **PRIVACY & SECURITY BEST PRACTICES**

### **Current State: Good but Needs Improvement**

✅ **Strengths:**
- All data stored locally (no cloud sync)
- No third-party analytics or tracking observed
- No ads or monetization (fewer privacy concerns)
- Room database provides structured data management
- Foreground services properly use notifications

⚠️ **Weaknesses:**
- No data encryption at rest
- No explicit user consent flow
- No data deletion mechanism
- No privacy policy implementation
- No data export capability

### **Recommended Security Enhancements:**

1. **Implement EncryptedSharedPreferences:**
   ```gradle
   implementation "androidx.security:security-crypto:1.1.0-alpha06"
   ```
   ```java
   EncryptedSharedPreferences.create(
       context,
       "AudionPrefs",
       MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
       EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
       EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
   );
   ```

2. **Add certificate pinning** if network features added in future

3. **Implement biometric authentication** for sensitive data access (optional)

4. **Add tamper detection** for release builds

5. **Secure logging:** Remove sensitive data from logs in production
   ```java
   if (BuildConfig.DEBUG) {
       Log.d(TAG, "User data: " + data);
   }
   ```

---

## 📞 **CONTACT & SUPPORT FOR REVIEWERS**

### **Suggested Support Infrastructure**

Before Play Store submission, set up:

1. **Support Email:** support@audion.app (or similar)
   - Respond to reviewer questions within 24 hours
   - Have template responses for common policy questions

2. **Privacy Policy Page:**
   - Host at: https://yourwebsite.com/privacy
   - Include sections:
     - What data we collect
     - How we use it
     - How we protect it
     - User rights (access, deletion, export)
     - Contact information

3. **Terms of Service:**
   - Clear PSAP positioning
   - Disclaimer of warranties
   - Limitation of liability
   - User responsibilities (appropriate use)

4. **Developer Website:**
   - About Audion
   - How it works (with disclaimer)
   - FAQ
   - Support contact

5. **Demo Video for Reviewers:**
   - Record screen walkthrough showing:
     - Onboarding with disclaimers
     - Hearing test flow
     - Calibration process
     - Amplification feature
     - Data deletion
   - Upload to YouTube (unlisted)
   - Include link in Play Console review notes

---

## 🎯 **CONCLUSION**

### **Current Status: NOT READY FOR SUBMISSION**

Audion is a well-engineered consumer hearing support app with solid technical foundations, but **critical compliance gaps** prevent immediate Play Store submission.

**Blockers Summary:**
1. ❌ No Privacy Policy (legal requirement)
2. ❌ No medical disclaimers (policy violation risk)
3. ❌ No age verification (health data from minors issue)
4. ❌ Missing `FOREGROUND_SERVICE_MICROPHONE` permission (Android 14+ crash risk)
5. ⚠️ "Hearing aid" terminology in code (medical device classification risk)

**Estimated Time to Fix:**
- **Critical fixes:** 2-3 days (privacy policy, disclaimers, permissions, age gate)
- **Major fixes:** 3-5 days (code terminology, data deletion, consent flow)
- **Testing & validation:** 2-3 days
- **Total:** ~1-2 weeks for submission-ready build

**Risk Level if Submitted As-Is:**
- **Immediate rejection:** High (80%+ probability due to missing privacy policy)
- **Policy violation flag:** High (health data without disclaimers)
- **Android 14 crashes:** Guaranteed (missing permission type)
- **Legal exposure:** Medium (health data collection without consent)

**Next Steps:**
1. Address all CRITICAL issues (CRIT-01 through CRIT-05)
2. Implement MAJOR fixes (MAJ-01 through MAJ-05)
3. Run through Release Readiness Checklist
4. Test on Android 10-14 devices
5. Prepare Play Console listing with Data Safety declaration
6. Submit for review with detailed notes explaining PSAP positioning

---

**Audit Prepared By:** AI Code Auditor  
**Audit Date:** November 19, 2025  
**Next Review Recommended:** After implementing critical fixes, before Play Store submission

---

**DISCLAIMER:** This audit is based on static code analysis and policy interpretation as of November 2025. Google Play policies evolve frequently. Consult official documentation and consider legal review before submission.
