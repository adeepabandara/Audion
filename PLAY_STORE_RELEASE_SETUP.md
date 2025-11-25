# Play Store Release Setup Guide

This guide covers the remaining setup steps needed to build and publish Audion to Google Play Store.

## ✅ Completed Fixes

1. **Debug Logging Removed** - All production debug logs commented out
2. **Package Name Updated** - Changed from `com.example.audion` to `com.audion.app`
3. **Signing Config Template** - Added to `local.properties`

---

## 🔴 Critical: Configure Release Signing

The app cannot be released without proper signing credentials.

### Step 1: Obtain Keystore Credentials

You need the following information for your keystore file (`app/audion-release.jks`):
- Keystore password
- Key alias name
- Key password

If you don't remember these credentials:
- Check your password manager or secure notes
- Check documentation where you created the keystore
- If lost, you'll need to create a new keystore (this means you cannot update an existing app on Play Store)

### Step 2: Update local.properties

Open `/Users/adeepabandara/Documents/GitHub/Audion/local.properties` and replace the placeholder values:

```properties
RELEASE_STORE_FILE=app/audion-release.jks
RELEASE_STORE_PASSWORD=your_actual_keystore_password
RELEASE_KEY_ALIAS=your_actual_key_alias
RELEASE_KEY_PASSWORD=your_actual_key_password
```

**⚠️ IMPORTANT**: Never commit `local.properties` to git! It's already in `.gitignore`.

---

## 🔴 Critical: Downgrade Java to Version 21

Your system currently has Java 25.0.1, but Android Gradle Plugin 8.7.3 requires Java 17 or 21.

### Using SDKMAN (Recommended)

```bash
# Install SDKMAN if not already installed
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 21
sdk install java 21.0.1-tem

# Set Java 21 as default
sdk default java 21.0.1-tem

# Verify installation
java -version
# Should show: openjdk version "21.0.1"
```

### Using Homebrew (Alternative)

```bash
# Install Java 21
brew install openjdk@21

# Link it
sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Update JAVA_HOME in ~/.zshrc
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
source ~/.zshrc

# Verify
java -version
```

---

## 🧪 Testing the Release Build

### Step 1: Clean Previous Builds

```bash
cd /Users/adeepabandara/Documents/GitHub/Audion
./gradlew clean
```

### Step 2: Build Release AAB (Required by Play Store)

```bash
./gradlew bundleRelease
```

This will create: `app/build/outputs/bundle/release/app-release.aab`

### Step 3: Build Release APK (For Testing)

```bash
./gradlew assembleRelease
```

This will create: `app/build/outputs/apk/release/app-release.apk`

---

## 📦 Upload to Google Play Console

### Step 1: Create App Listing

1. Go to [Google Play Console](https://play.google.com/console)
2. Create new app or select existing "Audion" app
3. Fill in required information:
   - App name: Audion
   - Default language: English (United States)
   - App or game: App
   - Free or paid: Free

### Step 2: Upload AAB

1. Navigate to **Production** → **Create new release**
2. Upload `app/build/outputs/bundle/release/app-release.aab`
3. Fill in release notes
4. Review and roll out

### Step 3: Complete Store Listing

Required fields:
- App icon (512x512 PNG)
- Feature graphic (1024x500 PNG)
- Screenshots (minimum 2)
- Short description (80 chars max)
- Full description (4000 chars max)
- Privacy policy URL: https://www.audion.live/privacy-policy ✅ (already configured)

---

## ✅ Pre-Release Checklist

Before uploading to Play Store:

- [ ] Java version is 21 (not 25)
- [ ] Signing credentials configured in `local.properties`
- [ ] `./gradlew bundleRelease` succeeds
- [ ] Test release APK on device
- [ ] Privacy policy accessible at https://www.audion.live/privacy-policy
- [ ] No debug logs in production code
- [ ] Package name is `com.audion.app` (not com.example.audion)
- [ ] Version code incremented if updating existing app
- [ ] Screenshots and graphics prepared
- [ ] Store listing content written

---

## 🔧 Troubleshooting

### Build Fails with "Unsupported class file major version"

**Cause**: Java version too new  
**Solution**: Downgrade to Java 21 (see instructions above)

### Build Fails with "SigningConfig 'release' is missing required property 'storeFile'"

**Cause**: Signing credentials not configured  
**Solution**: Update `local.properties` with actual credentials (see Step 2)

### Native Library Errors

**Cause**: JNI function names changed with package rename  
**Solution**: Already fixed - `native-lib.cpp` updated to use `com_audion_app`

### App Crashes on Launch

**Possible causes**:
- Old package name cached in Android Studio
- Need to uninstall previous version
- **Solution**: `adb uninstall com.example.audion && ./gradlew installRelease`

---

## 📊 Build Information

**Current Configuration**:
- Package: `com.audion.app`
- Version: 1.0.0-beta.1 (versionCode 2)
- Target SDK: 34 (Android 14)
- Min SDK: 27 (Android 8.1)
- Build Tools: Gradle 8.13, AGP 8.7.3
- ProGuard: Enabled (minifyEnabled true, shrinkResources true)
- Supported ABIs: arm64-v8a, armeabi-v7a

---

## 📞 Support

If you encounter issues:
1. Check Gradle build output for specific error messages
2. Verify all prerequisites are met (Java 21, signing config)
3. Clean and rebuild: `./gradlew clean && ./gradlew bundleRelease`
4. Check Android Studio's Build → Analyze APK to inspect the build

---

**Last Updated**: November 21, 2025  
**Status**: Ready for release build after completing Critical tasks
