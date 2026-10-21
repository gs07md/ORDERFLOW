package com.giriraj.todolist;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widget: today's habits, tap to tick. */
public class HabitWidget extends AppWidgetProvider {
    static final String ACTION = "com.giriraj.todolist.HABIT_TICK";
    static final int[] ROW = {R.id.h_row0, R.id.h_row1, R.id.h_row2, R.id.h_row3, R.id.h_row4, R.id.h_row5}, CHK = {R.id.h_chk0, R.id.h_chk1, R.id.h_chk2, R.id.h_chk3, R.id.h_chk4, R.id.h_chk5}, TTL = {R.id.h_ttl0, R.id.h_ttl1, R.id.h_ttl2, R.id.h_ttl3, R.id.h_ttl4, R.id.h_ttl5}, TIM = {R.id.h_tim0, R.id.h_tim1, R.id.h_tim2, R.id.h_tim3, R.id.h_tim4, R.id.h_tim5};

    @Override public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, build(ctx)); }

    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) { TodoWidget.updateAll(ctx); return; }
        if (!ACTION.equals(intent.getAction())) return;
        String id = intent.getStringExtra("id");
        if (id == null) return;
        String day = today();
        JSONObject d = TodoWidget.load(ctx);
        JSONArray hs = d.optJSONArray("habits");
        boolean sameDay = day.equals(d.optString("hday"));
        boolean on = true;
        if (hs != null) for (int i = 0; i < hs.length(); i++) {
            JSONObject h = hs.optJSONObject(i);
            if (h == null) continue;
            if (!sameDay) { try { h.put("done", false); } catch (Exception ignored) { } }
            if (id.equals(h.optString("id"))) {
                on = !h.optBoolean("done");
                try { h.put("done", on); h.put("streak", Math.max(0, h.optInt("streak") + (on ? 1 : -1))); } catch (Exception ignored) { }
            }
        }
        try { d.put("hday", day); new AlarmStore(ctx).addAction(new JSONObject().put("action", "habit").put("id", id).put("day", day).put("on", on)); } catch (Exception ignored) { }
        TodoWidget.store(ctx, d);
        TodoWidget.updateAll(ctx);
    }

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date()); }

    static boolean onDay(JSONObject h, int dow) {
        JSONArray days = h.optJSONArray("days");
        if (days == null || days.length() == 0) return true;
        for (int i = 0; i < days.length(); i++) if (days.optInt(i, -1) == dow) return true;
        return false;
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.habit_widget);
        JSONObject d = TodoWidget.load(ctx);
        JSONArray hs = d.optJSONArray("habits");
        boolean sameDay = today().equals(d.optString("hday"));
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        java.util.List<JSONObject> list = new java.util.ArrayList<>();
        if (hs != null) for (int i = 0; i < hs.length(); i++) { JSONObject h = hs.optJSONObject(i); if (h != null && onDay(h, dow)) list.add(h); }
        int done = 0;
        for (JSONObject h : list) if (sameDay && h.optBoolean("done")) done++;
        v.setTextViewText(R.id.h_title, "Habits");
        v.setTextViewText(R.id.h_sum, list.isEmpty() ? new SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(new Date()) : done + " of " + list.size() + " done today" + (done == list.size() ? " 🎉" : ""));
        for (int r = 0; r < ROW.length; r++) {
            if (r >= list.size()) { v.setViewVisibility(ROW[r], View.GONE); continue; }
            JSONObject h = list.get(r);
            boolean on = sameDay && h.optBoolean("done");
            v.setViewVisibility(ROW[r], View.VISIBLE);
            v.setTextViewText(CHK[r], on ? "●" : "○");
            v.setTextViewText(TTL[r], h.optString("emoji", "✅") + "  " + h.optString("name"));
            int st = h.optInt("streak");
            v.setTextViewText(TIM[r], st > 1 ? "🔥" + st : "");
            Intent t = new Intent(ctx, HabitWidget.class).setAction(ACTION).putExtra("id", h.optString("id"));
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 300 + r, t, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(CHK[r], pi);
            v.setOnClickPendingIntent(TTL[r], pi);
        }
        int more = list.size() - ROW.length;
        v.setViewVisibility(R.id.h_more, more > 0 ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.h_more, "+" + more + " more");
        v.setViewVisibility(R.id.h_empty, list.isEmpty() ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.h_empty, hs == null || hs.length() == 0 ? "Add habits in the app (Today → Habits)" : "No habits today 🌿");
        v.setOnClickPendingIntent(R.id.h_head, TodoWidget.openApp(ctx, "habits", 31));
        v.setOnClickPendingIntent(R.id.h_btn, TodoWidget.openApp(ctx, "habits", 32));
        v.setOnClickPendingIntent(R.id.h_empty, TodoWidget.openApp(ctx, "habits", 33));
        return v;
    }
}
