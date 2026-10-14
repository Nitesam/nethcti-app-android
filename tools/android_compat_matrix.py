#!/usr/bin/env python3
"""Sequential, disposable-emulator compatibility checks. Never targets physical devices.
Run after assembling debug + androidTest APKs. Images must already be installed.
Detailed reports are local-only; --output defaults to docs/validation-1.6.5.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_SDK_ROOT', Path.home() / 'Android/Sdk'))
ADB = str(SDK / 'platform-tools/adb')
SERIAL = 'emulator-5580'
PKG = 'it.nethesis.nethctiapp.debug'
RUNNER = PKG + '.test/org.linphone.LocalCallInstrumentation'
ENV = dict(os.environ, JAVA_HOME=str(Path.home() / '.local/opt/android-studio/jbr'))


def run(args, timeout=60, check=True):
    return subprocess.run([str(a) for a in args], cwd=ROOT, env=ENV, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, check=check).stdout


def adb(*args, **kwargs):
    return run([ADB, '-s', SERIAL, *args], **kwargs)


def wait_boot(api):
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        try:
            booted = adb('shell', 'getprop', 'sys.boot_completed', timeout=10, check=False).strip() == '1'
        except subprocess.TimeoutExpired:
            booted = False
        if booted:
            assert adb('shell', 'getprop', 'ro.hardware').strip() == 'ranchu'
            assert int(adb('shell', 'getprop', 'ro.build.version.sdk').strip()) == api
            return
        time.sleep(2)
    raise RuntimeError('Emulator boot timeout')


def test(api, label, args, output, peer=None):
    print(f'API {api}: {label}', flush=True)
    adb('shell', 'am', 'force-stop', PKG)
    proc = None
    with (output / (label + '-peer.txt')).open('w') as peer_log:
        try:
            if peer:
                proc = subprocess.Popen(['python', *peer], cwd=ROOT, stdout=peer_log, stderr=subprocess.STDOUT)
                time.sleep(0.5)
                if proc.poll() is not None: raise RuntimeError('Peer failed to start')
            text = adb('shell', 'am', 'instrument', '-w', '-r', *args, RUNNER, timeout=240)
            (output / (label + '.txt')).write_text(text)
            if label == 'setup':
                with (output / 'setup.png').open('wb') as image:
                    subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'run-as', PKG, 'cat', 'files/setup.png'], stdout=image, check=False)
            if 'INSTRUMENTATION_CODE: -1' not in text:
                raise RuntimeError(f'{label} failed: {text[-1800:]}')
        finally:
            if proc and proc.poll() is None:
                proc.terminate()
                proc.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apis', default='28,29,30,31,32,33,34,35,36,37')
    parser.add_argument('--audio-only', action='store_true', help='Focused call audio, route, boost UI and measured RTP tone regression')
    parser.add_argument('--attach', action='store_true', help='Use existing dedicated emulator for the first API')
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/validation-1.6.5')
    options = parser.parse_args()
    options.output.mkdir(parents=True, exist_ok=True)
    results = []
    for index, api in enumerate(map(int, options.apis.split(','))):
        output = options.output / f'api-{api}'
        output.mkdir(exist_ok=True)
        emulator = None
        try:
            if not (options.attach and index == 0):
                devices = run([ADB, 'devices'])
                if any(line.startswith('emulator-') for line in devices.splitlines()):
                    raise RuntimeError('An emulator is already running; refusing to start a second one')
                avd = f'NethCTI_Matrix_{api}' if api != 37 else 'NethCTI_API37_QPR_16KB'
                if not (Path.home() / '.android/avd' / (avd + '.ini')).exists():
                    image = f'system-images;android-{api};google_apis;x86_64' if api != 37 else 'system-images;android-37.2;google_apis_ps16k;x86_64'
                    creation = subprocess.run([str(SDK / 'cmdline-tools/latest/bin/avdmanager'), 'create', 'avd', '--name', avd,
                                    '--package', image, '--device', 'pixel_2'],
                                    input='no\n', text=True, env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
                    (output / 'avd.txt').write_text(creation.stdout)
                    creation.check_returncode()
                with (output / 'emulator.txt').open('w') as log:
                    emulator = subprocess.Popen([str(SDK / 'emulator/emulator'), '-avd', avd, '-port', '5580', '-no-window',
                            '-no-audio', '-no-snapshot', '-gpu', 'swangle', '-cores', '2', '-memory', '2048'], stdout=log, stderr=subprocess.STDOUT)
            print(f'API {api}: waiting for boot', flush=True)
            wait_boot(api)
            adb('shell', 'input', 'keyevent', '82')
            for apk in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
                assert 'Success' in adb('install', '-r', str(ROOT / apk))
            # Dedicated test fixture only, no corporate account. Clear channels to reproduce pre-consent creation.
            assert 'Success' in adb('shell', 'pm', 'clear', PKG)
            adb('shell', 'cmd', 'notification', 'disallow_dnd', PKG)
            adb('shell', 'pm', 'grant', PKG, 'android.permission.RECORD_AUDIO')
            if api >= 33: adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
            if api >= 37: adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_LOCAL_NETWORK')
            if options.audio_only:
                from audio_signal_check import verify
                test(api, 'audio', ['-e', 'audio', 'true', '-e', 'incoming', 'true',
                     '-e', 'page_size', '16384' if api == 37 else '4096'], output,
                     ['tools/local_sip_peer.py', '--incoming', '--tone'])
                for name in ['audio-boost.wav', 'audio-boost.png']:
                    with (output / name).open('wb') as artifact:
                        subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'run-as', PKG, 'cat', 'files/' + name], stdout=artifact, check=True)
                test(api, 'audio-recovery', ['-e', 'audio_recovery', 'true'], output)
                signal = verify(output / 'audio-boost.wav')
                (output / 'signal.json').write_text(json.dumps(signal, indent=2))
                results.append({'api': api, 'result': 'PASS', 'tests': ['audio', 'incoming', 'outgoing', 'background_rtp'], 'signal': signal})
            else:
                test(api, 'dnd', ['-e', 'dnd_grant', 'true'], output)
                test(api, 'setup', ['-e', 'setup', 'true'], output)
                with (output / 'setup.png').open('wb') as image:
                    subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'run-as', PKG, 'cat', 'files/setup.png'], stdout=image, check=True)
                test(api, 'rtp', ['-e', 'incoming', 'true', '-e', 'page_size', '16384' if api == 37 else '4096'],
                     output, ['tools/local_sip_peer.py', '--incoming'])
                test(api, 'reception', ['-e', 'reception', 'true'], output, ['tools/reception_sip_peer.py'])
                results.append({'api': api, 'result': 'PASS', 'tests': ['dnd', 'setup', 'rtp', 'reception']})
            print(f'API {api}: PASS', flush=True)
        except Exception as error:
            results.append({'api': api, 'result': 'FAIL', 'error': str(error)})
            (output / 'crash.txt').write_text(adb('logcat', '-d', '-b', 'crash', check=False))
            raise
        finally:
            (options.output / 'matrix.json').write_text(json.dumps(results, indent=2))
            adb('emu', 'kill', check=False)
            if emulator:
                try: emulator.wait(timeout=20)
                except subprocess.TimeoutExpired: emulator.terminate()
            time.sleep(2)


if __name__ == '__main__':
    main()
