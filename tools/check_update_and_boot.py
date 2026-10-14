#!/usr/bin/env python3
"""Preserve-data upgrade and boot checks on a dedicated emulator, never on a phone.
Use after the API matrix, with an already booted dedicated emulator at port 5580.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
from android_compat_matrix import ROOT, PKG, SERIAL, ADB, adb, test, wait_boot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', type=int, required=True)
    parser.add_argument('--old-apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/validation-1.6.5/upgrade-boot')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    wait_boot(args.api)
    assert adb('shell', 'getprop', 'ro.hardware').strip() == 'ranchu'
    # Disposable emulator only: clear before creating synthetic migration data.
    adb('shell', 'pm', 'clear', PKG)
    assert 'Success' in adb('install', '-r', '-d', str(args.old_apk))
    assert 'Success' in adb('install', '-r', str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    adb('shell', 'pm', 'grant', PKG, 'android.permission.RECORD_AUDIO')
    if args.api >= 33: adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
    if args.api >= 37: adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_LOCAL_NETWORK')
    adb('shell', 'cmd', 'notification', 'allow_dnd', PKG)
    adb('shell', 'dumpsys', 'deviceidle', 'whitelist', '+' + PKG)
    test(args.api, 'upgrade-seed-164', ['-e', 'upgrade', 'seed'], args.output)
    assert 'Success' in adb('install', '-r', str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk'))
    test(args.api, 'upgrade-verify-165', ['-e', 'upgrade', 'verify'], args.output)
    # Seed ON again to exercise the actual boot receiver with no corporate account.
    test(args.api, 'boot-seed', ['-e', 'upgrade', 'seed'], args.output)
    adb('reboot')
    time.sleep(5)
    wait_boot(args.api)
    adb('shell', 'input', 'keyevent', '82')
    deadline = time.monotonic() + 45
    passed = False
    while time.monotonic() < deadline:
        services = adb('shell', 'dumpsys', 'activity', 'services', PKG)
        if args.api >= 35:
            notifications = adb('shell', 'dumpsys', 'notification')
            passed = any(PKG in line and 'id=4 ' in line for line in notifications.splitlines())
            if passed:
                assert 'isForeground=true' not in services, 'Boot unexpectedly started the phone service'
                detail = 'PASS: boot posts resume notification without starting restricted foreground service'
        else:
            passed = 'isForeground=true' in services
            detail = 'PASS: boot resumes foreground service on supported Android version'
        if passed: break
        time.sleep(2)
    assert passed, 'Boot behavior not observed'
    (args.output / 'boot.txt').write_text(detail + '\n')
    print(detail, flush=True)
    adb('shell', 'am', 'force-stop', PKG)


if __name__ == '__main__':
    main()
