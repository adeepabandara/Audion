# Test script to enable/disable processing modes via ADB

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Audion Audio Processing Test Script" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "Available commands:" -ForegroundColor Yellow
Write-Host "  1. Enable BYPASS mode (passthrough only)" -ForegroundColor Green
Write-Host "  2. Disable BYPASS mode (full 4-band processing)" -ForegroundColor Green  
Write-Host "  3. Enable noise reduction" -ForegroundColor Green
Write-Host "  4. Disable noise reduction" -ForegroundColor Green
Write-Host "  5. Set amplification to 0.5x" -ForegroundColor Green
Write-Host "  6. Set amplification to 1.0x (default)" -ForegroundColor Green
Write-Host "  7. Set amplification to 1.5x" -ForegroundColor Green
Write-Host "  8. Monitor logs" -ForegroundColor Green
Write-Host "  9. Clear all preferences (reset)" -ForegroundColor Red
Write-Host "  Q. Quit" -ForegroundColor Gray
Write-Host ""

$choice = Read-Host "Enter choice"

switch ($choice) {
    "1" {
        Write-Host "Enabling BYPASS mode..." -ForegroundColor Yellow
        adb -s R58M244R2WL shell "run-as com.example.audion sh -c 'cat > /data/data/com.example.audion/shared_prefs/com.example.audion.PREFERENCES.xml << EOF
<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <boolean name=\"bypassMode\" value=\"true\" />
    <float name=\"amplificationFactor\" value=\"1.0\" />
    <boolean name=\"noiseRemoval\" value=\"true\" />
</map>
EOF'"
        Write-Host "✓ BYPASS mode enabled. Restart audio streaming in app." -ForegroundColor Green
    }
    
    "2" {
        Write-Host "Disabling BYPASS mode (full processing)..." -ForegroundColor Yellow
        adb -s R58M244R2WL shell "run-as com.example.audion sh -c 'cat > /data/data/com.example.audion/shared_prefs/com.example.audion.PREFERENCES.xml << EOF
<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <boolean name=\"bypassMode\" value=\"false\" />
    <float name=\"amplificationFactor\" value=\"1.0\" />
    <boolean name=\"noiseRemoval\" value=\"true\" />
</map>
EOF'"
        Write-Host "✓ Full processing enabled. Restart audio streaming in app." -ForegroundColor Green
    }
    
    "3" {
        Write-Host "Enabling noise reduction..." -ForegroundColor Yellow
        adb -s R58M244R2WL shell "am broadcast -a com.example.audion.UPDATE_PREFS --es key noiseRemoval --ez value true"
        Write-Host "✓ Noise reduction enabled" -ForegroundColor Green
    }
    
    "4" {
        Write-Host "Disabling noise reduction..." -ForegroundColor Yellow
        adb -s R58M244R2WL shell "am broadcast -a com.example.audion.UPDATE_PREFS --es key noiseRemoval --ez value false"
        Write-Host "✓ Noise reduction disabled" -ForegroundColor Green
    }
    
    "5" {
        Write-Host "Setting amplification to 0.5x..." -ForegroundColor Yellow
        adb -s R58M244R2WL shell "am broadcast -a com.example.audion.UPDATE_PREFS --es key amplificationFactor --ef value 0.5"
        Write-Host "✓ Amplification set to 0.5x" -ForegroundColor Green
    }
    
    "6" {
        Write-Host "Setting amplification to 1.0x..." -ForegroundColor Yellow
        adb -s R58M244R2WL shell "am broadcast -a com.example.audion.UPDATE_PREFS --es key amplificationFactor --ef value 1.0"
        Write-Host "✓ Amplification set to 1.0x" -ForegroundColor Green
    }
    
    "7" {
        Write-Host "Setting amplification to 1.5x..." -ForegroundColor Yellow
        adb -s R58M244R2WL shell "am broadcast -a com.example.audion.UPDATE_PREFS --es key amplificationFactor --ef value 1.5"
        Write-Host "✓ Amplification set to 1.5x" -ForegroundColor Green
    }
    
    "8" {
        Write-Host "Monitoring logs (Ctrl+C to stop)..." -ForegroundColor Yellow
        Write-Host "Look for [BYPASS] or Frame logs..." -ForegroundColor Gray
        adb -s R58M244R2WL logcat -v threadtime AudioProcessor:I AudioStreamingService:I *:S
    }
    
    "9" {
        Write-Host "Clearing all preferences..." -ForegroundColor Red
        adb -s R58M244R2WL shell "run-as com.example.audion rm -f /data/data/com.example.audion/shared_prefs/com.example.audion.PREFERENCES.xml"
        Write-Host "✓ Preferences reset" -ForegroundColor Green
    }
    
    default {
        Write-Host "Exiting..." -ForegroundColor Gray
    }
}
