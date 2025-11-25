# 🚀 GOOGLE PLAY STORE PUBLISHING READINESS AUDIT

**Audit Date:** November 21, 2025  
**App Name:** Audion  
**Package ID:** com.audion.psap  
**Target SDK:** 34 (Android 14)  
**Min SDK:** 27 (Android 8.1)  
**Version Code:** 2  
**Version Name:** 1.0.0-internal  
**Audited Branch:** demo2  
**Audit Type:** Pre-Release / Testing Track Readiness

---

## 📊 EXECUTIVE SUMMARY

### 🔴 **STATUS: NOT READY FOR PUBLICATION**

**Critical Blockers:** 3  
**High Priority Issues:** 5  
**Medium Priority Issues:** 4  
**Low Priority Warnings:** 6

**Estimated Time to Fix Critical Issues:** 4-8 hours  
**Recommended Action:** Address all critical and high-priority issues before submission

---

## 🚨 CRITICAL BLOCKING ISSUES

### ❌ **CRIT-01: Build Failure - Lint Check Issues**

**Severity:** `BLOCKER`  
**Impact:** Release build cannot complete successfully

**Issue:**
The release build fails during the lint analysis phase with Java version compatibility errors:
```
java.lang.IllegalArgumentException: 25.0.1
at com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:308)
```

**Root Cause:**
- Using Java 25.0.1 (OpenJDK Temurin)
- Android Gradle Plugin 8.7.3 lint tools have compatibility issues with Java 25
- Lint analysis is mandatory for release builds and cannot be bypassed for Play Store submission

**Impact on Publishing:**
- ❌ Cannot generate signed release APK/AAB
- ❌ Cannot proceed with Play Store upload
- ❌ Blocks all publishing workflows

**Fix Required:**
```bash
# Option 1: Downgrade Java to LTS version 17 or 21
# Using SDKMAN:
sdk install java 21.0.1-tem
sdk use java 21.0.1-tem

# Option 2: Update gradle.properties to force specific Java version
org.gradle.java.home=/path/to/java-21
```

**Verification:**
```bash
java -version  # Should show Java 17 or 21
./gradlew clean assembleRelease  # Should succeed
```

---

### ❌ **CRIT-02: Code Obfuscation Disabled**

**Severity:** `CRITICAL - SECURITY & POLICY`  
**Impact:** App can be easily reverse-engineered, potential Play Store rejection

**Current Configuration:**
```gradle
// app/build.gradle (line 45)
buildTypes {
    release {
        signingConfig signingConfigs.release
        minifyEnabled false  // ❌ DISABLED
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

**Issues:**
1. **Security Risk:** All Java code is fully readable when decompiled
2. **IP Protection:** DSP algorithms, hearing profile logic exposed
3. **App Size:** Unnecessary code not stripped (larger APK/AAB)
4. **Policy Compliance:** Some Play Store categories require obfuscation

**Consequences:**
- Competitors can easily clone your DSP pipeline
- Sensitive audio processing algorithms fully visible
- Calibration methods and hearing profile calculations exposed
- Larger app size affects conversion rates

**Fix Required:**
```gradle
buildTypes {
    release {
        signingConfig signingConfigs.release
        minifyEnabled true           // ✅ ENABLE
        shrinkResources true         // ✅ ENABLE
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

**ProGuard Rules Needed:**
```proguard
# app/proguard-rules.pro - ADD THESE RULES

# Keep Room database entities and DAOs
-keep class com.audion.psap.data.** { *; }
-keepclassmembers class com.audion.psap.data.** { *; }

# Keep native method declarations
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep DSP classes if accessed via reflection
-keep class com.audion.dsp.** { *; }

# Keep Gson models
-keepclassmembers class com.audion.psap.models.** { *; }
-keep class com.audion.psap.models.** { *; }

# Keep VOSK/Sherpa ONNX classes
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# Keep JNA classes
-keep class net.java.dev.jna.** { *; }
-dontwarn net.java.dev.jna.**

# Keep iTextPDF for PDF generation
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Keep Lottie animations
-keep class com.airbnb.lottie.** { *; }

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
```

**Testing Required:**
1. Test with obfuscation enabled
2. Verify all features work (Room, PDF export, audio processing)
3. Test crash reports are properly deobfuscated
4. Upload mapping.txt to Play Console for crash reports

---

### ❌ **CRIT-03: Weak Signing Configuration**

**Severity:** `CRITICAL - SECURITY`  
**Impact:** Default passwords in production build, security vulnerability

**Current Configuration:**
```gradle
// app/build.gradle (lines 34-39)
signingConfigs {
    release {
        storeFile file(localProperties.getProperty('RELEASE_STORE_FILE') ?: 'audion-release.jks')
        storePassword localProperties.getProperty('RELEASE_STORE_PASSWORD') ?: 'android'  // ❌
        keyAlias localProperties.getProperty('RELEASE_KEY_ALIAS') ?: 'audion-release'
        keyPassword localProperties.getProperty('RELEASE_KEY_PASSWORD') ?: 'android'      // ❌
    }
}
```

**Issues:**
1. Default passwords "android" hardcoded as fallback
2. Keystore file committed to repository: `app/audion-release.jks` (FOUND)
3. If local.properties is missing, insecure defaults are used
4. Keystore should NEVER be in version control

**Security Risks:**
- Anyone with repo access can sign malicious updates
- Compromised keystore = can't publish updates (new package name required)
- Google Play requires you maintain the same signing key forever
- If key is stolen, attackers can publish malicious updates

**Fix Required:**

1. **Remove keystore from repository immediately:**
```bash
cd /Users/adeepabandara/Documents/GitHub/Audion
git rm app/audion-release.jks
git commit -m "Remove keystore from version control"
git push
```

2. **Update .gitignore:**
```
# Add to /Users/adeepabandara/Documents/GitHub/Audion/.gitignore
*.jks
*.keystore
local.properties
```

3. **Store keystore securely:**
```bash
# Move keystore to secure location OUTSIDE repo
mkdir -p ~/.android/keystores
mv app/audion-release.jks ~/.android/keystores/

# Update local.properties (NEVER commit this file)
echo "RELEASE_STORE_FILE=$HOME/.android/keystores/audion-release.jks" >> local.properties
echo "RELEASE_STORE_PASSWORD=<your-strong-password>" >> local.properties
echo "RELEASE_KEY_ALIAS=audion-release" >> local.properties
echo "RELEASE_KEY_PASSWORD=<your-strong-password>" >> local.properties
```

4. **Update build.gradle to fail without proper credentials:**
```gradle
signingConfigs {
    release {
        def keystorePropertiesFile = rootProject.file('local.properties')
        if (!keystorePropertiesFile.exists()) {
            throw new GradleException("local.properties not found! Release builds require signing configuration.")
        }
        
        def keystoreProperties = new Properties()
        keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
        
        storeFile file(keystoreProperties['RELEASE_STORE_FILE'])
        storePassword keystoreProperties['RELEASE_STORE_PASSWORD']
        keyAlias keystoreProperties['RELEASE_KEY_ALIAS']
        keyPassword keystoreProperties['RELEASE_KEY_PASSWORD']
        
        // Verify all properties exist
        [storeFile, storePassword, keyAlias, keyPassword].each {
            if (it == null || it.toString().isEmpty()) {
                throw new GradleException("Missing signing configuration in local.properties")
            }
        }
    }
}
```

5. **For CI/CD:** Use GitHub Secrets or secure environment variables

**⚠️ IMPORTANT:** If the current keystore with default passwords was used to upload ANY version to Play Store:
- That keystore MUST continue to be used (Google Play requirement)
- Generate a new keystore with strong passwords
- Use Play App Signing to let Google manage the upload key

---

## 🔴 HIGH PRIORITY ISSUES

### ⚠️ **HIGH-01: Excessive Debug Logging in Production**

**Severity:** `HIGH - PERFORMANCE & PRIVACY`  
**Impact:** Performance degradation, potential privacy leaks

**Found Issues:**
```java
// 20+ instances found in:
- com/audion/dsp/LookaheadLimiter.java:59 - Log.i() with detailed DSP parameters
- com/example/audion/config/FeatureFlags.java:35,45,62,127 - Log.d()/Log.i() feature toggles
- com/example/audion/diarization/DiarizationBufferManager.java:29,50,54,79 - Log.d() buffer states
- com/k2fsa/sherpa/onnx/*.java - Multiple Log.i()/Log.d() calls
- FrequencySweepActivity.java:155,159 - Log.d() with profile IDs
```

**Problems:**
1. Log calls execute even if logs aren't displayed (CPU cycles wasted)
2. String concatenation in log statements creates garbage (memory pressure)
3. Sensitive data potentially logged (profile IDs, audio buffer states)
4. Logcat accessible to other apps with READ_LOGS permission on rooted devices

**Fix Required:**

**Option 1: Conditional Logging (Immediate Fix)**
```java
// Create BuildConfig-aware logger
public class AudionLogger {
    public static void d(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }
    
    public static void i(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message);
        }
    }
}

// Replace all Log.d/i/v calls with AudionLogger equivalents
```

**Option 2: ProGuard Stripping (Recommended)**
Already included in CRIT-02 ProGuard rules:
```proguard
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
```

**Keep Log.e() and Log.w()** for crash reporting

---

### ⚠️ **HIGH-02: Version Name Not Production-Ready**

**Severity:** `HIGH - PUBLISHING REQUIREMENT`  
**Impact:** Version name indicates internal build, not suitable for public release

**Current Configuration:**
```gradle
// app/build.gradle (line 21)
versionCode 2
versionName "1.0.0-internal"  // ❌ "-internal" suffix
```

**Issues:**
1. Play Console will show "1.0.0-internal" to users
2. Indicates non-production quality
3. Doesn't follow semantic versioning best practices for releases

**Fix Required:**
```gradle
defaultConfig {
    applicationId "com.audion.psap"
    minSdk 27
    targetSdk 34
    versionCode 2
    versionName "1.0.0"  // ✅ Clean version for testing track
    
    // OR if this is a beta:
    // versionName "1.0.0-beta.1"  // More appropriate for testing track
}
```

**Version Strategy for Play Store:**
- **Internal Testing:** 1.0.0-alpha.1, 1.0.0-alpha.2
- **Closed Testing:** 1.0.0-beta.1, 1.0.0-beta.2
- **Open Testing:** 1.0.0-rc.1 (release candidate)
- **Production:** 1.0.0

---

### ⚠️ **HIGH-03: Missing Privacy Policy URL**

**Severity:** `HIGH - PLAY STORE REQUIREMENT`  
**Impact:** May be rejected during review for health/personal data collection

**Current State:**
```xml
<!-- strings.xml has placeholder -->
<string name="privacy_policy_url">https://www.audion.live/privacy-policy</string>
```

**Issues:**
1. String defined but URL may not exist/be live
2. No visible link in app to Privacy Policy
3. Play Store requires privacy policy for apps collecting hearing data
4. `WhatsYourNameActivity` references policy but doesn't link to it

**Verification Needed:**
```bash
# Test if URL exists
curl -I https://www.audion.live/privacy-policy
```

**Fix Required:**
1. **Ensure privacy policy is published and accessible**
2. **Add clickable links in app:**

```java
// WhatsYourNameActivity.java (around line 43-44)
// Make the text clickable
TextView legalText = findViewById(R.id.legalText);
String text = "By tapping \"Continue\", you agree to our Terms of Service and Privacy Policy";
SpannableString spannableString = new SpannableString(text);

int tosStart = text.indexOf("Terms of Service");
int ppStart = text.indexOf("Privacy Policy");

spannableString.setSpan(new ClickableSpan() {
    @Override
    public void onClick(View widget) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
            Uri.parse("https://www.audion.live/terms"));
        startActivity(browserIntent);
    }
}, tosStart, tosStart + "Terms of Service".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

spannableString.setSpan(new ClickableSpan() {
    @Override
    public void onClick(View widget) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
            Uri.parse(getString(R.string.privacy_policy_url)));
        startActivity(browserIntent);
    }
}, ppStart, ppStart + "Privacy Policy".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

legalText.setText(spannableString);
legalText.setMovementMethod(LinkMovementMethod.getInstance());
```

3. **Add Settings menu item:**
```java
// SettingsActivity.java - Add menu item
Preference privacyPolicyPref = findPreference("privacy_policy");
privacyPolicyPref.setOnPreferenceClickListener(preference -> {
    Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
        Uri.parse(getString(R.string.privacy_policy_url)));
    startActivity(browserIntent);
    return true;
});
```

4. **Play Console Configuration:**
   - Add privacy policy URL in Play Console → App content → Privacy policy
   - Must be the same URL as in-app links

---

### ⚠️ **HIGH-04: Medical Disclaimers May Be Insufficient**

**Severity:** `HIGH - LEGAL COMPLIANCE`  
**Impact:** Potential medical device classification risk

**Current Disclaimers:**
```xml
<string name="medical_disclaimer_onboarding">Audion is a personal sound amplification tool, not a medical device. It does not diagnose, treat, or cure hearing loss. For hearing concerns, please consult a licensed audiologist or healthcare professional.</string>
```

**Issues:**
1. Disclaimer exists but visibility/prominence unknown
2. No verification that users see and acknowledge disclaimer
3. Play Store health apps require explicit user consent
4. Some jurisdictions require disclaimers on every screen showing health data

**Fix Required:**

1. **Show disclaimer as mandatory acknowledgment:**
```java
// Add to OnboardingCarouselActivity before allowing test
private void showMedicalDisclaimerDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Medical Disclaimer");
    builder.setMessage(getString(R.string.medical_disclaimer_onboarding));
    builder.setPositiveButton("I Understand", (dialog, which) -> {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("disclaimer_accepted", true).apply();
        proceedToTest();
    });
    builder.setNegativeButton("Cancel", (dialog, which) -> {
        finish();
    });
    builder.setCancelable(false);
    builder.show();
}
```

2. **Add footer to results screens:**
```xml
<!-- TestResultsActivity layout -->
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/medical_disclaimer_results"
    android:textSize="10sp"
    android:textColor="@color/gray_500"
    android:gravity="center"
    android:padding="8dp" />
```

3. **Store acceptance:**
```java
// Track that user has seen and accepted disclaimer
SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
prefs.edit()
    .putBoolean("disclaimer_accepted", true)
    .putLong("disclaimer_accepted_timestamp", System.currentTimeMillis())
    .apply();
```

---

### ⚠️ **HIGH-05: App Size Concerns**

**Severity:** `HIGH - USER ACQUISITION`  
**Impact:** Large APK size reduces install conversion rates

**Analysis:**
```
Assets detected:
- VOSK speech recognition model: ~40MB (vosk-model-small-en-us-0.15)
- ONNX models: embedding.onnx, segmentation.onnx (~5-10MB estimated)
- Lottie animations: JSON files (small)
- Native libraries: arm64-v8a, armeabi-v7a, x86 (RNNoise + custom)
```

**Concerns:**
1. Users on cellular won't install large apps
2. Play Store shows warnings for >150MB apps
3. VOSK model may not be essential for core functionality
4. Multiple ABIs increase size

**Investigation Required:**
```bash
# Check actual APK size
cd app/build/outputs/apk/debug/
ls -lh app-debug.apk

# Analyze APK contents
cd /Users/adeepabandara/Documents/GitHub/Audion
./gradlew assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E '(\.so|\.onnx|vosk)'
```

**Optimization Options:**

1. **Use Android App Bundle (.aab)** instead of APK:
```bash
./gradlew bundleRelease
# Play Store generates optimized APKs per device (only needed ABIs)
```

2. **On-demand download for VOSK:**
```java
// Download speech models only if user enables voice features
// Use Play Asset Delivery or Firebase Storage
```

3. **Remove unused ABIs:**
```gradle
android {
    defaultConfig {
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a'  // Remove x86
        }
    }
}
```

4. **Compress assets:**
```gradle
android {
    aaptOptions {
        cruncherEnabled = true
        noCompress 'onnx'  // Don't compress already-compressed files
    }
}
```

**Target:** Keep APK under 100MB, AAB under 150MB

---

## 🟡 MEDIUM PRIORITY ISSUES

### ⚠️ **MED-01: Activities Missing `exported` Declarations**

**Severity:** `MEDIUM - FUTURE COMPATIBILITY`  
**Impact:** Required for Android 12+ (API 31+), your targetSdk 34 already requires this

**Affected Activities (implicit exports):**
```xml
<!-- Several activities missing explicit android:exported attribute -->
<activity android:name=".UserCreationActivity" 
    android:windowSoftInputMode="adjustResize"/>  <!-- ❌ Missing exported -->

<activity android:name=".CaptionActivity" 
    android:windowSoftInputMode="adjustResize"/>  <!-- ❌ Missing exported -->

<!-- Many more... -->
```

**Status:** 
- Currently builds because most activities are implicitly `exported="false"`
- May cause issues in future Android versions
- Play Store reviewers may flag as warning

**Fix Required:**
Explicitly declare for ALL activities:
```xml
<activity 
    android:name=".UserCreationActivity"
    android:exported="false"  <!-- ✅ Explicit -->
    android:windowSoftInputMode="adjustResize"/>

<activity 
    android:name=".MainActivity"
    android:exported="false"/>  <!-- ✅ Only MAIN/LAUNCHER should be true -->
```

**⚠️ Note:** Only `SplashActivity` with LAUNCHER intent should have `exported="true"`

---

### ⚠️ **MED-02: Unused Permissions May Trigger Review Questions**

**Severity:** `MEDIUM - REVIEW DELAY`  
**Impact:** Play Store may request justification

**Potentially Unused:**
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

**Analysis Required:**
- Search codebase for file/media access
- If only used for PDF export (iTextPDF), may not need READ_MEDIA_AUDIO
- Ensure Data Safety form in Play Console explains each permission

**Action:**
```bash
# Check if actually used
cd /Users/adeepabandara/Documents/GitHub/Audion
grep -r "READ_EXTERNAL_STORAGE\|MediaStore\|ContentResolver" app/src/main/java/
```

If not used, remove from manifest.

---

### ⚠️ **MED-03: Network Security Config Missing**

**Severity:** `MEDIUM - SECURITY BEST PRACTICE`  
**Impact:** App doesn't explicitly define network security policy

**Current State:**
No `android:networkSecurityConfig` in AndroidManifest.xml

**Risk:**
- Defaults to allowing cleartext traffic on older Android versions
- No certificate pinning
- No custom trust anchors

**Fix (if app makes network calls):**
```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    
    <!-- Allow localhost for testing if needed -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```

**If app is fully offline:** Add to manifest:
```xml
<uses-permission android:name="android.permission.INTERNET" 
    tools:node="remove" />
```

---

### ⚠️ **MED-04: Missing App Metadata**

**Severity:** `MEDIUM - DISCOVERABILITY`  
**Impact:** Affects Play Store search and categorization

**Missing Elements:**
```xml
<!-- Should add to AndroidManifest.xml -->
<application>
    <!-- App category for Android 12+ -->
    <meta-data
        android:name="android.app.category"
        android:value="accessibility" />
    
    <!-- Support for different screen sizes -->
    <meta-data
        android:name="android.max_aspect"
        android:value="2.4" />
</application>
```

Also missing from manifest:
```xml
<supports-screens
    android:smallScreens="false"
    android:normalScreens="true"
    android:largeScreens="true"
    android:xlargeScreens="true"
    android:requiresSmallestWidthDp="320" />
```

---

## 🟢 LOW PRIORITY WARNINGS

### ⚠️ **LOW-01: Deprecated API Usage**

**Severity:** `LOW - FUTURE MAINTENANCE`

```
compileReleaseJavaWithJavac:
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
```

**Action:** Run with deprecation warnings enabled to identify:
```bash
./gradlew assembleDebug -Xlint:deprecation
```

Fix when time permits to avoid future breaking changes.

---

### ⚠️ **LOW-02: Unchecked Operations Warning**

```
Note: ResultsEarFragment.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
```

**Location:** `ResultsEarFragment.java:130`
```java
new TypeToken<Map<String, Float>>(){}.getType()  // Unchecked cast
```

**Fix:**
```java
// Add @SuppressWarnings or explicit type checking
Type type = new TypeToken<Map<String, Float>>(){}.getType();
```

---

### ⚠️ **LOW-03: Duplicate Dependencies**

**Found in build.gradle:**
```gradle
implementation 'androidx.core:core-ktx:1.12.0'  // Line 85
implementation "androidx.core:core-ktx:1.12.0"  // Line 100 (duplicate)

implementation "androidx.appcompat:appcompat:1.6.1"  // Duplicates libs.appcompat
```

**Fix:** Remove duplicates, use version catalog consistently

---

### ⚠️ **LOW-04: Missing Content Ratings**

**Action Required in Play Console:**
- Complete IARC questionnaire
- Answer questions about:
  - User-generated content (if any)
  - Audio recording features
  - Hearing test suitability for children
  - Violence, gambling, etc. (likely all "No")

---

### ⚠️ **LOW-05: Native Library Stripping Warning**

```
Unable to strip the following libraries, packaging them as they are: libjnidispatch.so
```

**Impact:** Slightly larger APK (includes debug symbols)

**Cause:** JNA library doesn't support symbol stripping

**Fix:** Low priority, minimal impact

---

### ⚠️ **LOW-06: Missing Adaptive Icon for All Densities**

**Current State:**
- Has `app_icon.png` in drawable
- Has WebP icons in mipmap folders
- Has adaptive icon XML

**Verification Needed:**
Check all density folders have proper adaptive icons:
```bash
ls -la app/src/main/res/mipmap-*/ic_launcher*
```

---

## ✅ POSITIVE FINDINGS

### **✓ Compliance Strengths**

1. **✅ Target SDK 34** - Meets current Google Play requirement (min SDK 34 for new apps)
2. **✅ 64-bit Support** - Native libraries compiled for arm64-v8a
3. **✅ Foreground Service Types** - Properly declared (microphone|mediaPlayback)
4. **✅ Runtime Permissions** - RECORD_AUDIO requested at runtime
5. **✅ Data Backup Rules** - Has `backup_rules.xml` and `data_extraction_rules.xml`
6. **✅ Localization Ready** - values-night, values-sw600dp for tablets
7. **✅ Material Design** - Uses Material Components
8. **✅ ViewBinding** - Modern view access (not findViewById)
9. **✅ Room Database** - Proper local data persistence
10. **✅ No Ads/Monetization** - Cleaner review process

---

## 📋 PLAY CONSOLE PREPARATION CHECKLIST

### **Before Upload:**

- [ ] **Build Configuration**
  - [ ] Fix Java version to 17 or 21
  - [ ] Enable ProGuard/R8 (minifyEnabled true)
  - [ ] Test release build succeeds
  - [ ] Generate signed AAB (not APK): `./gradlew bundleRelease`

- [ ] **Security**
  - [ ] Remove keystore from Git
  - [ ] Use strong keystore passwords
  - [ ] Store keystore backup securely (multiple locations)
  - [ ] Document keystore credentials securely

- [ ] **Code Quality**
  - [ ] Remove/disable debug logging
  - [ ] Fix deprecated API warnings
  - [ ] Verify all features work with obfuscation

- [ ] **App Content**
  - [ ] Change versionName to "1.0.0" or "1.0.0-beta.1"
  - [ ] Increment versionCode for each upload
  - [ ] Verify privacy policy URL is live and accessible
  - [ ] Test all disclaimer dialogs appear

### **Play Console Setup:**

- [ ] **Store Listing**
  - [ ] App name: "Audion" (or check availability)
  - [ ] Short description (80 chars)
  - [ ] Full description
  - [ ] Screenshots (phone, tablet, if applicable)
  - [ ] Feature graphic (1024x500)
  - [ ] App icon (512x512)

- [ ] **App Category & Tags**
  - [ ] Category: Medical OR Lifestyle (NOT Medical Device)
  - [ ] Tags: hearing, accessibility, sound amplification, PSAP

- [ ] **Content Rating**
  - [ ] Complete IARC questionnaire
  - [ ] Expected rating: Everyone or 12+ (if hearing test has restrictions)

- [ ] **App Content**
  - [ ] Privacy policy URL: https://www.audion.live/privacy-policy
  - [ ] App access: Free (no restrictions)
  - [ ] Ads: No
  - [ ] Target audience: Adults (18+) recommended for medical disclaimers
  - [ ] Content guidelines: Accessibility

- [ ] **Data Safety**
  - [ ] Data collection: Yes (hearing test data)
  - [ ] Data types: Health and fitness (hearing test results)
  - [ ] Data usage: App functionality, Personalization
  - [ ] Data sharing: None
  - [ ] Security practices: Data encrypted in transit (if any network), locally stored
  - [ ] User controls: Can delete data (Settings → Delete All Data)

- [ ] **Permissions Declaration**
  - [ ] RECORD_AUDIO: For hearing tests and sound amplification
  - [ ] FOREGROUND_SERVICE: To maintain audio processing in background
  - [ ] READ_MEDIA_AUDIO: [Justify if needed, remove if not]
  - [ ] VIBRATE: Haptic feedback during tests

### **Testing Track Strategy:**

1. **Internal Testing (first):**
   - Upload version 1.0.0-alpha.1
   - Test with 5-10 team members
   - Verify installation, permissions, core features
   - Duration: 3-7 days

2. **Closed Testing (second):**
   - Upload version 1.0.0-beta.1
   - Invite 20-100 beta testers
   - Gather feedback on UX, bugs, performance
   - Duration: 1-4 weeks

3. **Open Testing (optional):**
   - Upload version 1.0.0-rc.1
   - Public beta (anyone can join)
   - Final validation before production
   - Duration: 1-2 weeks

4. **Production:**
   - Upload version 1.0.0
   - Phased rollout: 10% → 25% → 50% → 100%

---

## 🔧 RECOMMENDED BUILD COMMANDS

### **Fix Build Environment:**
```bash
# 1. Install Java 21 (recommended)
sdk install java 21.0.1-tem
sdk use java 21.0.1-tem

# Verify
java -version  # Should show 21.x.x
```

### **Clean Build:**
```bash
cd /Users/adeepabandara/Documents/GitHub/Audion

# Clean all build artifacts
./gradlew clean

# Build debug (for testing fixes)
./gradlew assembleDebug

# Build release (after fixing issues)
./gradlew assembleRelease

# Build App Bundle for Play Store
./gradlew bundleRelease
```

### **Locate Output:**
```bash
# AAB (upload this to Play Console)
open app/build/outputs/bundle/release/

# APK (for direct testing)
open app/build/outputs/apk/release/
```

### **Test Release Build:**
```bash
# Install release APK on device
adb install -r app/build/outputs/apk/release/app-release.apk

# Check logs
adb logcat | grep -i audion
```

---

## 🎯 PRIORITY ACTION PLAN

### **Phase 1: Build Blockers (Day 1 - 4 hours)**

1. **Downgrade Java** to version 21
   - Estimated time: 30 minutes
   - Verify: `./gradlew assembleRelease` succeeds

2. **Remove Keystore from Git**
   - Estimated time: 30 minutes
   - Update .gitignore, move keystore, update local.properties

3. **Enable ProGuard**
   - Estimated time: 2 hours
   - Add ProGuard rules
   - Test all features with obfuscation
   - Fix any runtime issues (Room, reflection, etc.)

4. **Fix Version Name**
   - Estimated time: 5 minutes
   - Change to "1.0.0-beta.1"

### **Phase 2: Policy Compliance (Day 2 - 2 hours)**

5. **Verify Privacy Policy**
   - Estimated time: 30 minutes
   - Ensure URL is live
   - Add clickable links in app

6. **Medical Disclaimer Dialog**
   - Estimated time: 1 hour
   - Add mandatory acceptance flow
   - Test user experience

7. **Clean Up Logging**
   - Estimated time: 30 minutes
   - Verify ProGuard strips logs OR wrap in BuildConfig.DEBUG

### **Phase 3: Polish (Day 3 - 2 hours)**

8. **Add Explicit `exported` Attributes**
   - Estimated time: 30 minutes
   - Update all activities in manifest

9. **Remove Unused Permissions**
   - Estimated time: 30 minutes
   - Audit and remove if not needed

10. **Fix Duplicate Dependencies**
    - Estimated time: 15 minutes
    - Clean up build.gradle

11. **Test Release Build End-to-End**
    - Estimated time: 1 hour
    - Install on physical device
    - Test all features
    - Verify signing

### **Phase 4: Play Console Setup (Day 4 - 3 hours)**

12. **Create Play Console Listing**
13. **Complete IARC Content Rating**
14. **Fill Data Safety Form**
15. **Upload Internal Testing Build**

**Total Estimated Time:** 11-15 hours

---

## 📞 SUPPORT & RESOURCES

### **Google Play Console Help:**
- [Pre-launch checklist](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [App signing](https://support.google.com/googleplay/android-developer/answer/9842756)

### **Android Developer Docs:**
- [Build and release](https://developer.android.com/studio/publish)
- [ProGuard rules](https://developer.android.com/build/shrink-code)
- [App bundle](https://developer.android.com/guide/app-bundle)

### **Testing Commands:**
```bash
# Check APK size
ls -lh app/build/outputs/apk/release/app-release.apk

# Analyze APK contents
./gradlew analyzeReleaseBundle

# Test on device
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.audion.psap/.SplashActivity
```

---

## ✅ FINAL CHECKLIST BEFORE UPLOAD

```
BUILD READINESS:
[ ] Java 21 installed and configured
[ ] ./gradlew clean bundleRelease succeeds
[ ] ProGuard enabled (minifyEnabled true)
[ ] Release AAB generated successfully
[ ] No critical lint errors (warnings acceptable)

SECURITY:
[ ] Keystore removed from Git repository
[ ] Strong keystore passwords in local.properties
[ ] Keystore backed up to 2+ secure locations
[ ] mapping.txt saved for crash deobfuscation

CODE QUALITY:
[ ] Debug logs stripped/disabled in release
[ ] Version name set correctly (no "-internal")
[ ] All activities have explicit exported attribute
[ ] No unused permissions in manifest

COMPLIANCE:
[ ] Privacy policy URL live and accessible
[ ] Medical disclaimer shown and accepted by user
[ ] Data Safety form completed in Play Console
[ ] IARC content rating completed

PLAY CONSOLE:
[ ] App name, description, screenshots uploaded
[ ] Feature graphic and app icon uploaded
[ ] Internal/Closed/Open testing track selected
[ ] First upload successful with no errors

TESTING:
[ ] Release build installed on 2+ physical devices
[ ] All core features tested (hearing test, calibration, amplification)
[ ] Permissions requested properly
[ ] No crashes in critical user flows
[ ] Audio processing works correctly
```

---

## 📊 RISK ASSESSMENT

| Issue | Probability | Impact | Risk Score |
|-------|------------|--------|------------|
| Build Failure (Java) | **HIGH** | **CRITICAL** | 🔴 **10/10** |
| Keystore Compromised | **MEDIUM** | **CRITICAL** | 🔴 **9/10** |
| No Obfuscation | **HIGH** | **HIGH** | 🟠 **8/10** |
| Privacy Policy Missing | **LOW** | **HIGH** | 🟠 **7/10** |
| Medical Disclaimer Insufficient | **LOW** | **MEDIUM** | 🟡 **5/10** |
| Large APK Size | **MEDIUM** | **MEDIUM** | 🟡 **5/10** |

---

## 🎉 CONCLUSION

Your app **Audion** is architecturally sound with excellent DSP implementation and user experience design. However, **critical build and security issues block immediate publication**.

**Estimated time to production-ready:** 2-3 days of focused work

**Recommended path:**
1. Fix Java version + build configuration (Day 1)
2. Security fixes + policy compliance (Day 1-2)
3. Testing and validation (Day 2-3)
4. Internal testing track upload (Day 3)

Once the critical issues are resolved, the app should pass Play Store review for the **Internal Testing** track without major problems.

**Next Steps:**
1. Start with Phase 1 (Build Blockers)
2. Verify each fix with `./gradlew assembleRelease`
3. Proceed sequentially through phases
4. Reach out if you encounter blockers

Good luck with your launch! 🚀

---

**Audit conducted by:** GitHub Copilot  
**Report generated:** November 21, 2025  
**Audit version:** 1.0
