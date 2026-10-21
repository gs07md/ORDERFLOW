package com.giriraj.todolist;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small home-screen widget: spent today and this month, plus a quick "+ ₹". */
public class MoneyWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, build(ctx)); }

    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) TodoWidget.updateAll(ctx);
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.money_widget);
        JSONObject d = TodoWidget.load(ctx);
        long now = System.currentTimeMillis(), today = TodoWidget.startOfDay(now, 0), tomorrow = TodoWidget.startOfDay(now, 1);
        java.util.Calendar c = java.util.Calendar.getInstance(); c.setTimeInMillis(today); c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        long monthStart = c.getTimeInMillis();
        double t = 0, m = 0;
        JSONArray xs = d.optJSONArray("mexps");
        if (xs != null) for (int i = 0; i < xs.length(); i++) {
            JSONObject o = xs.optJSONObject(i);
            if (o == null) continue;
            long at = o.optLong("at"); double a = o.optDouble("a", 0);
            if (at >= today && at < tomorrow) t += a;
            if (at >= monthStart && at < tomorrow) m += a;
        }
        v.setTextViewText(R.id.m_today, TodoWidget.money(Math.round(t * 100) / 100.0));
        v.setTextViewText(R.id.m_month, new SimpleDateFormat("MMMM", Locale.ENGLISH).format(new Date(now)) + ": " + TodoWidget.money(Math.round(m * 100) / 100.0));
        v.setOnClickPendingIntent(R.id.m_root, TodoWidget.openApp(ctx, "money", 51));
        v.setOnClickPendingIntent(R.id.m_add, TodoWidget.openApp(ctx, "addx", 52));
        return v;
    }
}
