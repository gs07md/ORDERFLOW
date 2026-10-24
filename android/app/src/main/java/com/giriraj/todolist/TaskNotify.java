package com.giriraj.todolist;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

/** "Notification" reminder style: a normal heads-up notification with the phone's notification sound (no ringing screen). */
public class TaskNotify {
    public static final String CHANNEL = "reminders_v1";

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Reminders for your tasks");
        ch.enableVibration(true);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        nm.createNotificationChannel(ch);
    }

    static int notifId(String alarmId) { return 5000 + (alarmId == null ? 0 : (alarmId.hashCode() & 0xFFFFF)); }

    public static void show(Context ctx, JSONObject a) {
        ensureChannel(ctx);
        String json = a.toString(), id = a.optString("id");
        String title = a.optString("title", "Reminder"), body = a.optString("body", "");
        String when = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(new java.util.Date(a.optLong("at", System.currentTimeMillis())));
        // tapping opens the app directly (Android blocks opening apps from a receiver behind a notification)
        Intent open = new Intent(ctx, MainActivity.class).setAction("com.giriraj.todolist.OPEN_REMINDER." + id).putExtra("reminder", json)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(ctx, notifId(id) + 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🔔 " + title)
            .setContentText(body.isEmpty() ? when : when + " · " + body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body.isEmpty() ? when : when + " · " + body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "✓ Done", pi(ctx, "done", json, notifId(id) + 2))
            .addAction(0, "10 min later", pi(ctx, "snooze10", json, notifId(id) + 3));
        String phone = a.optString("phone", ""), act = a.optString("act", "");
        if (!phone.isEmpty()) {
            String label = "wa".equals(act) ? "💬 WhatsApp" : "wacall".equals(act) ? "📲 WA call" : "📞 Call";
            b.addAction(0, label, pi(ctx, act.isEmpty() ? "call" : act, json, notifId(id) + 4));
        }
        try { ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(notifId(id), b.build()); } catch (Exception ignored) { }
    }

    private static PendingIntent pi(Context ctx, String action, String json, int code) {
        Intent i = new Intent(ctx, ActionReceiver.class).putExtra("action", action).putExtra("alarm", json);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context ctx, String alarmId) {
        try { ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(notifId(alarmId)); } catch (Exception ignored) { }
    }
}
