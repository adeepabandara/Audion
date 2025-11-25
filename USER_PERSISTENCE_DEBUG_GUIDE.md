# User Persistence Debugging Guide

## Issue
When a user enters their name and continues, then closes the app and reopens it, the app shows the onboarding carousel again instead of navigating to the "Personalize Your Hearing" (StartTestActivity) page.

## Root Cause Investigation
The issue appears to be related to database persistence. The user creation happens in `NameActivity` but `SplashActivity` may not be finding the user on app restart.

## Changes Made

### 1. Enhanced Logging in NameActivity.java
Added comprehensive logging to track the user creation process:

```java
// Location: NameActivity.java, line ~85-115
Log.d("NameActivity", "===== CREATING NEW USER =====");
Log.d("NameActivity", "User name: " + userName);

// Insert user and get ID
User user = new User(userName);
long userId = userDao.insert(user);

Log.d("NameActivity", "✅ User inserted successfully! User ID: " + userId);

// Create hearing profile
HearingProfile hearingProfile = new HearingProfile(userName + "_Profile", "default");
long hearingProfileId = hearingProfileDao.insert(hearingProfile);

Log.d("NameActivity", "✅ Hearing profile created! Profile ID: " + hearingProfileId);

// Verify the user was actually saved by reading it back
List<User> allUsers = userDao.getAllUsers();
Log.d("NameActivity", "🔍 Verification: Total users in database: " + (allUsers != null ? allUsers.size() : 0));
if (allUsers != null) {
    for (User u : allUsers) {
        Log.d("NameActivity", "  User: ID=" + u.getId() + ", Name='" + u.getName() + "'");
    }
}

Log.d("NameActivity", "===== DATABASE WRITE COMPLETE =====");
```

### 2. Error Handling
Added try-catch block with error logging and user feedback:

```java
} catch (Exception e) {
    Log.e("NameActivity", "❌ Error creating user: " + e.getMessage(), e);
    runOnUiThread(() -> {
        Toast.makeText(NameActivity.this, 
            "Error saving profile. Please try again.", 
            Toast.LENGTH_SHORT).show();
    });
}
```

### 3. Added Required Imports
- `android.util.Log` - for logging
- `android.widget.Toast` - for error messages
- `java.util.List` - for user list verification

## Database Information
- **Database Name**: `audion_db`
- **Database Version**: 7
- **Relevant Tables**: 
  - `users` (User entity)
  - `hearing_profiles` (HearingProfile entity)
  - `hearing_test_results` (HearingTestResult entity)
  - `calibration_entries` (CalibrationEntry entity)

## Testing Instructions

### Step 1: Install the Latest Build
```bash
cd /Users/adeepabandara/Documents/GitHub/Audion
./gradlew installDebug
```

### Step 2: Clear App Data (Optional but Recommended)
To ensure a clean test:
- Settings → Apps → Audion → Storage → Clear Data

### Step 3: Test User Creation
1. Open the app
2. Go through the onboarding carousel
3. Enter your name on the name entry screen
4. Click Continue

### Step 4: Monitor Logcat
While testing, monitor the logcat output:

```bash
adb logcat -s NameActivity:D SplashActivity:D AppDatabase:D
```

Look for these log messages:

#### When Creating User (NameActivity):
```
D/NameActivity: ===== CREATING NEW USER =====
D/NameActivity: User name: [your name]
D/NameActivity: ✅ User inserted successfully! User ID: [number]
D/NameActivity: ✅ Hearing profile created! Profile ID: [number]
D/NameActivity: 🔍 Verification: Total users in database: [number]
D/NameActivity:   User: ID=[number], Name='[your name]'
D/NameActivity: ===== DATABASE WRITE COMPLETE =====
```

#### When Reopening App (SplashActivity):
```
D/SplashActivity: ===== CHECKING FOR EXISTING USERS =====
D/SplashActivity: Total users found: [number]
D/SplashActivity: Checking user - ID: [number], Name: '[your name]'
D/SplashActivity: ✓ Found valid user: [your name] (ID: [number])
D/SplashActivity: Pure tone results count: 0
D/SplashActivity: Hearing profiles count: [number]
D/SplashActivity: Calibration entries count: 0
D/SplashActivity: ===== NAVIGATION DECISION =====
D/SplashActivity: Has user: true, Has completed test: false
D/SplashActivity: → Navigating to StartTestActivity (continue incomplete test)
```

### Step 5: Close and Reopen App
1. After entering your name and continuing to the "Personalize Your Hearing" screen
2. Close the app completely (swipe away from recent apps)
3. Reopen the app
4. Check the logcat output to see:
   - Does SplashActivity find the user?
   - What is the navigation decision?
   - Does it navigate to StartTestActivity or back to the carousel?

## Expected Results

### ✅ Success Case
- User creation logs show successful insert with valid ID
- Verification shows user count = 1 and the user details
- On app reopen, SplashActivity finds the user
- Navigation goes to StartTestActivity (not carousel)

### ❌ Failure Cases & Diagnosis

#### Case 1: User Not Found on Reopen
**Logs show:**
```
D/NameActivity: ✅ User inserted successfully! User ID: 1
D/SplashActivity: Total users found: 0
```

**Possible Causes:**
1. Database not persisting between sessions
2. Database instance mismatch
3. App data being cleared

**Next Steps:**
- Check if database file exists: `adb shell ls /data/data/com.audion.app/databases/`
- Verify database name matches: `audion_db`

#### Case 2: User Found But Wrong Navigation
**Logs show:**
```
D/SplashActivity: ✓ Found valid user: [name] (ID: 1)
D/SplashActivity: → Navigating to OnboardingCarouselActivity
```

**Possible Causes:**
1. Logic error in navigation decision
2. Flags not being set correctly

**Next Steps:**
- Review navigation logic in SplashActivity line ~90-110

#### Case 3: User Creation Fails
**Logs show:**
```
E/NameActivity: ❌ Error creating user: [error message]
```

**Possible Causes:**
1. Database constraint violation
2. Migration issue
3. Database not initialized

**Next Steps:**
- Check database migration logs
- Verify database schema

## Additional Debugging

### Check Database Directly
```bash
# Pull the database file
adb pull /data/data/com.audion.app/databases/audion_db .

# Inspect with sqlite3
sqlite3 audion_db
.tables
SELECT * FROM users;
.exit
```

### Full Logcat (if needed)
```bash
adb logcat -c  # Clear logs
# Use the app
adb logcat > full_log.txt  # Save everything
```

## Next Actions
After testing, report back with:
1. The logcat output from when you enter your name
2. The logcat output from when you reopen the app
3. Whether the navigation works as expected
4. Any error messages or unexpected behavior

This will help identify whether the issue is:
- Database write not completing
- Database read not finding the user
- Navigation logic error
- Database persistence issue
