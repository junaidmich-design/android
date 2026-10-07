package com.focusguard.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.inspector.WindowInspector;
import android.widget.ListView;
import android.widget.TextView;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.io.File;

/** Real UI/service checks for the disposable Android 15 FocusGuard emulator. */
@TargetApi(29)
public final class SmokeInstrumentation extends Instrumentation {
    private MainActivity main;
    private AppState state;
    private int passed;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            state = new AppState(getTargetContext());
            require(!state.entitled() && !state.enabled() && state.selected().isEmpty(), "Start with cleared debug app data");
            main = openMain();
            require(hasText("Set up required access"), "Required access was not requested at startup");
            require(!blockingEnabled(), "Blocking was available before permission setup");
            click("Not now");
            require(!accessibilityReady() && !state.enabled(), "Declining setup granted access or enabled blocking");
            pass("Startup requests access and declining keeps blocking disabled");
            click("Enable accessibility");
            click("I agree · Open settings");
            File permissionReady = new File(getTargetContext().getFilesDir(), "smoke-access-ready");
            if (permissionReady.exists()) require(permissionReady.delete(), "Could not reset test readiness marker");
            Bundle setup = new Bundle(); setup.putString("stream", "READY_FOR_ACCESSIBILITY\n"); sendStatus(0, setup);
            long deadline = SystemClock.elapsedRealtime() + 45_000;
            while ((!permissionReady.exists() || !accessibilityReady()) && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(200);
            }
            require(permissionReady.exists() && accessibilityReady(), "Accessibility service did not bind after test setup");
            getTargetContext().startActivity(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            waitForIdleSync();
            deadline = SystemClock.elapsedRealtime() + 15_000;
            while (!hasText("Access enabled. Screen content is never read.") && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200);
            require(hasText("Access enabled. Screen content is never read."), "Access status did not refresh after returning from Settings");
            require(!hasText("Set up required access"), "Permission prompt remained after access was granted");
            pass("Returning from Settings refreshes permission setup");
            String targetPackage = null;
            Intent targetIntent = null;
            for (String candidate : new String[]{"com.android.browser", "com.android.contacts", "com.android.messaging"}) {
                Intent candidateIntent = getTargetContext().getPackageManager().getLaunchIntentForPackage(candidate);
                if (candidateIntent != null) { targetPackage = candidate; targetIntent = candidateIntent; break; }
            }
            require(targetPackage != null, "No selectable sample app is installed");
            final String selectedPackage = targetPackage;
            final String label = getTargetContext().getPackageManager().resolveActivity(targetIntent, 0)
                    .loadLabel(getTargetContext().getPackageManager()).toString();
            click("Choose apps");
            final int[] selectionIndex = {-1};
            runOnMainSync(() -> {
                ListView list = findList();
                require(list != null, "App selection dialog was not shown");
                int index = -1;
                for (int i=0; i<list.getAdapter().getCount(); i++) {
                    if (label.equals(list.getAdapter().getItem(i).toString())) { index=i; break; }
                }
                require(index >= 0, "Sample app is missing from the picker");
                selectionIndex[0] = index;
                list.setSelection(index);
            });
            final int[] location = {-1, -1};
            for (int attempt=0; attempt<50 && location[0]<0; attempt++) {
                runOnMainSync(() -> {
                    ListView list = findList();
                    View row = list.getChildAt(selectionIndex[0] - list.getFirstVisiblePosition());
                    if (row != null && row.getHeight() > 0) {
                        row.getLocationOnScreen(location);
                        location[0] += row.getWidth()/2;
                        location[1] += row.getHeight()/2;
                    }
                });
                if (location[0]<0) SystemClock.sleep(200);
            }
            require(location[0]>=0, "Selected app row did not become visible");
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, location[0], location[1], 0);
            MotionEvent up = MotionEvent.obtain(now, now+100, MotionEvent.ACTION_UP, location[0], location[1], 0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            sendPointerSync(down); sendPointerSync(up); down.recycle(); up.recycle();
            waitForIdleSync();
            click("Save selection");
            require(state.selected().contains(selectedPackage), "Selection did not persist: " + state.selected() + ", expected " + selectedPackage);
            click("Block selected apps");
            require(!state.enabled(), "Unpaid user was able to enable blocking");
            pass("Subscription access is required");

            click("Enable demo access");
            require(state.entitled(), "Demo access did not enable");
            click("Block selected apps");
            require(state.enabled(), "Blocking did not enable; check accessibility permission");
            ActivityMonitor blockedMonitor = addMonitor(BlockedActivity.class.getName(), null, false);
            getTargetContext().startActivity(targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity blocked = blockedMonitor.waitForActivityWithTimeout(45_000);
            removeMonitor(blockedMonitor);
            require(blocked instanceof BlockedActivity, "Selected app was not blocked by the accessibility service");
            require(selectedPackage.equals(blocked.getIntent().getStringExtra("package")), "Wrong app was blocked");
            pass("Selected app opens the blocking screen");

            click("Back to my day");
            require(blocked.isFinishing(), "Home action did not dismiss the blocking screen");
            pass("Home action exits the blocking screen");

            main = openMain();
            click("Block selected apps");
            require(!state.enabled(), "Blocking switch did not turn off");
            ActivityMonitor unexpected = addMonitor(BlockedActivity.class.getName(), null, false);
            getTargetContext().startActivity(targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            require(unexpected.waitForActivityWithTimeout(8_000) == null, "An app was blocked after turning blocking off");
            removeMonitor(unexpected);
            pass("Turning off blocking lets the selected app open");

            main = openMain();
            click("Disable demo access");
            require(!state.entitled(), "Demo access was not revoked");
            click("Block selected apps");
            require(!state.enabled(), "Subscription gate did not return after demo revocation");
            pass("Disabling demo restores the subscription gate");
            click("Enable demo access");
            click("Block selected apps");
            require(state.enabled(), "Could not enable blocking before permission revocation");
            File revoked = new File(getTargetContext().getFilesDir(), "smoke-access-revoked");
            Bundle revoke = new Bundle(); revoke.putString("stream", "REVOKE_ACCESSIBILITY\n"); sendStatus(0, revoke);
            deadline = SystemClock.elapsedRealtime() + 20_000;
            while ((!revoked.exists() || accessibilityReady() || state.enabled() || blockingEnabled())
                    && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200);
            require(revoked.exists() && !accessibilityReady() && !state.enabled() && !blockingEnabled(),
                    "Revoking accessibility did not pause blocking and disable the switch");
            main = openMain();
            require(hasText("Set up required access"), "Relaunch did not request revoked access");
            click("Not now");
            pass("Permission revocation pauses blocking and relaunch requests access again");
            result.putString("stream", "8 functional checks passed\n");
            result.putInt("tests", passed); result.putInt("failures", 0);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure + "\n");
            result.putInt("tests", passed); result.putInt("failures", 1);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private MainActivity openMain() {
        return (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private boolean accessibilityReady() {
        AccessibilityManager manager = (AccessibilityManager) getTargetContext().getSystemService(Activity.ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (getTargetContext().getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && AppBlockerService.class.getName().equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }
    private void click(String text) {
        runOnMainSync(() -> {
            View view = null;
            for (View root : WindowInspector.getGlobalWindowViews()) {
                View found = findText(root, text);
                if (found != null) view = found;
            }
            require(view != null, "Missing UI control: " + text);
            view.performClick();
        });
        // AlertDialog posts its positive-button callback to the main queue.
        waitForIdleSync();
    }
    private boolean hasText(String text) {
        final boolean[] found = {false};
        runOnMainSync(() -> {
            for (View root : WindowInspector.getGlobalWindowViews())
                if (findText(root, text) != null) found[0] = true;
        });
        return found[0];
    }
    private boolean blockingEnabled() {
        final boolean[] enabled = {false};
        runOnMainSync(() -> {
            for (View root : WindowInspector.getGlobalWindowViews()) {
                View toggle = findText(root, "Block selected apps");
                if (toggle != null) enabled[0] = toggle.isEnabled();
            }
        });
        return enabled[0];
    }
    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0; i<group.getChildCount(); i++) {
                View result = findText(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }
    private ListView findList() {
        for (View root : WindowInspector.getGlobalWindowViews()) {
            ListView found = findList(root);
            if (found != null) return found;
        }
        return null;
    }
    private static ListView findList(View view) {
        if (view instanceof ListView) return (ListView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0; i<group.getChildCount(); i++) {
                ListView result = findList(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
    private void pass(String message) {
        passed++;
        Bundle status = new Bundle(); status.putString("stream", "PASS: " + message + "\n");
        sendStatus(0, status);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
