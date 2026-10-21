package com.giriraj.todolist;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

/** What happens when a button is pressed on the alarm screen or notification. */
public class Actions {
    public static void handle(Context ctx, String action, String alarmJson) {
        if (action == null) return;
        JSONObject a;
        try { a = new JSONObject(alarmJson == null ? "{}" : alarmJson); } catch (Exception e) { a = new JSONObject(); }
        stopRinging(ctx);
        AlarmStore store = new AlarmStore(ctx);
        String id = a.optString("id"), taskId = a.optString("taskId");
        long at = a.optLong("at");
        boolean isTest = "test".equals(taskId);
        try {
            switch (action) {
                case "done":
                    if (!isTest) store.addAction(new JSONObject().put("taskId", taskId).put("at", at).put("action", "done"));
                    store.removeAlarm(id);
                    AlarmScheduler.cancel(ctx, id);
                    break;
                case "snooze10":
                case "snooze60": {
                    long until = System.currentTimeMillis() + ("snooze60".equals(action) ? 3600_000L : 600_000L);
                    JSONObject n = new JSONObject(a.toString());
                    n.put("id", taskId + "@" + until).put("at", until);
                    store.removeAlarm(id);
                    store.addExtra(n);
                    AlarmScheduler.scheduleOne(ctx, n.getString("id"), until);
                    if (!isTest) store.addAction(new JSONObject().put("taskId", taskId).put("at", at).put("action", "snooze").put("until", until));
                    break;
                }
                case "call":
                case "wa":
                case "wacall":
                    if (!isTest) store.addAction(new JSONObject().put("taskId", taskId).put("at", at).put("action", "stop"));
                    store.removeAlarm(id);
                    String phone = a.optString("phone");
                    if ("call".equals(action)) call(ctx, phone);
                    else if ("wa".equals(action)) openUrl(ctx, waUrl(phone, a.optString("msg")));
                    else if (!whatsappCall(ctx, phone)) openUrl(ctx, waUrl(phone, ""));
                    break;
                case "open":
                    if (!isTest) store.addAction(new JSONObject().put("taskId", taskId).put("at", at).put("action", "stop"));
                    store.removeAlarm(id);
                    Intent m = new Intent(ctx, MainActivity.class);
                    m.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    ctx.startActivity(m);
                    break;
                default: // "stop"
                    if (!isTest) store.addAction(new JSONObject().put("taskId", taskId).put("at", at).put("action", "stop"));
                    store.removeAlarm(id);
            }
        } catch (Exception ignored) { }
    }

    public static void stopRinging(Context ctx) {
        Intent s = new Intent(ctx, AlarmService.class);
        s.setAction(AlarmService.ACTION_STOP);
        try { ctx.startService(s); } catch (Exception e) { ctx.stopService(new Intent(ctx, AlarmService.class)); }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(AlarmService.NOTIF_ID);
    }

    static String digits(String p) { return p == null ? "" : p.replaceAll("[^0-9]", ""); }

    static String waUrl(String phone, String msg) {
        String d = digits(phone);
        if (d.length() == 10) d = "91" + d;
        String u = "https://wa.me/" + d;
        if (msg != null && !msg.isEmpty()) u += "?text=" + Uri.encode(msg);
        return u;
    }

    public static void openUrl(Context ctx, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    /** Direct call if allowed, otherwise the dialer with the number filled in. */
    public static void call(Context ctx, String phone) {
        String num = phone.startsWith("+") ? phone : "+91" + digits(phone);
        boolean direct = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        Intent i = new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + num));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    /** Starts a WhatsApp voice call to a saved contact. Returns false if not possible (then open the chat). */
    public static boolean whatsappCall(Context ctx, String phone) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false;
        String d = digits(phone);
        if (d.length() == 10) d = "91" + d;
        String[] mimes = { "vnd.android.cursor.item/vnd.com.whatsapp.voip.call", "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call" };
        for (String mime : mimes) {
            try (Cursor c = ctx.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ ContactsContract.Data._ID },
                    ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.Data.DATA1 + " LIKE ?",
                    new String[]{ mime, d + "@%" }, null)) {
                if (c != null && c.moveToFirst()) {
                    long dataId = c.getLong(0);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.parse("content://com.android.contacts/data/" + dataId), mime);
                    i.setPackage(mime.contains("w4b") ? "com.whatsapp.w4b" : "com.whatsapp");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    return true;
                }
            } catch (Exception ignored) { }
        }
        return false;
    }
}
