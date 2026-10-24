#!/usr/bin/env python3
"""HY300 Projector (RK3326 / Android 12) ADB & Paced Screenshot Helper.

The SDIO Wi-Fi chip on this projector resets TCP connections when sending bursts >24-40 KB.
`take_screenshot()` captures `/data/local/tmp/__tv_shot.png` on the device and streams it
in paced 4 KB chunks (50ms interval) over a local TCP socket (`nc -l -p 19999`), then verifies
both the PNG signature and the `IEND` trailer before writing to disk.
"""
import argparse
import os
import socket
import subprocess
import sys
import time

DEFAULT_HOST = os.environ.get("TV_HOST", "192.168.0.174")
DEFAULT_PORT = os.environ.get("TV_PORT", "5555")
DEVICE = f"{DEFAULT_HOST}:{DEFAULT_PORT}"
NC_PORT = 19999
PNG_HEADER = b"\x89PNG\r\n\x1a\n"
PNG_TRAILER = b"IEND\xaeB`\x82"

KEY_MAP = {
    "DPAD_UP": "19",
    "DPAD_DOWN": "20",
    "DPAD_LEFT": "21",
    "DPAD_RIGHT": "22",
    "DPAD_CENTER": "23",
    "ENTER": "66",
    "BACK": "4",
    "HOME": "3",
    "MENU": "82",
    "SETTINGS": "176",
    "VOLUME_UP": "24",
    "VOLUME_DOWN": "25",
}


def run_adb(args, retries=4):
    cmd = ["adb", "-s", DEVICE] + args
    for _ in range(retries):
        res = subprocess.run(cmd, capture_output=True)
        out = res.stdout.decode("utf-8", errors="replace")
        err = res.stderr.decode("utf-8", errors="replace")
        if res.returncode == 0 and "device offline" not in err and "not found" not in err:
            return res.returncode, out, err
        subprocess.run(["adb", "disconnect", DEVICE], capture_output=True)
        subprocess.run(["adb", "connect", DEVICE], capture_output=True)
        time.sleep(0.4)
    return res.returncode, out, err


def ensure_connected():
    subprocess.run(["adb", "connect", DEVICE], capture_output=True)
    subprocess.run(["adb", "-s", DEVICE, "wait-for-device"], capture_output=True, timeout=10)


def take_screenshot(out_path):
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    remote = "/data/local/tmp/__tv_shot.png"
    for attempt in range(3):
        ensure_connected()
        _, size_str, _ = run_adb([
            "shell",
            f"rm -f {remote}; screencap -p {remote}; stat -c %s {remote}"
        ])
        try:
            expected_size = int(size_str.strip().splitlines()[-1])
        except (ValueError, IndexError):
            expected_size = 0
        if expected_size <= 0:
            time.sleep(0.5)
            continue

        port = NC_PORT + attempt
        chunks = (expected_size + 3071) // 3072 + 1
        nc_cmd = (
            f"killall nc 2>/dev/null; "
            f"nohup sh -c '(i=0; while [ $i -lt {chunks} ]; do "
            f"dd if={remote} bs=3072 skip=$i count=1 2>/dev/null; "
            f"i=$((i+1)); sleep 0.06; done; sleep 0.5) | nc -l -p {port}' >/dev/null 2>&1 &"
        )
        run_adb(["shell", nc_cmd])
        time.sleep(0.5)

        buf = bytearray()
        try:
            with socket.create_connection((DEFAULT_HOST, port), timeout=12.0) as sock:
                sock.settimeout(12.0)
                while len(buf) < expected_size:
                    chunk = sock.recv(8192)
                    if not chunk:
                        break
                    buf.extend(chunk)
        except OSError as e:
            print(f"Attempt {attempt + 1} socket error: {e}", file=sys.stderr)
            time.sleep(0.5)
            continue

        data = bytes(buf)
        if len(data) == expected_size and data.startswith(PNG_HEADER) and data.endswith(PNG_TRAILER):
            with open(out_path, "wb") as f:
                f.write(data)
            run_adb(["shell", f"rm -f {remote}"])
            print(f"Saved valid PNG to {out_path} ({len(data)} bytes)")
            return True
        print(
            f"Attempt {attempt + 1} incomplete PNG ({len(data)}/{expected_size} bytes), retrying...",
            file=sys.stderr,
        )
        time.sleep(0.5)

    print(f"Failed to capture valid screenshot to {out_path}", file=sys.stderr)
    return False


def main():
    parser = argparse.ArgumentParser(description="HY300 Projector ADB Helper")
    parser.add_argument("--screenshot", "-s", help="Path to save screenshot PNG")
    parser.add_argument("keys", nargs="*", help="Optional key events to send before screenshot (e.g. DPAD_DOWN)")
    args = parser.parse_args()

    ensure_connected()

    for k in args.keys:
        keycode = KEY_MAP.get(k.upper(), k)
        run_adb(["shell", f"input keyevent {keycode}"])
        time.sleep(0.7)

    if args.screenshot:
        if not take_screenshot(args.screenshot):
            sys.exit(1)


if __name__ == "__main__":
    main()
