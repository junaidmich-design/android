package com.focusguard.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class AppState {
    private final SharedPreferences preferences;
    AppState(Context context) { preferences = context.getSharedPreferences("focusguard", Context.MODE_PRIVATE); }
    Set<String> selected() { return new HashSet<>(preferences.getStringSet("selected", new HashSet<>())); }
    void selected(Set<String> packages) { preferences.edit().putStringSet("selected", new HashSet<>(packages)).apply(); }
    boolean enabled() { return preferences.getBoolean("enabled", false); }
    void enabled(boolean value) { preferences.edit().putBoolean("enabled", value).apply(); }
    boolean demo() { return BuildConfig.DEBUG && preferences.getBoolean("demo", false); }
    void demo(boolean value) { if (BuildConfig.DEBUG) preferences.edit().putBoolean("demo", value).apply(); }
    boolean entitled() {
        return demo() || BlockPolicy.entitlementValid(preferences.getBoolean("purchased", false),
                preferences.getLong("verifiedAt", 0), System.currentTimeMillis());
    }
    void purchase(boolean purchased) {
        preferences.edit().putBoolean("purchased", purchased)
                .putLong("verifiedAt", purchased ? System.currentTimeMillis() : 0).apply();
    }
    static Set<String> protectedPackages(Context context) {
        Set<String> result = new HashSet<>(Arrays.asList(context.getPackageName(), "com.android.settings",
                "com.android.systemui", "com.android.vending", "com.google.android.permissioncontroller",
                "com.android.permissioncontroller"));
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(home, 0)) {
            result.add(info.activityInfo.packageName);
        }
        return result;
    }
}
