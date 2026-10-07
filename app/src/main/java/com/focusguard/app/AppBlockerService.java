package com.focusguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Set;

public final class AppBlockerService extends AccessibilityService {
    private AppState state;
    private Set<String> protectedPackages;

    @Override protected void onServiceConnected() {
        state = new AppState(this);
        protectedPackages = AppState.protectedPackages(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (state == null || event.getPackageName() == null) return;
        String foreground = event.getPackageName().toString();
        if (!BlockPolicy.shouldBlock(foreground, getPackageName(), state.selected(), protectedPackages,
                state.enabled(), state.entitled())) return;
        startActivity(new Intent(this, BlockedActivity.class).putExtra("package", foreground)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override public void onInterrupt() { }
}
