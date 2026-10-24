# Removes the CYNUX launcher; the TV falls back to its original home screen.
$adb = "C:\platform-tools\adb.exe"
$log = Join-Path $PSScriptRoot "change-log.txt"
& $adb shell "cmd package clear-home-activity" 2>&1 | Out-Null    # may be unsupported; harmless
$r = & $adb uninstall tv.cynux.launcher 2>&1
"{0}  uninstall: {1}" -f (Get-Date -Format s), $r | Tee-Object -FilePath $log -Append
& $adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME" | Out-Null
Write-Host "If the TV still shows a chooser, pick the original launcher. No firmware was changed, so nothing else needs undoing."
