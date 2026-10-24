# CYNUX PRODUCTION – Findings (as of 2026-10-24)

Legend: **[VERIFIED]** I checked it. **[SUPPLIED]** you told me; not independently checked. **[HYPOTHESIS]** reasoned, needs a test on the TV.

## What I could and could not do
- [VERIFIED] No computer-control access on the current (free) plan, so I did **not** inspect `C:\platform-tools`, your ADB version, or the TV. Nothing was installed or changed on the TV or PC.
- [VERIFIED] This sandbox cannot reach Google's Android SDK servers, so the APK is **not compiled**. Source is complete; build steps are in `BUILD.md`.
- [VERIFIED] Web searches for `t950s_be30ae` and Solar Max firmware returned **no relevant results**. No manufacturer firmware, board documentation or recovery image was found. Do not assume one exists.

## Hardware: chipset is UNCONFIRMED
- [SUPPLIED] Props say Amlogic; fingerprint says MediaTek (`m7332_eu`).
- [HYPOTHESIS – strong] The identity strings are spoofed or mixed, not trustworthy:
  - Fingerprint build ID `PPR2.180905.006.A1` is an Android **9** (Pie) style ID, yet the TV claims Android 14. The build description uses `UP1A.231005.007.A1` (an Android 14 ID) with `test-keys`, while the fingerprint says `dev-keys`. Two inconsistent build identities in one device.
  - Cheap Android boxes/TVs are documented faking Android version, RAM and SoC through edited `build.prop`/Settings (CUJO AI analysis of a set-top box showing 7.1.2 / 10 / 12 as OS versions with the same build ID; AndroidPCTV's coverage of boxes advertising Android 16 that were Android 14 with a Mali-450 GPU).
  - Mali-450 MP2 + 1 GB RAM + 8 GB storage is the classic profile of an older budget TV SoC (Amlogic S905-family or Rockchip RK3518-class), not a platform that ships Android 14 in the normal way. [HYPOTHESIS] The real Android version may be much older (7–9) even though Settings shows 14.
- Consequence: **do not choose any firmware, root method, boot logo tool or flasher from these strings.** The on-device kernel data (`/proc/cpuinfo`, `/proc/meminfo`, `ro.board.platform`) is harder to fake; `scripts\00-diagnose.ps1` collects it once ADB works.

## Connectivity
- [SUPPLIED] Ping works; ports 5555/8008/8009 closed; Developer Options won't unlock.
- [HYPOTHESIS] Network ADB is simply off because Developer Options/USB debugging are hidden by this firmware. 8008/8009 (Chromecast) closed suggests it may not be a certified Android TV/Google TV build either.
- Ways to get ADB, least to most invasive:
  1. Look for Developer Options elsewhere: Settings > System/Device Preferences > About; the build-number row may be named "Version" or be on a different screen. Press OK 7+ times with the remote; some TVs need a USB-keyboard.
  2. If the TV has a USB-A port that is marked service/debug, a USB-A to USB-A cable can sometimes expose ADB. Unverified for this model.
  3. **Skip ADB entirely** (recommended; see below).

## Recommended deployment without ADB
A launcher is a normal APK. Copy `app-release.apk` to a USB stick, plug it in, open it with the TV's file manager (or "Send Files to TV" style app) and allow "Install unknown apps" for that file manager. Then press HOME and choose CYNUX PRODUCTION -> Always. Uninstalling the app restores the original home screen. Whether the TV lets you choose a default launcher is firmware-dependent [HYPOTHESIS: usually yes].

## What each customization needs
| Item | Method | Needs root/unlocked bootloader? | Status |
|---|---|---|---|
| Branded home screen, clock, network, favourites, recents, wallpapers | `CYNUX` launcher APK | No | **Source written, not compiled/tested** |
| Default launcher | Chooser on HOME press, or ADB `set-home-activity` | No | Depends on firmware |
| Startup transition into launcher | App's own window background (dark green, no title bar) | No | Done in theme |
| Android boot animation (`bootanimation.zip`) | Replace file in `/system/media` (or `/oem`, `/product`) | **Yes** – write access to system partition | **Not attempted**; may not apply if the TV's logo is drawn by the vendor/TV stack |
| Very first power-on logo | Stored in bootloader/`logo` partition, in a vendor-specific format | **Yes** – firmware-specific tool and exact-board image | **Not attempted.** Unsafe until the chipset is confirmed |
| Startup sound | Vendor/firmware-level | Yes | Unknown |
| Removing ads inside third-party apps | No supported method | n/a | Not promised |

A launcher **cannot** change the earliest boot logo or boot animation. I have not found any evidence that this TV allows it, and I will not suggest flashing anything until the real chipset is established and a matching stock image is in hand and backed up.

## Next verified step (one thing for you)
Pick one: (a) install the APK by USB stick and tell me what happens, or (b) get ADB working and run `scripts\00-diagnose.ps1`, then paste `diagnose-report.txt`. Either gives me real data about the board.
