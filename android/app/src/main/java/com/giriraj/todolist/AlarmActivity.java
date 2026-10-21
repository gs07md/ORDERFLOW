package com.giriraj.todolist;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

/** Full-screen alarm screen, shown over the lock screen. */
public class AlarmActivity extends Activity {
    private String json;
    private JSONObject alarm;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.parseColor("#0B3A30"));
        getWindow().setNavigationBarColor(Color.parseColor("#0B3A30"));
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); load(intent); }

    private void load(Intent intent) {
        json = intent.getStringExtra("alarm");
        if (json == null) json = AlarmService.current;
        if (json == null) { finish(); return; }
        try { alarm = new JSONObject(json); } catch (Exception e) { finish(); return; }
        setContentView(build());
    }

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private Button button(String text, String bg, String fg, String action) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(17);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.parseColor(fg));
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(bg));
        d.setCornerRadius(dp(16));
        btn.setBackground(d);
        btn.setPadding(dp(12), dp(14), dp(12), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> act(action));
        return btn;
    }

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.parseColor("#0B3A30"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(48), dp(24), dp(32));
        sv.addView(col);

        TextView bell = new TextView(this);
        bell.setText("⏰");
        bell.setTextSize(64);
        bell.setGravity(Gravity.CENTER);
        col.addView(bell);
        bell.animate().rotation(12f).setDuration(250).withEndAction(new Runnable() {
            boolean right = false;
            @Override public void run() { right = !right; bell.animate().rotation(right ? -12f : 12f).setDuration(250).withEndAction(this).start(); }
        }).start();

        TextView time = new TextView(this);
        time.setText(new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(new java.util.Date(alarm.optLong("at", System.currentTimeMillis()))));
        time.setTextColor(Color.parseColor("#B7D9CF"));
        time.setTextSize(18);
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, dp(8), 0, 0);
        col.addView(time);

        TextView title = new TextView(this);
        title.setText(alarm.optString("title", "Reminder"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(10), 0, dp(6));
        col.addView(title);

        String body = alarm.optString("body", "");
        if (!body.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(body);
            t.setTextColor(Color.parseColor("#D5EAE3"));
            t.setTextSize(16);
            t.setGravity(Gravity.CENTER);
            col.addView(t);
        }

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, dp(24)));

        col.addView(button("✓  Done", "#FFFFFF", "#0B3A30", "done"));
        String phone = alarm.optString("phone", "");
        if (!phone.isEmpty()) {
            String who = alarm.optString("cname", "");
            if (who.isEmpty()) who = phone;
            String act = alarm.optString("act", "");
            String[] order = "wa".equals(act) ? new String[]{"wa", "call", "wacall"} : "wacall".equals(act) ? new String[]{"wacall", "call", "wa"} : new String[]{"call", "wa", "wacall"};
            for (int i = 0; i < order.length; i++) {
                String k = order[i];
                String label = "call".equals(k) ? "📞  Call" : "wa".equals(k) ? "💬  WhatsApp" : "📲  WhatsApp call";
                col.addView(button(i == 0 ? label + " " + who : label, i == 0 ? "#1E8E4E" : "#2A5A4F", "#FFFFFF", k));
            }
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button s10 = button("10 min later", "#2A5A4F", "#FFFFFF", "snooze10");
        Button s60 = button("1 hour later", "#2A5A4F", "#FFFFFF", "snooze60");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.topMargin = dp(10); half.rightMargin = dp(5);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.topMargin = dp(10); half2.leftMargin = dp(5);
        s10.setLayoutParams(half); s60.setLayoutParams(half2);
        row.addView(s10); row.addView(s60);
        col.addView(row);
        col.addView(button("Open app", "#2A5A4F", "#FFFFFF", "open"));
        col.addView(button("Stop", "#14473C", "#B7D9CF", "stop"));
        return sv;
    }

    private void act(String action) {
        boolean needsUnlock = action.equals("call") || action.equals("wa") || action.equals("wacall") || action.equals("open");
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (needsUnlock && Build.VERSION.SDK_INT >= 26 && km != null && km.isKeyguardLocked()) {
            Actions.stopRinging(this);
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { Actions.handle(AlarmActivity.this, action, json); finish(); }
                @Override public void onDismissCancelled() { }
            });
            return;
        }
        Actions.handle(this, action, json);
        finish();
    }

    @Override public void onBackPressed() { /* use the buttons */ }
}
