#!/usr/bin/env python3
"""Run native UI/service checks on the disposable FocusGuard AVD only."""
import pathlib
import subprocess

SERIAL = 'emulator-5554'
PACKAGE = 'com.focusguard.app.debug'
ROOT = pathlib.Path(__file__).resolve().parents[1]

def adb(*args):
    return subprocess.check_output(['adb', '-s', SERIAL, *args], text=True, stderr=subprocess.STDOUT).strip()

assert adb('shell', 'getprop', 'ro.boot.qemu.avd_name') == 'FocusGuard', 'Use the disposable FocusGuard AVD only'
assert adb('shell', 'getprop', 'sys.boot_completed') == '1', 'Emulator is not ready'
test_apk = ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
assert test_apk.is_file(), 'Run ./gradlew :app:assembleDebugAndroidTest first'
adb('install', '-r', str(test_apk))
adb('shell', 'pm', 'clear', PACKAGE)
adb('shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services')
adb('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '0')
process = subprocess.Popen(['adb', '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r',
                            PACKAGE + '.test/com.focusguard.app.SmokeInstrumentation'],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = []
for line in process.stdout:
    lines.append(line)
    print(line, end='', flush=True)
    if 'READY_FOR_ACCESSIBILITY' in line:
        # The launcher force-stops the app; grant test access after it has restarted.
        adb('shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services')
        adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', PACKAGE + '/com.focusguard.app.AppBlockerService')
        adb('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1')
        adb('shell', 'run-as', PACKAGE, 'touch', 'files/smoke-access-ready')
    if 'REVOKE_ACCESSIBILITY' in line:
        adb('shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services')
        adb('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '0')
        adb('shell', 'run-as', PACKAGE, 'touch', 'files/smoke-access-revoked')
assert process.wait() == 0, 'Android instrumentation command failed'
result = ''.join(lines)
assert 'INSTRUMENTATION_RESULT: tests=8' in result and 'INSTRUMENTATION_RESULT: failures=0' in result, 'Native smoke checks failed'
assert 'INSTRUMENTATION_CODE: -1' in result, 'Instrumentation did not finish successfully'
print('8 native functional checks passed.', flush=True)
