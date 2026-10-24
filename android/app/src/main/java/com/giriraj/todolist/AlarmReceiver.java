package com.giriraj.todolist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

/** Fired by AlarmManager at the reminder time: shows a notification, or starts the ringing alarm. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String id = intent.getStringExtra("alarmId");
        if (id == null) return;
        JSONObject alarm = new AlarmStore(ctx).findAlarm(id);
        if (alarm == null) return; // task was finished or deleted after the alarm was set
        String style = alarm.optString("style", "");
        if (style.isEmpty()) style = new AlarmStore(ctx).style();
        if ("notify".equals(style)) { TaskNotify.show(ctx, alarm); return; }
        Intent s = new Intent(ctx, AlarmService.class);
        s.setAction(AlarmService.ACTION_RING);
        s.putExtra("alarm", alarm.toString());
        ContextCompat.startForegroundService(ctx, s);
    }
}
