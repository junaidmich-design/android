package com.focusguard.app;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity implements BillingManager.Listener {
    private AppState state;
    private BillingManager billing;
    private TextView status, statusDetail, selection, accessStatus, planStatus, billingMessage;
    private Switch blocking;
    private Button subscribe, accessButton, demoButton;
    private boolean updating;
    private boolean permissionPrompted;
    private AlertDialog permissionDialog;
    private final Handler permissionHandler = new Handler(Looper.getMainLooper());
    private long accessCheckDeadline;
    private final Runnable accessCheck = new Runnable() {
        @Override public void run() {
            render();
            // Binding and unbinding can lag behind Settings notifications.
            if (SystemClock.uptimeMillis() < accessCheckDeadline)
                permissionHandler.postDelayed(this, 250);
        }
    };
    private final ContentObserver accessObserver = new ContentObserver(permissionHandler) {
        @Override public void onChange(boolean selfChange) { refreshAccess(); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        permissionPrompted = saved != null && saved.getBoolean("permissionPrompted");
        state = new AppState(this);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Ui.PAPER); scroll.setFillViewport(true);
        LinearLayout root = Ui.column(this); scroll.addView(root); setContentView(scroll); Ui.edgeInsets(this, root, 24);
        root.addView(Ui.text(this, "◈  FocusGuard", 20, Ui.GREEN, true));
        root.addView(Ui.text(this, "Less distraction.\nMore you.", 36, Ui.INK, true));
        root.addView(Ui.text(this, "Make room for what matters. Choose the apps you want a break from.", 16, Ui.MUTED, false));

        LinearLayout focus = Ui.card(this, root);
        focus.setBackground(Ui.background(Color.rgb(229, 239, 224), 24, this));
        status = Ui.text(this, "Your focus, protected", 23, Ui.INK, true); focus.addView(status);
        statusDetail = Ui.text(this, "", 14, Ui.MUTED, false); focus.addView(statusDetail);
        blocking = new Switch(this); blocking.setText("Block selected apps"); blocking.setTextSize(17);
        blocking.setPadding(0, Ui.dp(this, 16), 0, 0); focus.addView(blocking);
        blocking.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            if (checked && (!state.entitled() || !accessEnabled() || state.selected().isEmpty())) {
                render(); return;
            }
            state.enabled(checked); render();
        });

        LinearLayout apps = Ui.card(this, root);
        apps.addView(Ui.text(this, "01  Choose your distractions", 18, Ui.INK, true));
        selection = Ui.text(this, "", 14, Ui.MUTED, false); apps.addView(selection);
        Button choose = Ui.button(this, "Choose apps", false); apps.addView(choose); choose.setOnClickListener(v -> chooseApps());

        LinearLayout access = Ui.card(this, root);
        access.addView(Ui.text(this, "02  Allow app blocking", 18, Ui.INK, true));
        accessStatus = Ui.text(this, "", 14, Ui.MUTED, false); access.addView(accessStatus);
        accessButton = Ui.button(this, "Enable accessibility", false); access.addView(accessButton);
        accessButton.setOnClickListener(v -> { if (accessEnabled()) openAccessSettings(); else showDisclosure(); });

        LinearLayout plan = Ui.card(this, root);
        plan.addView(Ui.text(this, "03  Your monthly plan", 18, Ui.INK, true));
        planStatus = Ui.text(this, "Block distractions. Keep your balance.", 16, Ui.INK, true); plan.addView(planStatus);
        plan.addView(Ui.text(this, "A monthly subscription unlocks app blocking. Renews automatically until canceled in Google Play.", 14, Ui.MUTED, false));
        billingMessage = Ui.text(this, "Connecting to Google Play…", 13, Ui.MUTED, false); plan.addView(billingMessage);
        subscribe = Ui.button(this, "Loading monthly plan…", true); subscribe.setEnabled(false); plan.addView(subscribe);
        Button restore = Ui.button(this, "Restore purchases", false); plan.addView(restore);
        Button manage = Ui.button(this, "Manage subscription", false); plan.addView(manage);
        manage.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku="
                    + BillingManager.PRODUCT_ID + "&package=" + getPackageName()))); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Open Google Play → Payments & subscriptions.", Toast.LENGTH_LONG).show(); }
        });
        if (BuildConfig.DEBUG) {
            plan.addView(Ui.text(this, "DEVELOPMENT BUILD · Demo access is free, makes no purchase, and is absent from release builds.", 12, Ui.MUTED, true));
            demoButton = Ui.button(this, "Enable demo access", false); plan.addView(demoButton);
            demoButton.setOnClickListener(v -> { state.demo(!state.demo()); render(); });
        }
        Button privacy = Ui.button(this, "Privacy & how blocking works", false); root.addView(privacy);
        privacy.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Your apps stay private")
                .setMessage("Your blocked-app list stays on this device. FocusGuard observes only the foreground app package; it does not read screen content, messages, passwords, or browsing history. No app-usage data is uploaded. Google Play handles payments under its own privacy terms.\n\nBlocking is a voluntary focus tool. You can turn it off here or in Android Accessibility Settings. It is not parental control or tamper-proof device management. Payment checks require Internet access; cached access lasts up to 24 hours. If it expires, reopen FocusGuard to restore purchases.")
                .setPositiveButton("Got it", null).show());
        root.addView(Ui.text(this, "Small boundaries. Better days.", 13, Ui.MUTED, false));
        billing = new BillingManager(this, state, this);
        subscribe.setOnClickListener(v -> billing.subscribe()); restore.setOnClickListener(v -> billing.refresh());
        render();
    }
    @Override protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, accessObserver);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, accessObserver);
    }
    @Override protected void onResume() {
        super.onResume(); refreshAccess(); billing.connect();
        if (!accessEnabled() && !permissionPrompted) showDisclosure();
    }
    @Override protected void onStop() {
        getContentResolver().unregisterContentObserver(accessObserver);
        permissionHandler.removeCallbacks(accessCheck);
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("permissionPrompted", permissionPrompted && (permissionDialog == null || !permissionDialog.isShowing()));
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        permissionHandler.removeCallbacks(accessCheck);
        if (permissionDialog != null) permissionDialog.dismiss();
        if (billing != null) billing.close(); super.onDestroy();
    }
    private void refreshAccess() {
        permissionHandler.removeCallbacks(accessCheck);
        accessCheckDeadline = SystemClock.uptimeMillis() + 10_000;
        accessCheck.run();
    }
    private boolean accessEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && AppBlockerService.class.getName().equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }
    private void render() {
        boolean access = accessEnabled();
        boolean ready = state.entitled() && access && !state.selected().isEmpty();
        if (!access && state.enabled()) state.enabled(false);
        if (access && permissionDialog != null && permissionDialog.isShowing()) permissionDialog.dismiss();
        boolean active = state.enabled() && ready;
        updating = true; blocking.setChecked(active); blocking.setEnabled(ready); updating = false;
        status.setText(active ? "Your focus is protected" : "Build a little breathing room");
        statusDetail.setText(active ? "Selected apps are blocked. You’re in control."
                : !access ? "Enable accessibility access to finish permission setup. Blocking stays off until access is granted."
                : state.selected().isEmpty() ? "Choose the apps you want to block."
                : !state.entitled() ? "Activate your monthly subscription to enable blocking."
                : "Setup complete. Turn on blocking when you’re ready.");
        Set<String> selected = state.selected();
        List<String> names = new ArrayList<>();
        for (String packageName : selected) {
            try { names.add(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString()); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { names.add(packageName); }
        }
        Collections.sort(names);
        selection.setText(names.isEmpty() ? "No apps selected yet. Your home screen and Settings always remain available."
                : names.size() + (names.size() == 1 ? " app selected · " : " apps selected · ") + String.join(", ", names));
        accessStatus.setText(access ? "Access enabled. Screen content is never read." : "Permission needed to detect when a selected app opens.");
        accessButton.setText(access ? "Open accessibility settings" : "Enable accessibility");
        planStatus.setText(state.demo() ? "Demo access active · no payment" : state.entitled() ? "Subscription active" : "Monthly subscription required");
        if (demoButton != null) demoButton.setText(state.demo() ? "Disable demo access" : "Enable demo access");
    }
    private void showDisclosure() {
        if (permissionDialog != null && permissionDialog.isShowing()) return;
        permissionPrompted = true;
        permissionDialog = new AlertDialog.Builder(this).setTitle("Set up required access")
                .setMessage(getString(R.string.accessibility_description) + "\n\nAndroid needs your approval before FocusGuard can block apps. On the next screen, choose FocusGuard and enable its accessibility service, then return here. Blocking stays disabled until setup is complete.")
                .setNegativeButton("Not now", null).setPositiveButton("I agree · Open settings", (dialog, which) -> openAccessSettings()).show();
    }
    private void openAccessSettings() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (ActivityNotFoundException unavailable) {
            Toast.makeText(this, "Open Android Settings → Accessibility → FocusGuard to enable access.", Toast.LENGTH_LONG).show();
        }
    }
    private void chooseApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> protectedApps = AppState.protectedPackages(this);
        Map<String, String> labels = new HashMap<>();
        for (ResolveInfo info : getPackageManager().queryIntentActivities(launcher, 0)) {
            String packageName = info.activityInfo.packageName;
            if (!protectedApps.contains(packageName)) labels.put(packageName, info.loadLabel(getPackageManager()).toString());
        }
        List<String> packages = new ArrayList<>(labels.keySet());
        packages.sort(Comparator.comparing(labels::get, String.CASE_INSENSITIVE_ORDER));
        if (packages.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("No selectable apps found. Install an app, then try again.").setPositiveButton("OK", null).show(); return;
        }
        Set<String> chosen = state.selected();
        String[] titles = new String[packages.size()]; boolean[] checked = new boolean[packages.size()];
        for (int i = 0; i < packages.size(); i++) { titles[i] = labels.get(packages.get(i)); checked[i] = chosen.contains(packages.get(i)); }
        new AlertDialog.Builder(this).setTitle("Apps to take a break from")
                .setMultiChoiceItems(titles, checked, (dialog, index, isChecked) -> {
                    if (isChecked) chosen.add(packages.get(index)); else chosen.remove(packages.get(index));
                }).setNegativeButton("Cancel", null).setPositiveButton("Save selection", (dialog, which) -> {
                    state.selected(chosen); render();
                }).show();
    }
    @Override public void onBillingChanged(String message, String price, boolean available) {
        if (isFinishing() || isDestroyed()) return;
        billingMessage.setText(message); subscribe.setText("Subscribe · " + price); subscribe.setEnabled(available); render();
    }
}
