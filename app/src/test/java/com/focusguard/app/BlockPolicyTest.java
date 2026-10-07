package com.focusguard.app;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class BlockPolicyTest {
    private final Set<String> selected = Set.of("social.app", "settings", "focusguard");
    private final Set<String> protectedApps = Set.of("settings", "launcher");
    private boolean block(String app, boolean enabled, boolean paid) {
        return BlockPolicy.shouldBlock(app, "focusguard", selected, protectedApps, enabled, paid);
    }
    @Test public void selectedAppsRequireBothPaymentAndEnabledBlocking() {
        assertTrue(block("social.app", true, true));
        assertFalse(block("social.app", false, true));
        assertFalse(block("social.app", true, false));
    }
    @Test public void systemNavigationAndOurOwnAppRemainAccessible() {
        assertFalse(block("settings", true, true));
        assertFalse(block("focusguard", true, true));
        assertFalse(block("launcher", true, true));
    }
    @Test public void unselectedAndMissingPackagesAreAllowed() {
        assertFalse(block("other.app", true, true));
        assertFalse(block(null, true, true));
        assertFalse(block("", true, true));
    }
    @Test public void cachedPaymentExpiresAfterOneDay() {
        long verified = 100_000L;
        assertTrue(BlockPolicy.entitlementValid(true, verified, verified + BlockPolicy.OFFLINE_GRACE_MS - 1));
        assertFalse(BlockPolicy.entitlementValid(true, verified, verified + BlockPolicy.OFFLINE_GRACE_MS));
        assertFalse(BlockPolicy.entitlementValid(false, verified, verified));
    }
    @Test public void missingOrFutureVerificationCannotUnlockBlocking() {
        assertFalse(BlockPolicy.entitlementValid(true, 0, 100));
        assertFalse(BlockPolicy.entitlementValid(true, 100, 99));
    }
}
