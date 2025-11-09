# Monitor audio engine startup
Write-Host "Monitoring Audion audio startup..." -ForegroundColor Cyan
Write-Host "Press START button in the app now!" -ForegroundColor Yellow
Write-Host ""

adb -s R58M244R2WL logcat -c
adb -s R58M244R2WL logcat | Select-String -Pattern "AudioEngine|DspGraph|RAW_PASSTHROUGH|AudioStreamingService"
