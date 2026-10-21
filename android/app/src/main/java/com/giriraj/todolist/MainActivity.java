package com.giriraj.todolist;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TodoAlarmPlugin.class);
        super.onCreate(savedInstanceState);
        fromWidget(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        fromWidget(intent);
    }

    /** "+ Task" / "+ ₹" on the home-screen widget: the app opens that sheet when it reads its actions. */
    private void fromWidget(Intent i) {
        if (i == null) return;
        // "Share" from WhatsApp or any app -> new task
        if (Intent.ACTION_SEND.equals(i.getAction()) && "text/plain".equals(i.getType())) {
            String txt = i.getStringExtra(Intent.EXTRA_TEXT), sub = i.getStringExtra(Intent.EXTRA_SUBJECT);
            String all = ((sub != null && !sub.isEmpty() && (txt == null || !txt.contains(sub))) ? sub + " " : "") + (txt == null ? "" : txt);
            if (!all.trim().isEmpty()) {
                try { new AlarmStore(this).addAction(new JSONObject().put("action", "share").put("text", all.trim().length() > 1000 ? all.trim().substring(0, 1000) : all.trim())); } catch (Exception ignored) { }
            }
            i.setAction(Intent.ACTION_MAIN);
            return;
        }
        String w = i.getStringExtra("widget");
        if (w == null || "open".equals(w)) return;
        try { new AlarmStore(this).addAction(new JSONObject().put("action", "widget").put("what", w)); } catch (Exception ignored) { }
        i.removeExtra("widget");
    }
}
