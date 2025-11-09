# Force app to use SAFE mode (bypass most DSP, keep only feedback canceller + limiter)
Write-Host "Setting audio to SAFE mode (minimal DSP)..." -ForegroundColor Cyan

# Stop app
adb -s R58M244R2WL shell "am force-stop com.example.audion"
Start-Sleep -Seconds 1

# Set to SAFE mode via intent broadcast
adb -s R58M244R2WL shell "am broadcast -a com.example.audion.SET_MODE --es mode SAFE"

Write-Host "`n✓ SAFE mode activated" -ForegroundColor Green
Write-Host "  - Feedback canceller: ON" -ForegroundColor White
Write-Host "  - Limiter: ON (safety)" -ForegroundColor White
Write-Host "  - All other DSP: OFF" -ForegroundColor Yellow
Write-Host "`nNow start the app and test audio quality." -ForegroundColor Cyan
