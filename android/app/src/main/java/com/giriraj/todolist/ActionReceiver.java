package com.giriraj.todolist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Buttons on the alarm notification (Done / Snooze). */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Actions.handle(ctx, intent.getStringExtra("action"), intent.getStringExtra("alarm"));
    }
}
