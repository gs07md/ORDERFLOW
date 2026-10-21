package com.giriraj.todolist;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widget: today's tasks (tap the circle to finish one), today's spend, + Task / + Expense. */
public class TodoWidget extends AppWidgetProvider {
    static final String PREFS = "todo_widget";
    static final String ACTION_DONE = "com.giriraj.todolist.WIDGET_DONE";
    static final int ROWS = 6;
    static final int[] ROW = {R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4, R.id.w_row5};
    static final int[] CHK = {R.id.w_chk0, R.id.w_chk1, R.id.w_chk2, R.id.w_chk3, R.id.w_chk4, R.id.w_chk5};
    static final int[] TTL = {R.id.w_ttl0, R.id.w_ttl1, R.id.w_ttl2, R.id.w_ttl3, R.id.w_ttl4, R.id.w_ttl5};
    static final int[] TIM = {R.id.w_tim0, R.id.w_tim1, R.id.w_tim2, R.id.w_tim3, R.id.w_tim4, R.id.w_tim5};

    /** Saved by the app (TodoAlarmPlugin.widget). */
    static void save(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", json).apply();
        updateAll(ctx);
    }

    static JSONObject load(Context ctx) {
        try { return new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    static void store(Context ctx, JSONObject d) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", d.toString()).apply();
    }

    static void updateAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, TodoWidget.class))) m.updateAppWidget(id, build(ctx));
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, HabitWidget.class))) m.updateAppWidget(id, HabitWidget.build(ctx));
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, ListWidget.class))) m.updateAppWidget(id, ListWidget.build(ctx));
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, MoneyWidget.class))) m.updateAppWidget(id, MoneyWidget.build(ctx));
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_DONE.equals(a)) {
            String taskId = intent.getStringExtra("taskId");
            long at = intent.getLongExtra("at", 0);
            if (taskId == null) return;
            AlarmStore store = new AlarmStore(ctx);
            try { store.addAction(new JSONObject().put("taskId", taskId).put("at", at).put("action", "done")); } catch (Exception ignored) { }
            String alarmId = taskId + "@" + at;
            store.removeAlarm(alarmId);
            AlarmScheduler.cancel(ctx, alarmId);
            // take it off the widget right away; the app catches up when opened
            JSONObject d = load(ctx);
            JSONArray t = d.optJSONArray("tasks"), keep = new JSONArray();
            if (t != null) for (int i = 0; i < t.length(); i++) { JSONObject o = t.optJSONObject(i); if (o != null && !taskId.equals(o.optString("id"))) keep.put(o); }
            try { d.put("tasks", keep); d.put("doneToday", d.optInt("doneToday") + 1); } catch (Exception ignored) { }
            store(ctx, d);
            updateAll(ctx);
        } else if (Intent.ACTION_DATE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            updateAll(ctx);
        }
    }

    static PendingIntent openApp(Context ctx, String what, int req) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction("com.giriraj.todolist.OPEN_" + what);
        i.putExtra("widget", what);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static long startOfDay(long now, int add) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, add);
        return c.getTimeInMillis();
    }

    static String money(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        f.setMaximumFractionDigits(Math.round(v) == v ? 0 : 2);
        f.setMinimumFractionDigits(Math.round(v) == v ? 0 : 2);
        return "₹" + f.format(v);
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.todo_widget);
        JSONObject d = load(ctx);
        long now = System.currentTimeMillis(), today = startOfDay(now, 0), tomorrow = startOfDay(now, 1);

        v.setTextViewText(R.id.w_date, new SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(new Date(now)));

        // today's open tasks + anything overdue
        List<JSONObject> list = new ArrayList<>();
        JSONArray t = d.optJSONArray("tasks");
        if (t != null) for (int i = 0; i < t.length(); i++) {
            JSONObject o = t.optJSONObject(i);
            if (o != null && o.optLong("at") > 0 && o.optLong("at") < tomorrow) list.add(o);
        }
        Collections.sort(list, (x, y) -> Long.compare(x.optLong("at"), y.optLong("at")));

        // spend today
        double spent = 0;
        JSONArray xs = d.optJSONArray("exps");
        if (xs != null) for (int i = 0; i < xs.length(); i++) {
            JSONObject o = xs.optJSONObject(i);
            if (o != null && o.optLong("at") >= today && o.optLong("at") < tomorrow) spent += o.optDouble("a", 0);
        }
        int doneToday = d.optString("day").equals(new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date(now))) ? d.optInt("doneToday") : 0;
        String sum = list.isEmpty() ? (doneToday > 0 ? "All done for today ✓" : "Nothing for today") : (list.size() + (list.size() == 1 ? " task" : " tasks") + " to do");
        if (spent > 0) sum += "  ·  " + money(Math.round(spent * 100) / 100.0) + " spent";
        v.setTextViewText(R.id.w_sum, sum);

        boolean night = (ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int ink = night ? Color.parseColor("#E6EEEB") : Color.parseColor("#16211E");
        int muted = night ? Color.parseColor("#93A39D") : Color.parseColor("#66746F");
        int red = Color.parseColor("#D93A2B");
        SimpleDateFormat hm = new SimpleDateFormat("h:mm a", Locale.ENGLISH), dm = new SimpleDateFormat("d MMM", Locale.ENGLISH);

        for (int r = 0; r < ROWS; r++) {
            if (r >= list.size()) { v.setViewVisibility(ROW[r], View.GONE); continue; }
            JSONObject o = list.get(r);
            long at = o.optLong("at");
            boolean allDay = o.optBoolean("allDay");
            boolean late = allDay ? at < today : at < now;
            v.setViewVisibility(ROW[r], View.VISIBLE);
            v.setTextViewText(TTL[r], o.optString("title"));
            v.setTextColor(TTL[r], ink);
            String when = at < today ? dm.format(new Date(at)) : (allDay ? "Today" : hm.format(new Date(at)).toLowerCase(Locale.ENGLISH).replace(" ", ""));
            v.setTextViewText(TIM[r], when);
            v.setTextColor(TIM[r], late ? red : muted);
            Intent done = new Intent(ctx, TodoWidget.class).setAction(ACTION_DONE)
                .putExtra("taskId", o.optString("id")).putExtra("at", at);
            v.setOnClickPendingIntent(CHK[r], PendingIntent.getBroadcast(ctx, 100 + r, done, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            v.setOnClickPendingIntent(TTL[r], openApp(ctx, "open", 20 + r));
        }
        int more = list.size() - ROWS;
        v.setViewVisibility(R.id.w_more, more > 0 ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.w_more, "+" + more + " more");
        v.setViewVisibility(R.id.w_empty, list.isEmpty() ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.w_empty, d.has("tasks") ? (doneToday > 0 ? "🎉 All done for today" : "☀️ Nothing planned for today") : "Open the app once to show your tasks here");

        v.setOnClickPendingIntent(R.id.w_head, openApp(ctx, "open", 1));
        v.setOnClickPendingIntent(R.id.w_more, openApp(ctx, "open", 2));
        v.setOnClickPendingIntent(R.id.w_empty, openApp(ctx, "open", 3));
        v.setOnClickPendingIntent(R.id.w_add, openApp(ctx, "add", 4));
        v.setOnClickPendingIntent(R.id.w_addx, openApp(ctx, "addx", 5));
        return v;
    }
}
