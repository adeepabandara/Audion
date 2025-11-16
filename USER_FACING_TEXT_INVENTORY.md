# Audion App - User-Facing Text Inventory

**Document Date:** November 16, 2025  
**Purpose:** Complete inventory of all words, sentences, and text displayed to users in the Audion application

---

## Table of Contents
1. [Onboarding & Welcome](#onboarding--welcome)
2. [Hearing Tests](#hearing-tests)
3. [Calibration](#calibration)
4. [Home Screen & Audio Controls](#home-screen--audio-controls)
5. [Focus Mode](#focus-mode)
6. [Profile Management](#profile-management)
7. [Settings & About](#settings--about)
8. [System Messages & Toasts](#system-messages--toasts)
9. [Buttons & Actions](#buttons--actions)
10. [Error Messages](#error-messages)

---

## Onboarding & Welcome

### Main Onboarding Screen
- **Title Line 1:** "We believe in"
- **Title Line 2 (Bold):** "Natural"
- **Title Line 3 (Bold):** "Technology"
- **Description:** "Experience the perfect harmony of natural sound processing and cutting-edge technology. Our app enhances your hearing experience with personalized audio amplification that adapts to your unique needs, creating a seamless bridge between you and the world around you."
- **Button:** "Let's get started"

### Name Entry Screen
- **Question:** "What's your name?"
- **Input Hint:** "Your name" / "Enter your name"
- **Button:** "Continue"
- **Legal Text:** "By tapping 'Continue', you agree to our Terms of Service and Privacy Policy"

### Hearing Test Introduction
- **Title:** "Personalize Your Hearing"
- **Description:** "No appointments, no trouble. Audion helps you get your pure-tone audiogram right from your phone."
- **Button:** "Get Started"
- **Skip Link:** "Continue Later"

---

## Hearing Tests

### Right Ear Instruction
- **Title:** "Right Ear – Pure Tone Test"
- **Instructions:** "Place your headphones on and tap 'Start Test' when you're ready.\n\nYou'll hear tones at different volumes – tap when you can hear them."
- **Button:** "Start Test"

### Left Ear Instruction
- **Title:** "Left Ear – Pure Tone Test"
- **Instructions:** "Place your headphones on and tap 'Start Test' when you're ready.\n\nYou'll hear tones at different volumes – tap when you can hear them."
- **Button:** "Start Test"

### Pure Tone Test (During Test)
- **Title:** "Left Ear Test" / "Right Ear Test"
- **Description:** "Listen carefully. Tap the circle when you hear the tone."
- **Circle Button:** "Tap here" / "Start again" / "Start\nAgain"
- **Button:** "Didn't Hear"
- **Steps:** "1", "2", "3", "4", "5", "6", "7", "8" (for 8 frequencies)

### Test Results
- **Title:** "Your Test Results"
- **No Data Message:** "No test data available for [left/right] ear"
- **Calibration Message:** "Calibration data not available for this ear."

### Ear Result Screen
- **Ear Label:** "Right Ear" / "Left Ear"
- **Result Chip:** Shows completion status
- **Button (After Right):** "Next"
- **Continue Text (After Right):** "Tap Next to continue to Left ear"
- **Button (After Left):** "Continue"
- **Continue Text (After Left):** "Tap Continue to proceed to Pure Tone Test"
- **Button (Retry):** "Try Again"

---

## Calibration

### Calibration Instruction
- **Title (Left):** "Left Ear Calibration"
- **Description (Left):** "We'll play 3 standard levels for your left ear calibration"
- **Title (Right):** "Right Ear Calibration"
- **Description (Right):** "We'll play 3 standard levels for your right ear calibration"
- **Button:** "Begin" / "Start"

### Baseline Calibration
- **Ear Label:** "Right Ear" / "Left Ear"
- **Instruction:** "You will hear the tone in..."
- **Volume Label:** "Volume: 50%"
- **Instructions:**
  - "• Every time you hear the tone tap 👍"
  - "• If the tone is not audible, tap 👎"
- **Button (Hear):** "👍"
- **Button (Not Hear):** "👎"
- **Button (Next):** "Next"
- **Countdown:** "3", "2", "1"
- **Countdown Message:** "Get ready..."

### Calibration Test
- **Step Label:** "Calibrating [LEFT/RIGHT] ear - Frequency [X] of [Y]"
- **Frequency Instructions:**
  - "Finding uncomfortable level for [frequency] Hz"
  - "Finding comfortable level for [frequency] Hz"
- **Buttons:** "Too Soft", "Comfortable", "Too Loud"
- **Instruction:** "Adjust until volume feels comfortable"
- **Button:** "Save Volume"

### Calibration Messages
- **Success:** "✅ [LEFT/RIGHT] ear calibration saved!"
- **Progress:** "✓ Comfort level saved for [frequency label] ([frequency] Hz)"
- **Warnings:**
  - "Maximum level reached for [frequency]Hz - possible hearing loss"
  - "High tolerance detected for [frequency]Hz"
  - "Small dynamic range ([X] dB) for [frequency]Hz"
  - "Extreme sensitivity detected for [frequency]Hz"
  - "Invalid level: [X] dB SPL"
- **Error:** "Error saving calibration data"
- **Requirement:** "Please complete the calibration test"
- **Pre-requisite:** "❌ Pure Tone Test required first! Redirecting..."

---

## Home Screen & Audio Controls

### Main Title
- **Line 1:** "Ready to"
- **Line 2 (Bold):** "Amplify"
- **Line 3 (Bold):** "Your World?"
- **Description:** "Tap the button to start amplifying audio. Adjust volume and settings to personalize your experience."

### Tabs
- **Tab 1:** "Normal"
- **Tab 2:** "Focus"

### Personalization Status Banner
- **Title (Incomplete):** "Hearing Personalization Incomplete"
- **Subtitle (Incomplete):** "Run hearing test for personalized audio"
- **Title (Complete):** Shows completion status
- **Subtitle (Complete):** "Personalized to your hearing profile"
- **Button:** "START TEST"

### Audio Control Buttons
- **Main Toggle:** "Start" / "Stop"

### Mode Indicators
- **Environment Mode:** "Listening to environment"
- **Phone Media Mode:** "Amplifying phone media"

---

## Focus Mode

### Focus Mode UI
- **Tab:** "Focus"
- **Description:** "Isolate and amplify the voice you want to hear.\nScan your environment to detect speakers."
- **Button:** "Scan Environment" / "Stop Scan" / "Scan Again"
- **Status Messages:**
  - "Calibrating microphone…"
  - "Starting diarization…"
  - "Processing audio… [X]%"
  - "Loading Speaker Playbacks… [X]%"

### Noise Cancellation
- **Label:** "Noise Reduction"
- **Status ON:** "Noise Cancellation ON"
- **Status OFF:** "Noise Cancellation OFF"

### Speaker Selection
- **Default Message:** "Please select a speaker"
- **Chip Label:** "Speaker [number]"
- **Chunk Label:** "Chunk [number]"

### Scan Messages (Rotating)
Various motivational messages displayed during scanning:
- Messages rotate to keep user engaged during the scan process

---

## Profile Management

### Hearing Profile Screen
- **Title:** "Hearing Profile"
- **Button:** "START HEARING TEST" (if no test completed)
- **Button:** "RETEST HEARING" (if test exists)
- **Last Test Date:** "Last updated: [date]" / "Test date unavailable"
- **Left Ear Summary:** "Left Ear: No data" / Shows data
- **Right Ear Summary:** "Right Ear: No data" / Shows data
- **No Data:** "No audiogram data available"
- **Calibration:** "No calibration data available"

### Profile Activity
- **Test Date (No Profile):** "No hearing profile found. Please create a profile first."
- **Test Date (With Test):** "Last test: [date]" / "Last test: Recently completed"
- **Test Date (No Data):** "No test data available. Complete a hearing test to see your audiogram."
- **Test Date (Error):** "Error loading test data"

### New Profile Sheet
- **Title:** "Add profile +"
- **Input Hint:** "Profile Name"
- **Buttons:** Create/Save options

---

## Settings & About

### Frequency Adjustment
- **Title:** "Select Hearing Profile"
- **Tab Labels:** "LEFT EAR", "RIGHT EAR"
- **Frequency Labels:** "125 Hz", "250 Hz", "500 Hz", "1000 Hz", "2000 Hz", "4000 Hz", "6000 Hz", "8000 Hz"

### Unsaved Changes Dialog
- **Title:** "Unsaved Changes"
- **Message:** "You have unsaved changes to your frequency settings. What would you like to do?"
- **Button 1:** "Save Changes"
- **Button 2:** "Discard Changes"

---

## System Messages & Toasts

### Success Messages
- "Saved successfully"
- "Changes discarded"
- "Profile created successfully!"
- "Permissions granted! Starting hearing test..."
- "Listening to environment"
- "Amplifying phone media"
- "✓ Comfort level saved for [frequency]"
- "✅ [ear] ear calibration saved!"

### Error Messages
- "Error loading data"
- "Error saving: [error details]"
- "⚠️ No test data found for this profile"
- "Error loading test results: [error details]"
- "Error loading hearing profile"
- "Permission required for phone audio mode"
- "Microphone permission required"
- "Audio permissions are required for hearing tests. Please grant permissions and try again."
- "Phone audio mode requires Android 10 or higher"
- "Phone audio mode requires permission"

### Warning Messages
- "⚠️ Complete calibration first"
- "Please enter your name"
- "Please enter a name"
- "Select a speaker first"
- "No audio sample"
- "No speakers to reset"

---

## Buttons & Actions

### Common Buttons
- "Start"
- "Stop"
- "Next"
- "Continue"
- "Try Again"
- "Save"
- "Discard"
- "Begin"
- "Start Test"
- "Start Again"
- "Repeat"
- "Record"
- "Stop Recording"
- "Save Volume"
- "Scan Environment"
- "Stop Scan"
- "Scan Again"
- "START TEST"
- "RETEST HEARING"
- "START HEARING TEST"
- "Let's get started"
- "Get Started"
- "Continue Later"

### Navigation
- "›" (right arrow indicator)
- Tab indicators for navigation

---

## Error Messages

### Audio Errors
- "Error: [error message]"
- "Diarization error: [error message]"
- "Failed to initialize diarization"
- "Failed to initialize AudioRecord"
- "No speakers detected. Try recording again."
- "No speakers with enough speech detected. Try recording again."
- "All speakers have been removed"
- "All enrolled speakers have been removed"
- "Diarization manager not available"
- "No audio available for this speaker"
- "Error playing audio: [error message]"
- "Play error: [error message]"

### Test Errors
- "Missing USER_ID or HEARING_PROFILE_ID"
- "Error during test: [error message]"
- "Threshold at maximum level (120 dB)"
- "Maximum volume reached"
- "Error retrieving the created user"

### Permission Errors
- "Audio permission is required for captions"
- "Permission denied; cannot list music."
- "Recording permission granted"
- "Recording permission denied"

### Speech Recognition
- "Model initialization failed: [error message]"
- "Listening for speech..."
- "SpeechService failed: [error message]"
- "Recognition error: [error message]"
- "No speech detected."

---

## Earbuds Detection

### Bottom Sheet
- **Title:** "Earbuds Required"
- **Description:** "Please connect your wired earbuds or Bluetooth headphones to continue.\n\nThis ensures accurate audio testing and the best experience."
- **Status (Checking):** "Checking for audio devices..."
- **Status (Connected):** "Earbuds connected! ✓"
- **Status (Not Connected):** "Waiting for earbuds..."

---

## Enrollment & Speaker Management

### Enrollment Activity
- **Button:** "Record" / "Stop Recording"
- **Status Messages:**
  - "Recording... Speak naturally for [X] seconds"
  - "Processing audio..."
  - "No audio recorded"
  - "Running speaker diarization..."
  - "Found [X] new speakers. You can rename them by tapping on their names."
  - "All speakers have been removed"

### Speaker Adapter
- **Speaker ID:** "Speaker [number]"
- **Chunk Header:** "Chunk [number]"
- **Dialog Title:** "Rename Speaker"

---

## Caption Activity

### Real-time Captions
- **Status:** "Listening for speech..."
- **Error States:**
  - "Model initialization failed: [error]"
  - "SpeechService failed: [error]"
  - "Recognition error: [error]"
  - "No speech detected."

---

## Music Player

### Music Controls
- **Permission Required:** "Permission denied; cannot list music."
- **Placeholder:** Shows album art with music note icon

---

## Status & Progress Indicators

### Loading States
- "Processing audio… [percentage]%"
- "Loading Speaker Playbacks… [percentage]%"
- "Calibrating microphone…"
- "Starting diarization…"
- "Running speaker diarization..."

### Countdown
- "3"
- "2"
- "1"
- "Get ready..."

---

## Navigation Messages

### Navigation Errors
- "Navigation Error"
- "Unable to proceed. Would you like to try again or return to main menu?"
- "Unable to verify pure tone test - redirecting"

---

## Test Status Messages

### Frequency Display
- Shows current frequency being tested (e.g., "125 Hz", "250 Hz", etc.)
- Step labels showing progress (e.g., "1 of 8", "2 of 8", etc.)

### Volume Indicators
- "Volume: [percentage]%"
- Shows current dB level during calibration

---

## Additional UI Elements

### Icons & Symbols
- 👍 (thumbs up - hear tone)
- 👎 (thumbs down - don't hear tone)
- ✓ (checkmark - success)
- ✅ (checkbox - completion)
- ⚠️ (warning)
- ❌ (error/block)
- › (navigation arrow)
- 🎵 (music note)

### Placeholders
- "Success message" (toast placeholder)
- "Error message" (toast placeholder)
- ic_music_placeholder (image placeholder)

---

## Notes for Localization

### Context-Aware Text
- Ear indicators: "LEFT" / "RIGHT" or "Left" / "Right" depending on context
- Frequency values: Always shown with "Hz" unit
- dB values: Always shown with "dB" or "dB SPL" unit
- Percentage values: Always shown with "%" symbol
- Dates: Formatted according to system locale

### Dynamic Text
- Speaker numbers are generated dynamically (e.g., "Speaker 1", "Speaker 2")
- Chunk numbers are generated dynamically (e.g., "Chunk 1", "Chunk 2")
- Progress percentages are calculated in real-time
- Error messages include dynamic error details from system

### Formatting Notes
- Use \n for line breaks in multi-line text
- Emojis are used for visual communication (👍, 👎, ✓, ✅, ⚠️, ❌)
- Ellipsis (…) used to indicate ongoing processes
- Capitalization varies by context (ALL CAPS for primary CTAs, Title Case for labels)

---

## App Name & Branding

- **App Name:** "Audion"
- **App Description:** "Experience the perfect harmony of natural sound processing and cutting-edge technology."

---

**Total Word Count:** Approximately 2,500+ unique words and phrases  
**Total Sentences:** Approximately 150+ complete sentences  
**UI Screens Covered:** 25+ different screens and dialogs  
**Message Types:** Instructions, labels, buttons, errors, success messages, warnings, status updates

---

*This document should be used for localization, UI/UX review, consistency checking, and content strategy planning.*
