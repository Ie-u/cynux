# CYNUX PRODUCTION - read-only diagnostics. Changes NOTHING on the PC or the TV.
# Run in PowerShell:  powershell -ExecutionPolicy Bypass -File .\00-diagnose.ps1
$ErrorActionPreference = "Continue"
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.100.79"
$out = Join-Path $PSScriptRoot "diagnose-report.txt"
Start-Transcript -Path $out -Force | Out-Null

Write-Host "== PC checks =="
if (Test-Path $adb) { & $adb version } else { Write-Host "adb.exe NOT found at $adb" }
Get-PSDrive C | ForEach-Object { "C: free GB = {0:N1}" -f ($_.Free/1GB) }
"Java:"; (& java -version 2>&1) | Select-Object -First 1
"ANDROID_HOME = $env:ANDROID_HOME"

Write-Host "`n== Network =="
Test-Connection $tv -Count 2 -Quiet
foreach ($p in 5555,8008,8009,5000,6466,6467,9000) {
  $r = Test-NetConnection $tv -Port $p -WarningAction SilentlyContinue
  "{0}:{1} open={2}" -f $tv,$p,$r.TcpTestSucceeded
}

Write-Host "`n== ADB devices (USB and already-paired) =="
& $adb devices -l

Write-Host "`n== Device properties (only works once a device is listed as 'device') =="
$props = "ro.product.model","ro.product.device","ro.product.board","ro.board.platform","ro.hardware",
         "ro.hardware.chipname","ro.boot.hardware","ro.soc.manufacturer","ro.soc.model","ro.build.fingerprint",
         "ro.bootimage.build.fingerprint","ro.vendor.build.fingerprint","ro.build.version.release",
         "ro.build.version.security_patch","ro.product.cpu.abi","ro.boot.verifiedbootstate",
         "ro.boot.flash.locked","ro.boot.vbmeta.device_state","ro.debuggable","ro.secure"
foreach ($k in $props) { "{0} = {1}" -f $k, (& $adb shell getprop $k 2>&1) }
"--- /proc/cpuinfo (hardware lines) ---"; & $adb shell "grep -iE 'hardware|model name|Features' /proc/cpuinfo | head -5"
"--- real RAM (kernel) ---";               & $adb shell "head -1 /proc/meminfo"
"--- storage ---";                          & $adb shell df /data
"--- GPU ---";                              & $adb shell "dumpsys SurfaceFlinger | grep -i -m3 'GLES'"
"--- installed launchers ---";              & $adb shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME"
"--- current default HOME ---";             & $adb shell "cmd shortcut get-default-launcher"
"--- bootanimation files ---";              & $adb shell "ls -l /system/media/bootanimation.zip /oem/media/bootanimation.zip /product/media/bootanimation.zip 2>&1"

Stop-Transcript | Out-Null
Write-Host "`nSaved to $out  - paste its contents back to Claude."
