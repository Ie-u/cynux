# Installs the CYNUX launcher over ADB and records a backup of what it touched.
# Prerequisite: `adb devices` shows the TV as "device". Does NOT root, unlock or flash anything.
param(
  [string]$Apk = "$PSScriptRoot\..\app\build\outputs\apk\release\app-release.apk",
  [string]$Tv  = "192.168.100.79:5555"
)
$adb = "C:\platform-tools\adb.exe"
$log = Join-Path $PSScriptRoot "change-log.txt"
function Log($m){ "{0}  {1}" -f (Get-Date -Format s), $m | Tee-Object -FilePath $log -Append }

if (-not (Test-Path $Apk)) { throw "APK not found: $Apk (build it first, see docs/BUILD.md)" }
$state = (& $adb devices) -join "`n"
if ($state -notmatch "\bdevice\b") { throw "No authorised ADB device. Connect first (docs/CONNECT.md)." }

# 1. Record the current default launcher so rollback is possible.
$prev = (& $adb shell "cmd shortcut get-default-launcher") -join ""
Log "Previous default launcher: $prev"
& $adb shell "cmd package list packages -3" | Out-File "$PSScriptRoot\backup-user-packages.txt"
& $adb shell "settings list global; settings list secure; settings list system" | Out-File "$PSScriptRoot\backup-settings.txt"
Log "Saved package list and settings snapshot."

# 2. Install (reversible: adb uninstall tv.cynux.launcher)
$r = & $adb install -r $Apk 2>&1
Log "adb install: $r"
if ($r -notmatch "Success") { throw "Install failed: $r" }

# 3. Verify it is registered as a HOME activity
$home = (& $adb shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME") -join "`n"
if ($home -match "tv.cynux.launcher") { Log "VERIFIED: registered as HOME app." } else { Log "WARNING: not listed as HOME app." }

# 4. Try to make it default (works on many TVs; others show a chooser on first HOME press)
& $adb shell "cmd package set-home-activity tv.cynux.launcher/.MainActivity" 2>&1 | ForEach-Object { Log "set-home-activity: $_" }
& $adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME" | Out-Null
Log "Done. Press HOME on the remote; if asked, choose CYNUX PRODUCTION -> Always."
Log "Rollback: scripts\99-rollback.ps1"
