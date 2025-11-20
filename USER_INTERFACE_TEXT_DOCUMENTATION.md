

## 📱 **ONBOARDING & REGISTRATION FLOW**

### **OnboardingCarouselActivity**
**Slide 1:**
- Title: "Welcome to Audion"
- Description: "Audion helps you hear what matters, comfortably and with confidence."

**Slide 2:**
- Title: "Stay Focused on What Matters"
- Description: "Audion helps you focus on the right voice, so speech stays clear and close."

**Slide 3:**
- Title: "Hear. Connect. Belong."
- Description: "Audion helps you follow every word, so it's easier to share the moments that matter."

**Button:**
- "Get Started"

---

### **WhatsYourNameActivity**
**Screen Title:**
- "What's your name?"

**Input Field:**
- Placeholder: "Your name"
- Hint: "Enter your name"

**Button:**
- "Continue"

**Legal Text:**
- "By tapping 'Continue', you agree to our Terms of Service and Privacy Policy."

**Toast Messages:**
- "Please enter your name"

---

### **StartTestActivity**
**Title:**
- "Set Up Your Hearing Profile"

**Description:**
- "Skip appointments. Audion lets you check your hearing with a simple test on your phone."

**Buttons:**
- "Get Started"
- "Continue Later" (link)

---

## 🎯 **HEARING TEST FLOW**

### **GeneralInstructionActivity**
**Title:**
- "Before You Start"

**Instructions:**
- "Find a quiet place"
- "Put on your earbuds"
- "Tap when you hear a tone"
- "Start with your right ear"

**Button:**
- "Begin Test"

---

### **RightEarInstructionActivity**
**Title:**
- "Right Ear Test"

**Description:**
- "Listen carefully and tap the circle whenever you hear a tone."

**Instructions:**
- "1. Put on your earbuds"
- "2. Tap the circle when you hear a tone"
- "3. Stay focused - test takes about 2-3 minutes"

**Button:**
- "Start Right Ear Test"

**Toast Messages:**
- "Permissions granted! Starting hearing test..."
- "Audio permissions are required for hearing tests. Please grant permissions and try again."
- "⚠️ Complete calibration first"

---

### **LeftEarInstructionActivity**
**Title:**
- "Left Ear Test"

**Description:**
- "Listen carefully and tap the circle whenever you hear a tone."

**Instructions:**
- "1. Keep your earbuds on"
- "2. Tap the circle when you hear a tone"
- "3. Test takes about 2-3 minutes"

**Button:**
- "Start Left Ear Test"

**Toast Messages:**
- "Permissions granted! Starting hearing test..."
- "Audio permissions are required for hearing tests. Please grant permissions and try again."
- "⚠️ Complete calibration first"

---

**Screen Title:**
- "[Left/Right] Ear Test"

**Description:**
- "Listen carefully and tap the circle whenever you hear a tone."

**Button Labels:**
- "Tap here" (main response button)
- "Didn't Hear" / "Didn't hear"
- "Start\nagain"

**Status Messages:**
- "🎵 Listen carefully and tap when you hear the tone..."
- "🎵 Listening..."
- "✓ Tap Registered!"
- "✓ Threshold Recorded: [X] dB"
- "⏳ Processing..."
- "⚠️ Reached maximum level (120 dB)"

**Progress Indicator:**
- "Testing [X] of [Y] frequencies"
- Progress bar visualization

**Toast Messages:**
- "Missing USER_ID or HEARING_PROFILE_ID"
- "Error during test: [error message]"

---

## 🎚️ **CALIBRATION FLOW**

### **CalibrationInstructionActivity**
**Title (Right Ear):**
- "Right Ear Calibration"

**Title (Left Ear):**
- "Left Ear Calibration"

**Description:**
- "We'll play three tones to fine-tune your right ear."
- "We'll play three tones to fine-tune your left ear."

**Instructions:**
- "1. Put on your earbuds"
- "2. Listen to each tone"
- "3. Tell us how it sounds"

**Button:**
- "Begin Calibration"

**Toast Messages:**
- "❌ Pure Tone Test required first! Redirecting..."
- "Unable to verify pure tone test - redirecting"

---

### **CalibrationTestActivity**
**Screen Title:**
- "Calibrating [LEFT/RIGHT] ear - Frequency [X] of [Y]"

**Phase Labels:**
- "Finding comfortable level for [X] Hz"
- "Finding uncomfortable level for [X] Hz"

**Response Buttons:**
- "Too Soft"
- "Comfortable"
- "Too Loud"

**Frequency Display:**
- "[X] Hz" (e.g., "1000 Hz")

**Level Display:**
- "[X] dB SPL" (e.g., "70 dB SPL")

**Toast Messages:**
- "Maximum level reached for [X] Hz. You may have significant hearing loss at this pitch."
- "High tolerance detected for [X]Hz"
- "MCL unusually low ([X] dB). Please verify."
- "MCL unusually high ([X] dB). Please verify."
- "UCL unusually low ([X] dB). Please verify."
- "UCL unusually high ([X] dB). Please verify."
- "Small hearing range ([X] dB) for [X] Hz. Please review this result."
- "Extreme sensitivity detected for [X]Hz"
- "Error saving calibration data"

**Progress:**
- "Testing frequency [X] of [Y]"
- Visual frequency bars

---

### **BaselineCalibrationActivity**
**Note:** Legacy activity - not currently used in main flow but remains in codebase.

---

### **EarResultActivity**
**Note:** Legacy activity - not currently used in main flow but remains in codebase.

---

## ✅ **TEST COMPLETION FLOW**

### **TestCompletionActivity**
**Visual:**
- Animated success checkmark/celebration
- No text (visual only)

---

### **TestCompletionSplashActivity**
**Note:** Legacy activity - not currently used in main flow but remains in codebase.

---

### **TestResultsActivity**
**Screen Title:**
- "[User Name] Your Test Results" (if user name available)
- "Your Test Results" (if no user name)

**User Display:**
- "[User Name]" or "Unknown User"

**Date Display:**
- Date formatted as "MMMM d, yyyy HH:mm" (e.g., "November 19, 2025 14:30")

**Results Sections:**
- "Right Ear Results"
- "Left Ear Results"
- Frequency-specific threshold data

**Button:**
- "Proceed to Home"

**Toast Messages:**
- "Profile created successfully!" (when FROM_NEW_PROFILE flag is true)
- "⚠️ No test data found for this profile"
- "Error loading test results: [error message]"

---

## 🏠 **HOME & MAIN NAVIGATION**

### **HomeActivity**
**Navigation Tabs:**
- "Normal"
- "Focus"

**Audio Source Toggle:**
- Microphone icon/label
- Phone audio icon/label

**Status Messages:**
- "Noise Cancellation ON"
- "Noise Cancellation OFF"

**Toggle Button:**
- Play icon (▶️)
- Stop icon (⏸️)

**Amplification Section:**
- "Amplification" label
- "[X] dB" display (0-40 dB range)

**Amplification Warning Dialogs:**

**Dialog 1 (20-30 dB):**
- Title: "Stronger Amplification"
- Message: "Amplification above 20 dB is quite strong. Make sure it still feels comfortable for you. Continue?"
- Buttons: "Yes, Continue" / "Cancel"

**Dialog 2 (30-40 dB):**
- Title: "Very Strong Amplification"
- Message: "Amplification above 30 dB is very strong. Only continue if it still feels safe and comfortable for you. Continue?"
- Buttons: "Yes, Continue" / "Cancel"

**MediaProjection Permission Dialog:**
- Title: "Allow Phone Audio Access"
- Message: "To amplify music, videos, and calls, Audion needs permission to capture your phone's audio.\n\nAndroid will show a 'Screen recording' popup – this is expected.\n\nAudion only uses the sound, not your screen, and your privacy stays protected."
- Buttons: "I Understand - Allow" / "Cancel"

**Toast Messages:**
- "Phone audio mode requires Android 10 or higher"
- "Listening to environment"
- "Amplifying phone media"
- "Phone audio mode requires permission"

**Personalization Status Banner:**
- "✅ Personalized for your hearing"
- "Audiogram & calibration active"
- "⚠️ Hearing profile not complete"
- "Run a hearing test to personalize Audion for you"
- Action Button: "RE-TEST" or "START TEST"

---

### **FrequencyActivity**
**Ear Selection Chips:**
- "Left"
- "Right"

**Frequency Sliders:**
- "125 Hz"
- "250 Hz"
- "500 Hz"
- "1000 Hz"
- "2000 Hz"
- "4000 Hz"
- "8000 Hz"

**Amplification Display:**
- "[X] dB" per frequency

**Bottom Navigation:**
- Home icon
- Frequencies icon
- Settings icon

**Unsaved Changes Dialog:**
- Title: "Unsaved Changes"
- Message: "You have unsaved changes. What would you like to do with them?"
- Buttons: "Save" / "Discard" / "Cancel"

**Toast Messages:**
- "Changes discarded"
- "Saved successfully"
- "Error saving: [error message]"

---

### **ProfileActivity**
**Screen Title:**
- "[User Name]'s hearing profile"

**Sections:**
- "Pure Tone Audiogram"
- "Left Ear" (X markers)
- "Right Ear" (O markers)

**Frequency Labels:**
- "125" / "250" / "500" / "1k" / "2k" / "4k" / "8k" (Hz)

**Threshold Labels:**
- "0" / "20" / "40" / "60" / "80" / "100" (dB HL)

**Messages:**
- "No hearing profile found. Please create a profile to see your results."
- "Last test: [date]"
- "Last test: Recently completed"
- "No test data available. Complete a hearing test to view your audiogram."
- "Error loading test data"

**Button:**
- Navigation to home/frequencies

---

### **HearingProfileActivity**
**Toolbar Title:**
- "Your hearing profile"

**Action Buttons:**
- "START HEARING TEST" (when no data exists)
- "RETEST HEARING" (when data exists)
- "RECALIBRATE" button

**Audiogram Section:**
- "Pure Tone Audiogram"
- "No hearing test data available yet"

**Ear-specific Summaries:**
- "Left Ear: [X] dB HL avg ([status])"
- "Right Ear: [X] dB HL avg ([status])"
- "Left Ear: No data"
- "Right Ear: No data"

**Hearing Loss Classification:**
- "Normal" (0-25 dB HL)
- "Mild Loss" (26-40 dB HL)
- "Moderate Loss" (41-55 dB HL)
- "Severe Loss" (56-70 dB HL)
- "Profound Loss" (71+ dB HL)

**Calibration Section:**
- "Calibration levels"
- "No calibration data available"
- "MCL: [X] dB" (Most Comfortable Level)
- "UCL: [X] dB" (Uncomfortable Level)
- "Last updated: [date]"
- "Test date unavailable"

**Toast Messages:**
- "Error loading hearing profile"

---

### **SettingsActivity**
**Button:**
- "Start Calibration"

**QA Toggle Toast (Long Press):**
- "🔧 NEW DSP Pipeline ENABLED\n\nRestart audio processing to take effect\n\n[Debug: PreGain=6dB, WDRC, Limiter, Dither]"
- "🔧 LEGACY Pipeline ENABLED\n\nRestart audio processing to take effect\n\n[Debug: 4-band, tanh() clipping]"

**Bottom Navigation:**
- Home / Frequencies / Settings tabs

---

### **GraphActivity**
**Ear Selection Tabs:**
- "LEFT"
- "RIGHT"

**Graph Labels:**
- Frequency axis labels (Hz)
- Threshold axis labels (dB HL)

**Bottom Navigation:**
- Home / Frequencies / Settings

---

## 🎵 **MUSIC & FOCUS MODE**

### **MusicPlayerActivity**
**Permission Dialog:**
- Title: "Permission needed"
- Message: "We need audio access to list your music files."
- Buttons: "OK" / "Cancel"

**Music List:**
- "Song Title"
- "Artist Name"
- Duration display

**Toast Messages:**
- "Permission denied; cannot list music."

**Bottom Navigation:**
- Home / Frequencies / Settings

---

### **FocusActivity**
**Navigation Tabs:**
- "Normal"
- "Focus"

**Scanning Status Messages (Rotating):**
- "Warming up your ears…"
- "Catching every whisper…"
- "Spotlighting the speakers…"
- "Prepping your focus lens…"

**Speaker List:**
- "Found [X] speakers"
- "Speaker [ID]"
- "[Duration]s total • [X] chunks"

**Buttons:**
- "Enroll" button
- "Scan Again" button

**Status Messages:**
- "Please select a speaker"
- "Let's see who is speaking"
- "Getting the microphone ready…"

**Amplification Warnings:**
(Same as HomeActivity - moderate/high amplification dialogs)

**Toast Messages:**
- "Diarization manager not available"
- "No audio for Speaker [X]"
- "Playing Speaker [X] (Chunk [Y], [Z]s)"
- "Error: [error message]"
- Various speaker enrollment/playback status messages

---

## 🔔 **NOTIFICATIONS & SERVICES**

### **AudioStreamingService**
**Notification:**
- Title: "Audio Streaming"
- Text: "Running…"

**Channel:**
- Name: "audio_streaming_channel"

---

### **MusicStreamingService**
**Notification:**
- Title: "Music Amplifier"
- Text: "Amplified Music is playing"

**Channel:**
- Name: "Music Streaming"
- Description: "Channel for streaming amplified music"

---

## 📋 **HELP & SUPPORT**

### **HelpActivity**
**Screen Title:**
- "Help & Support"

**Quick Guides Section:**
- "Quick Guides"

**Guide Items:**
1. "Play Button" / "How to start/stop audio streaming"
2. "Amplification" / "Adjust the amplification level"
3. "Noise Cancellation" / "Toggle noise removal on/off"

**FAQs Section:**
- "FAQs"
- "How do I adjust volume?" / "Use the amplification slider to change how loud things sound."
- "How to enable captions?" / "Tap the 'Open Captions' button."

---

## 🎨 **UI COMPONENTS & MODALS**

### **NewProfileBottomSheet**
**Title:**
- "Create New Profile"

**Input:**
- "Profile Name"

**Button:**
- "Next"

**Toast:**
- "Please enter a profile name"

---

### **EarbudsRequiredBottomSheet**
**Title:**
- "Earbuds Required"

**Message:**
- "For accurate hearing test results, please connect earbuds or headphones."

**Buttons:**
- "I have earbuds connected"
- "Cancel"

---

## 🔧 **PROGRESS & STATUS INDICATORS**

### **Stepper Progress**
**Steps:**
1. "1" / "Instructions"
2. "2" / "Right Ear"
3. "3" / "Left Ear"
4. "4" / "Pure Tone"

---

### **Loading States**
- "Loading..."
- "Processing..."
- "Please wait..."
- Spinning progress indicators

---

### **Error Messages (General)**
- "Error: [specific error message]"
- "Something went wrong. Please try again."
- "Network error. Please check your connection."
- "Unable to load data"
- "Operation failed"

---

## 📊 **DATA DISPLAY FORMATS**

### **Date/Time Formats**
- "MMMM d, yyyy HH:mm" (e.g., "November 19, 2025 14:30")
- "MM/dd/yyyy" (e.g., "11/19/2025")
- "Recently completed"
- "Not available"

### **Audio Levels**
- "[X] dB" (decibels)
- "[X] dB HL" (Hearing Level)
- "[X] dB SPL" (Sound Pressure Level)
- "[X] Hz" (Hertz/frequency)
- "[X]s" (seconds)

### **Percentages**
- "[X]%" (progress indicators)

---

## 🎯 **BUTTON & ACTION LABELS**

### **Primary Actions**
- "Continue"
- "Next"
- "Get Started"
- "Let's get started"
- "Begin Test"
- "Begin Calibration"
- "Start Test"
- "Start Right Ear Test"
- "Start Left Ear Test"
- "Proceed to Home"

### **Secondary Actions**
- "Continue Later"
- "Test Later"
- "Skip"
- "Cancel"
- "Try Again"
- "Scan Again"

### **Destructive Actions**
- "Discard"
- "Delete"
- "Remove"

### **Confirmation Actions**
- "Yes, Continue"
- "I Understand - Allow"
- "OK"
- "Confirm"

---

## ⚠️ **WARNINGS & ALERTS**

### **Permission Warnings**
- "Audio permissions are required for hearing tests. Please grant permissions and try again."
- "Phone audio mode requires permission"

### **Calibration Warnings**
- "⚠️ Complete calibration first"
- "❌ Pure Tone Test required first! Redirecting..."

### **Audio Level Warnings**
- "⚠️ Reached maximum level (120 dB)"
- "Maximum level reached for [X] Hz. You may have significant hearing loss at this pitch."
- "High tolerance detected for [X]Hz"

### **Data Warnings**
- "⚠️ No test data found for this profile"
- "⚠️ Incomplete hearing profile"

---

## ✅ **SUCCESS MESSAGES**

- "✓ Tap Registered!"
- "✓ Threshold Recorded: [X] dB"
- "✅ Personalized for your hearing"
- "Profile created successfully!"
- "Calibration completed successfully!"
- "Saved successfully"
- "Permissions granted! Starting hearing test..."

---

## 🎵 **AUDIO FEEDBACK MESSAGES**

- "🎵 Listen carefully and tap when you hear the tone..."
- "🎵 Listening..."
- "Listening to environment"
- "Amplifying phone media"

---

## 📱 **SYSTEM REQUIREMENTS**

- "Phone audio mode requires Android 10 or higher"
- Various Android version-specific messages

---

## 🔐 **LEGAL & PRIVACY**

- "By tapping 'Continue', you agree to our Terms of Service and Privacy Policy."
- "Your privacy is protected - no screen recording occurs."
- "Audion ONLY captures audio, never your screen."

---

## 🌐 **ACCESSIBILITY DESCRIPTIONS**

- "Onboarding illustration"
- "Hearing test illustration"
- Various content descriptions for screen readers

---

**Document Version:** 2.0 (Current Flow Only)  
**Last Updated:** November 19, 2025  
**Total Active Text Entries:** 250+ unique user-facing strings  
**Note:** Legacy screens removed - this document reflects only the current active user flow

---

## 📝 **NOTES FOR LOCALIZATION**

When translating this app:
1. Maintain tone consistency (friendly, supportive, clinical when needed)
2. Preserve technical terms (dB, Hz, SPL, HL)
3. Keep button labels concise (max 2-3 words)
4. Ensure medical/hearing terminology is accurate
5. Test UI layout with longer translations (especially German, Russian)
6. Maintain emoji usage for visual clarity
7. Preserve line breaks in multi-line messages
8. Keep date/time formats region-appropriate

---

## 🎨 **TONE & VOICE GUIDELINES**

**General Tone:**
- Warm and supportive
- Clear and straightforward
- Professional but approachable

**Medical/Technical Sections:**
- Precise and clinical
- Use proper audiological terminology
- Provide context for technical terms

**Onboarding/Welcome:**
- Encouraging and positive
- Focus on benefits and empowerment
- Avoid medical jargon

**Errors/Warnings:**
- Direct and actionable
- Explain what happened and how to fix
- Use caution symbols appropriately

---

**End of Documentation**
