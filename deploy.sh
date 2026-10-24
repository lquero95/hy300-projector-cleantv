#!/usr/bin/env sh
# deploy.sh - Deploys the Apple TV Light Mode Launcher (com.gathin.tvhome), config.json, and safe debloat on the HY300 Projector
# Usage: TV_HOST=192.168.0.174 sh deploy.sh

set -e

TV_HOST="${TV_HOST:-192.168.0.174}"
TV_PORT="${TV_PORT:-5555}"
ADB="adb -s ${TV_HOST}:${TV_PORT}"
APK_PATH="${APK_PATH:-./TvHome-HY300.apk}"
CONFIG_PATH="${CONFIG_PATH:-./config.json}"

echo "=== 1. Connecting to HY300 Projector at ${TV_HOST}:${TV_PORT} ==="
adb connect "${TV_HOST}:${TV_PORT}"

echo "=== 2. Uploading and installing Apple TV Light Mode Launcher (${APK_PATH}) ==="
# Paced TCP transfer via netcat to avoid overflowing the HY300 SDIO Wi-Fi TCP buffer
$ADB shell "killall nc 2>/dev/null; rm -f /data/local/tmp/TvHome.apk; nohup sh -c 'nc -l -p 19998 > /data/local/tmp/TvHome.apk' >/dev/null 2>&1 &"
sleep 1
python3 - "$TV_HOST" 19998 "$APK_PATH" <<'EOF'
import socket, sys, time
host, port, path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
with open(path, "rb") as f:
    data = f.read()
with socket.create_connection((host, port), timeout=10.0) as s:
    for i in range(0, len(data), 8192):
        s.sendall(data[i:i+8192])
        time.sleep(0.025)
    time.sleep(0.3)
EOF
$ADB shell "pm install -r /data/local/tmp/TvHome.apk && rm -f /data/local/tmp/TvHome.apk"

echo "=== 3. Granting permissions and applying config.json (6-slot Dock + App Library + Projection Settings) ==="
$ADB shell "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION" || true
$ADB shell "am start -n com.gathin.tvhome/.MainActivity"
sleep 1
B64_CFG=$(base64 -w 0 "$CONFIG_PATH" 2>/dev/null || base64 "$CONFIG_PATH" | tr -d '\r\n')
$ADB shell "echo '$B64_CFG' | base64 -d > /data/local/tmp/config.json && mkdir -p /sdcard/Android/data/com.gathin.tvhome/files && cp /data/local/tmp/config.json /sdcard/Android/data/com.gathin.tvhome/files/config.json && content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json && date +%s > /data/local/tmp/rev && content write --uri content://com.gathin.tvhome.theme/theme_revision < /data/local/tmp/rev && rm -f /data/local/tmp/config.json /data/local/tmp/rev"

echo "=== 4. Setting com.gathin.tvhome as default Home Launcher ==="
$ADB shell "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity"
$ADB shell "am force-stop com.gathin.tvhome && am start -n com.gathin.tvhome/.MainActivity"

echo "=== 5. Enabling accessibility services for remote control keys and global display filter ==="
$ADB shell "settings put secure enabled_accessibility_services 'com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService'; settings put secure accessibility_enabled 1"

echo "=== Deployment completed successfully ==="
