# Getting ADB (only if you want scripted install)

Needs Developer Options -> USB debugging (and "ADB over network" if present) enabled on the TV. If those menus are missing, use the USB-stick install in FINDINGS.md instead.

```
cd C:\platform-tools
adb connect 192.168.100.79:5555
adb devices          # must show "device" (accept the prompt on the TV if "unauthorized")
```
Connection refused = the TV isn't listening on 5555, i.e. network debugging is off. Ping succeeding doesn't change that.

Then: `scripts\00-diagnose.ps1`, then `scripts\01-install-launcher.ps1`. Undo with `scripts\99-rollback.ps1`. The scripts never root, unlock, flash or reset anything.
