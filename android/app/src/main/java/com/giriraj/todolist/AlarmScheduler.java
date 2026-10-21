package com.giriraj.todolist;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Puts every alarm into Android's AlarmManager as a real alarm clock (exact, works in Doze and when locked). */
public class AlarmScheduler {
    public static boolean canExact(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    private static PendingIntent fireIntent(Context ctx, String id) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction("com.giriraj.todolist.ALARM");
        i.putExtra("alarmId", id);
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void scheduleOne(Context ctx, String id, long at) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = fireIntent(ctx, id);
        if (canExact(ctx)) {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent(ctx)), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context ctx, String id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(fireIntent(ctx, id));
    }

    /** Cancel everything we set before, then set all current alarms again. */
    public static int scheduleAll(Context ctx) {
        AlarmStore store = new AlarmStore(ctx);
        long now = System.currentTimeMillis();
        Set<String> next = new HashSet<>();
        JSONArray[] lists = { store.getAlarms(), store.getExtras() };
        for (JSONArray a : lists) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                long at = o.optLong("at");
                String id = o.optString("id");
                if (id.isEmpty() || at < now - 60_000L) continue;
                next.add(id);
            }
        }
        for (String old : store.scheduledIds()) if (!next.contains(old)) cancel(ctx, old);
        for (JSONArray a : lists) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id");
                if (!next.contains(id)) continue;
                scheduleOne(ctx, id, Math.max(o.optLong("at"), now + 1000L));
            }
        }
        store.setScheduledIds(next);
        return next.size();
    }
}
