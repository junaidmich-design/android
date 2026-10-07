package com.focusguard.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

final class Ui {
    static final int INK = Color.rgb(25, 43, 35), GREEN = Color.rgb(36, 92, 69);
    static final int MUTED = Color.rgb(103, 116, 106), PAPER = Color.rgb(247, 247, 239);
    static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(activity, 5), 0, dp(activity, 5));
        return view;
    }
    static GradientDrawable background(int color, int radius, Activity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(activity, radius)); return drawable;
    }
    static LinearLayout column(Activity activity) {
        LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    static LinearLayout card(Activity activity, LinearLayout parent) {
        LinearLayout card = column(activity);
        card.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 18));
        card.setBackground(background(Color.WHITE, 24, activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 16); parent.addView(card, params); return card;
    }
    static Button button(Activity activity, String title, boolean primary) {
        Button view = new Button(activity); view.setText(title); view.setAllCaps(false); view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(primary ? Color.WHITE : GREEN);
        view.setBackground(background(primary ? GREEN : Color.rgb(234, 242, 235), 14, activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 52));
        params.topMargin = dp(activity, 12); view.setLayoutParams(params); return view;
    }
    static void edgeInsets(Activity activity, View root, int padding) {
        int p = dp(activity, padding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(p + bars.left, p + bars.top, p + bars.right, p + bars.bottom);
            } else {
                view.setPadding(p + insets.getSystemWindowInsetLeft(), p + insets.getSystemWindowInsetTop(),
                        p + insets.getSystemWindowInsetRight(), p + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }
}
