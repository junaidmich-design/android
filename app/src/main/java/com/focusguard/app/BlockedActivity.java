package com.focusguard.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class BlockedActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goHome);
        }
        render();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); render(); }
    private void render() {
        LinearLayout root = Ui.column(this); root.setGravity(Gravity.CENTER); root.setBackgroundColor(Ui.PAPER);
        setContentView(root); Ui.edgeInsets(this, root, 32);
        root.addView(Ui.text(this, "◈  FocusGuard", 20, Ui.GREEN, true));
        root.addView(Ui.text(this, "Take a breath.", 38, Ui.INK, true));
        String name = "This app";
        String packageName = getIntent().getStringExtra("package");
        if (packageName != null) {
            try { name = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        }
        TextView detail = Ui.text(this, name + " is on your blocked list.\nYou chose to make space for something better.", 17, Ui.MUTED, false);
        detail.setGravity(Gravity.CENTER); root.addView(detail);
        Button home = Ui.button(this, "Back to my day", true); root.addView(home);
        home.setOnClickListener(v -> goHome());
        Button manage = Ui.button(this, "Manage blocked apps", false); root.addView(manage);
        manage.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });
    }
    private void goHome() { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish(); }
    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && android.os.Build.VERSION.SDK_INT < 33) {
            goHome(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
