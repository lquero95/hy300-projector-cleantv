# AI Assistant Prompt for HY300 Projector Optimization

Paste the prompt below into your AI coding assistant (Claude Code, Gemini CLI, Antigravity, Codex, or Cursor) on a computer connected to the same Wi-Fi network as your **HY300 Projector**.

> **Prerequisite on the HY300 Projector (Step 0):**
> Because the stock HY300 firmware hides the standard Android Developer Options menu, you must first install and open **Activity Launcher** (`ActivityLauncher.apk` included in this repo):
> 1. Open **Activity Launcher** on the projector.
> 2. Scroll through the list of system packages until you locate **`Configuration`** (`com.android.tv.set`).
> 3. Click on **`Configuration`** to expand its sub-activities and select **`Developer Configuration`** (*Developer Options*).
> 4. Inside **Developer Configuration**, toggle **ON** both:
>    - **USB debugging** (*Depuración USB*)
>    - **ADB Internet** (*Network ADB debugging* / port `5555`)
> 5. Note the projector's local Wi-Fi IP address (e.g., `192.168.0.174`).

---

## Copy & Paste Prompt

```markdown
I want to optimize, debloat, and install a custom high-contrast Apple TV Light Mode launcher with a real-time global display/color calibration overlay on my HY300 Smart Projector (`rk3326_sgo`, Rockchip RK3326, Android 12L / SDK 32, `1280x720` @ `160 dpi`).
My projector's IP address is: <YOUR_PROJECTOR_IP_HERE> (port 5555).

This project is inspired by and adapted from CleanYourTV (https://github.com/Gathin23/cleanyourtv), specifically re-engineered for the optical and hardware constraints of the HY300 projector. Work on the real device step by step and verify every visual change using screenshots pulled from the projector.

### 1. Connect & Handle the HY300 SDIO Wi-Fi TCP Bug
- Connect to `<YOUR_PROJECTOR_IP_HERE>:5555` over ADB. Tell me when the "Allow USB debugging?" prompt appears on the projector screen so I can accept it.
- **CRITICAL HARDWARE QUIRK**: The SDIO Wi-Fi chip on the HY300 (`rk3326_sgo`) resets TCP connections whenever ADB transfers burst past 24–40 KB (`adb pull`, `adb push`, and `adb exec-out screencap -p` will hang or truncate).
  - Never use standard `adb pull` or `adb push` for screenshots or APKs.
  - Use `tools/tv.py --screenshot <path>` to capture screenshots on `/data/local/tmp/__tv_shot.png` and stream them in paced 3 KB chunks (60ms interval) over a local `nc -l -p 19999` TCP socket, verifying both the PNG header (`\x89PNG\r\n\x1a\n`) and `IEND` trailer.
  - When uploading APKs, stream them in paced 8 KB chunks (25ms delay between chunks) to a background `nc -l -p 19998 > /data/local/tmp/TvHome.apk` listener on the projector, then run `pm install -r /data/local/tmp/TvHome.apk`.

### 2. Full Backup & 1-Line Restore Script
- Before modifying anything, save to `backup/`:
  - `getprop.txt` (`getprop`)
  - `packages_all.txt` (`pm list packages -f`)
  - `packages_system.txt` (`pm list packages -s`)
  - `packages_3rdparty.txt` (`pm list packages -3`)
  - `packages_disabled_initial.txt` (`pm list packages -d`)
  - `settings_global.txt`, `settings_secure.txt`, and `settings_system.txt`
- Generate `restore.sh` so that every disabled package can be re-enabled (`pm enable --user 0 <pkg>`) and the stock launcher (`com.shzhtxcz.txbox.launcher/com.shzhtxcz.txbox.SplashActivity`) can be restored in one command.

### 3. Safe HY300 Debloat & Private DNS
- Disable the HY300 manufacturer telemetry, bloatware, factory test tools, and ad-heavy stock launcher one by one using `pm disable-user --user 0` (never uninstall system packages):
  - `com.shzhtxcz.txbox` (Stock bloatware service)
  - `com.sszztxr.oott` (Manufacturer background telemetry/OTA junk)
  - `com.shzhtxcz.txcz.test` (Factory aging/test suite)
  - `com.rockchip.devicetest` (Rockchip hardware factory test)
  - `com.aispeech.tvui` (Unused Chinese voice assistant UI)
  - `com.rockchip.itvbox` (Legacy Rockchip IPTV bloat)
  - `com.sszztxr.app` (Preloaded ad/app store agent)
  - `com.tv.client.droid.nexocine` (Preloaded trial streaming bloat)
  - `com.shzhtxcz.txbox.launcher` (Stock launcher — disable ONLY after the new launcher is installed and verified as the default Home activity)
- **NEVER disable hardware-critical packages**:
  - `com.android.tv.settings` (Controls HY300 hardware Keystone correction, manual/auto focus, zoom, and projection mode!)
  - `com.android.tv.set` (Android system settings & Developer Configuration)
  - `com.rockchips.mediacenter` (USB drive & local file manager)
  - Google Play Services (`com.google.android.gms`) & Google Play Store (`com.android.vending`)
  - HDMI input services, Bluetooth, Wi-Fi, and remote control input services.
- Configure optional system-wide ad-blocking Private DNS:
  - `settings put global private_dns_specifier dns.adguard-dns.com`
  - `settings put global private_dns_mode hostname`

### 4. Apple TV Light Mode Launcher + Real-Time Projector Display Calibration
Build and install the custom launcher (`launcher/` -> `TvHome-HY300.apk`, package `com.gathin.tvhome`) specifically tuned for low-ANSI-lumen projectors:
1. **High-Contrast Apple TV Light Mode Aesthetics**:
   - Pearl-white to silver-blue gradient background (`#F1F5FB` -> `#DCE7F5` -> `#C7D7ED` in `bg_apple_light.xml`) to eliminate shadow crushing on low-lumen LCD projectors.
   - Frosted white glass Dock band (`#E6FFFFFF`) with specular border (`DockDecoration.java`) and high-visibility **Apple Blue** (`#0066CC`) focus ring (`GlassView.java`).
   - High-contrast dark slate typography (`#0F172A`) for the clock, date, active app title, and dock labels.
2. **Single-Row 6-App Dock + Centered App Library Button**:
   - Configure `config.json` with `"columns": 6`, `"rows_below_dock": 0`, `"app_library": true`, placing the 6 primary apps (including `com.android.tv.settings` labeled as **Proyección** / **Projection** for instant Keystone & Focus access) in a single clean dock row.
   - Place a centered **App Library** (`Biblioteca de aplicaciones`) pill button directly below the dock so pressing Down + OK opens a sleek modal grid with all remaining installed apps (File Manager, Play Store, Downloader, Send Files to TV, etc.), while hiding `de.szalkowski.activitylauncher.oss` from cluttering the grid.
3. **Real-Time Global Display & Color Calibration Engine (`DisplayFilter.java` + `HomeKeyService.java`)**:
   - Add an **`Imagen`** (Display) pill button in the top bar that opens a live projector calibration dialog (`showDisplayDialog()`), applying a hardware-accelerated global screen overlay (`TYPE_ACCESSIBILITY_OVERLAY` in `HomeKeyService` + local window fallback) that persists across all apps:
     - **Picture Presets**: *HY300 Light (Recommended)*, *Warm Cinema*, *Cool Vivid*, *Soft Night*, *Neutral Standard*.
     - **Illumination Intensity**: `20%` to `100%` (5% steps with D-pad Left/Right).
     - **Black Detail (Shadow Lift)**: `0%` to `100%` (lifts crushed dark grays so dark movie scenes remain visible on the HY300).
     - **Color Temperature**: `Warm (-50)` <-> `Neutral (0)` <-> `Cool (+50)`.
     - **Color Tint**: `Green (-50)` <-> `Neutral (0)` <-> `Magenta (+50)`.

### 5. Set Default Home, Enable Accessibility Services & Verify
- Set the launcher as default: `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity`
- Enable the accessibility services for remote key interception and the global display filter overlay:
  `settings put secure enabled_accessibility_services 'com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService'`
  `settings put secure accessibility_enabled 1`
- Capture and inspect verification screenshots of:
  1. The Home Screen (`shots/07_light_home.png`)
  2. The Live Display & Color Calibration Panel (`shots/08_display_controls.png`)
  3. The App Library Modal (`shots/09_app_library.png`)
```
