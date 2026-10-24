#!/usr/bin/env sh
# restore.sh - Re-enables all packages disabled during the HY300 projector debloat and restores the stock launcher
# Usage: TV_HOST=192.168.0.174 sh restore.sh

TV_HOST="${TV_HOST:-192.168.0.174}"
TV_PORT="${TV_PORT:-5555}"
ADB="adb -s ${TV_HOST}:${TV_PORT}"

echo "Connecting to ${TV_HOST}:${TV_PORT}..."
adb connect "${TV_HOST}:${TV_PORT}"

PACKAGES="
com.shzhtxcz.txbox
com.sszztxr.oott
com.shzhtxcz.txcz.test
com.rockchip.devicetest
com.aispeech.tvui
com.rockchip.itvbox
com.sszztxr.app
com.tv.client.droid.nexocine
com.shzhtxcz.txbox.launcher
"

for pkg in $PACKAGES; do
    echo "Re-enabling $pkg..."
    $ADB shell "pm enable --user 0 $pkg"
done

echo "Restoring stock launcher as default Home activity..."
$ADB shell "cmd package set-home-activity --user 0 com.shzhtxcz.txbox.launcher/com.shzhtxcz.txbox.SplashActivity"

echo "Restore completed."
