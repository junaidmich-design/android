# FocusGuard

An Android app that blocks user-selected distracting apps. Users need a monthly Google Play subscription to enable blocking. The app detects foreground packages with a consented accessibility service and displays a blocking screen. It does not read window content or upload app-usage data.

## Development

- Android Studio, JDK 17 or newer, Android SDK platform 36 and build tools 36.0.0.
- Pinned Gradle 8.13 / Android Gradle Plugin 8.13.2, Java source, Android 8.0+ (API 26).
- Existing cloud checkout: `/workspace/android`; do not create another checkout or worktree.

In this cloud environment:

```bash
source /workspace/.cloud-android/env.sh
cd /workspace/android
./gradlew --no-daemon --max-workers=3 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On other machines, set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK, or let Android Studio configure them. The wrapper verifies the Gradle distribution checksum. Build outputs are under `app/build/outputs/apk/`.

## Try app blocking

1. Install `app/build/outputs/apk/debug/app-debug.apk` on a device or emulator.
2. On first launch, read the required-access disclosure and tap **I agree · Open settings**. Enable FocusGuard’s accessibility service in Android Settings, then return to the app. Access is checked again automatically. If you choose **Not now**, blocking stays disabled; tap **Enable accessibility** to retry.
3. Tap **Choose apps**, select an installed app, and save.
4. In a debug build, tap **Enable demo access**. This is explicitly free development access, not a purchase. Release builds omit this control and cannot enable demo entitlement.
5. Turn on **Block selected apps**, then open a selected app. The FocusGuard blocking screen should appear.
6. Use **Back to my day** to return home, or **Manage blocked apps** to change the list or turn blocking off.

Settings, the current home launcher, permission controllers, Google Play, and FocusGuard itself cannot be selected. Blocking is voluntary and can be disabled in Android Settings. This is not tamper-proof parental control. OEM accessibility/background behavior can vary; verify on your target devices.

Accessibility is the only special access required. Internet and Google Play billing permissions are granted at installation. Android requires the user to approve accessibility access; the app cannot grant it automatically. The blocking switch stays disabled until accessibility, app selection, and subscription access are ready. Removing accessibility access pauses blocking; a fresh launch requests setup again.

## Configure real monthly payments

The Google Play Billing 8 client is integrated, but **payments are not configured or verified in this cloud environment**. No price is hard-coded; the app displays the localized price supplied by Play.

Before publishing:

1. Choose your permanent application ID (currently `com.focusguard.app`) and register the app in Google Play Console. Debug builds use `.debug` and are for local demo testing.
2. Create and activate a subscription with product ID `focusguard_monthly`, and a non-offer monthly auto-renewing base plan (`P1M`). Set your price and countries in Play Console.
3. Configure release signing outside the repository. Never commit signing keys or credentials. Build an Android App Bundle (`./gradlew :app:bundleRelease`) and upload it to an internal test track.
4. Add license testers and test purchase, cancel, pending purchase, restore, renewal, and expiry from a Play-installed release build. The client only unlocks purchased subscriptions; unacknowledged purchases unlock after successful acknowledgement. Pending payments do not unlock blocking.
5. **Add a backend entitlement service before commercial release.** The current implementation trusts Play client purchase responses and caches access for up to 24 hours. It is a prototype, not server-verified subscription enforcement. Production should verify tokens using the Google Play Developer API, bind entitlements to user accounts, handle renewal/refund/revocation notifications, and refresh from trusted server expiry times. Google credentials belong on the server, never inside the Android app.
6. Complete Play's accessibility API declaration, consent/disclosure review, Data Safety, subscription disclosures, and a public privacy policy matching the final app and backend. Policy approval is not established by a successful build.

When the cached subscription check expires, blocking pauses until FocusGuard is opened and purchases are restored. No app-usage or purchase token is logged by this app. Google Play processes payments under its own terms.

## Tests

`BlockPolicyTest` covers selected-app enforcement, subscription/toggle gating, protected apps, unselected/null packages, and cached-entitlement expiry/invalid timestamps. Build and lint check Android integration. Unit tests do not prove actual Play purchases or accessibility behavior on physical devices.

The cloud environment also has a disposable Android 15 AOSP emulator named `FocusGuard`. It uses software acceleration and may take several minutes to boot:

```bash
source /workspace/.cloud-android/env.sh
bash /workspace/.cloud-android/start-emulator.sh
adb -s emulator-5554 shell getprop sys.boot_completed  # wait for 1
adb -s emulator-5554 shell wm size 540x1170
adb -s emulator-5554 shell wm density 220
./gradlew --no-daemon --max-workers=3 :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/emulator-smoke.py
```

The eight smoke checks reset the debug app on that specific disposable AVD and cover the startup permission prompt and refusal, returning from Settings, subscription gating, actual selected-app blocking, the Home action, disabling blocking, revoking demo access, and removing accessibility access. The runner prepares the stock Contacts sample's permissions, grants and revokes FocusGuard's accessibility for testing, and refuses to run on a different AVD. FocusGuard itself does not request contacts permissions. It avoids the external UI hierarchy reader, which is unreliable in this software-rendered environment. On a real device, grant accessibility access through the consent screen rather than these test commands.
