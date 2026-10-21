package com.giriraj.todolist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import org.json.JSONObject;

/** Rings like an alarm clock: loud looping sound on the alarm volume, vibration, full-screen alarm screen. */
public class AlarmService extends Service {
    public static final String ACTION_RING = "com.giriraj.todolist.RING";
    public static final String ACTION_STOP = "com.giriraj.todolist.STOP";
    public static final String CHANNEL = "alarms_v1";
    public static final String CHANNEL_MISSED = "missed_v1";
    public static final int NOTIF_ID = 4242;
    public static volatile String current = null; // alarm json being shown

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeout;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) { stopAll(); stopSelf(); return START_NOT_STICKY; }
        String json = intent.getStringExtra("alarm");
        JSONObject a;
        try { a = new JSONObject(json); } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        stopSound();
        current = json;
        ensureChannels(this);

        Notification n = buildNotification(a, json);
        int type = Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, NOTIF_ID, n, type);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "todolist:ring");
        AlarmStore store = new AlarmStore(this);
        int mins = Math.max(1, Math.min(10, store.ringMins()));
        wake.acquire(mins * 60_000L + 10_000L);
        startSound(store.toneUri());
        if (store.vibrate()) startVibrate();

        // open the alarm screen directly too (works when the app is allowed to; the notification covers the rest)
        try {
            Intent act = new Intent(this, AlarmActivity.class);
            act.putExtra("alarm", json);
            act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            startActivity(act);
        } catch (Exception ignored) { }

        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = () -> { missed(a); stopAll(); stopSelf(); };
        handler.postDelayed(timeout, mins * 60_000L);
        return START_NOT_STICKY;
    }

    private Notification buildNotification(JSONObject a, String json) {
        Intent full = new Intent(this, AlarmActivity.class);
        full.putExtra("alarm", json);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(this, 7, full, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = a.optString("title", "Reminder");
        String body = a.optString("body", "");
        String when = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(new java.util.Date(a.optLong("at", System.currentTimeMillis())));
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ " + title)
                .setContentText(body.isEmpty() ? when : when + " · " + body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .addAction(0, "✓ Done", actionPi("done", json, 11))
                .addAction(0, "10 min later", actionPi("snooze10", json, 12));
        String phone = a.optString("phone", "");
        String act = a.optString("act", "");
        if (!phone.isEmpty()) {
            String label = "wa".equals(act) ? "💬 WhatsApp" : "wacall".equals(act) ? "📲 WhatsApp call" : "📞 Call";
            b.addAction(0, label, actionPi(act.isEmpty() ? "call" : act, json, 13));
        }
        return b.build();
    }

    private PendingIntent actionPi(String action, String json, int code) {
        Intent i = new Intent(this, ActionReceiver.class);
        i.putExtra("action", action);
        i.putExtra("alarm", json);
        return PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Task alarms", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Rings when a task is due");
            ch.setSound(null, null); // the service plays the alarm sound itself, on a loop
            ch.enableVibration(false);
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_MISSED) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_MISSED, "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
    }

    private void startSound(String toneUri) {
        Uri uri = null;
        if (toneUri != null && !toneUri.isEmpty()) uri = Uri.parse(toneUri);
        if (uri == null) uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception ignored) { }
        }
    }

    private void startVibrate() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        long[] pattern = { 0, 700, 500, 700, 1200 };
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        else vibrator.vibrate(pattern, 0);
    }

    private void stopSound() {
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
        if (vibrator != null) { vibrator.cancel(); vibrator = null; }
    }

    private void stopAll() {
        stopSound();
        if (timeout != null) handler.removeCallbacks(timeout);
        if (wake != null && wake.isHeld()) wake.release();
        current = null;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    private void missed(JSONObject a) {
        try {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(this, 9, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new NotificationCompat.Builder(this, CHANNEL_MISSED)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Missed: " + a.optString("title"))
                    .setContentText("Tap to open your tasks")
                    .setContentIntent(pi).setAutoCancel(true).build();
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(a.optString("id").hashCode(), n);
            new AlarmStore(this).addAction(new JSONObject().put("taskId", a.optString("taskId")).put("at", a.optLong("at")).put("action", "stop"));
        } catch (Exception ignored) { }
    }

    @Override public void onDestroy() { stopAll(); super.onDestroy(); }
}
