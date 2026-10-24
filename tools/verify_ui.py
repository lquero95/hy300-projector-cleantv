#!/usr/bin/env python3
"""Applies config.json to the HY300 projector and captures verification screenshots."""
import base64
import json
import os
import subprocess
import sys
import time

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TV_PY = os.path.join(BASE_DIR, "tools", "tv.py")
SHOTS_DIR = os.path.join(BASE_DIR, "shots")
CFG_PATH = os.path.join(BASE_DIR, "config.json")

TV_HOST = os.environ.get("TV_HOST", "192.168.0.174")
TV_PORT = os.environ.get("TV_PORT", "5555")
DEVICE = f"{TV_HOST}:{TV_PORT}"

with open(CFG_PATH, "rb") as f:
    raw = f.read()

b64 = base64.b64encode(raw).decode("ascii")

subprocess.run(["adb", "connect", DEVICE], check=False)
cmd = (
    f"echo '{b64}' | base64 -d > /sdcard/Android/data/com.gathin.tvhome/files/config.json && "
    "echo -n 'hy302' > /sdcard/Android/data/com.gathin.tvhome/files/theme_revision && "
    "chmod 666 /sdcard/Android/data/com.gathin.tvhome/files/config.json /sdcard/Android/data/com.gathin.tvhome/files/theme_revision && "
    "am force-stop com.gathin.tvhome && input keyevent 3"
)
subprocess.run(["adb", "-s", DEVICE, "shell", cmd], check=True)
time.sleep(3.5)

# 1. Capture Home Screen
subprocess.run([sys.executable, TV_PY, "--screenshot", os.path.join(SHOTS_DIR, "07_light_home.png")], check=True)

# 2. Open App Library
subprocess.run(["adb", "connect", DEVICE], check=False)
subprocess.run(["adb", "-s", DEVICE, "shell", "input keyevent 20 && sleep 0.4 && input keyevent 66"], check=True)
time.sleep(1.5)
subprocess.run([sys.executable, TV_PY, "--screenshot", os.path.join(SHOTS_DIR, "09_app_library.png")], check=True)

# Return to clean Home state
subprocess.run(["adb", "connect", DEVICE], check=False)
subprocess.run(["adb", "-s", DEVICE, "shell", "input keyevent 4"], check=False)
print("Done!")
