package com.focusguard.app;

import java.util.Set;

/** Pure decision logic shared by the UI and accessibility service. */
public final class BlockPolicy {
    public static final long OFFLINE_GRACE_MS = 24 * 60 * 60 * 1000L;
    private BlockPolicy() {}

    public static boolean entitlementValid(boolean purchased, long verifiedAt, long now) {
        return purchased && verifiedAt > 0 && now >= verifiedAt
                && now - verifiedAt < OFFLINE_GRACE_MS;
    }

    public static boolean shouldBlock(String foreground, String ownPackage, Set<String> selected,
                                      Set<String> protectedPackages, boolean enabled, boolean entitled) {
        return enabled && entitled && foreground != null && !foreground.isEmpty()
                && !foreground.equals(ownPackage) && !protectedPackages.contains(foreground)
                && selected.contains(foreground);
    }
}
