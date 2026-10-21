package com.giriraj.todolist;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Settings;
import androidx.activity.result.ActivityResult;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bridge from the web app: alarms, ringtone, contacts, calls and WhatsApp. */
@CapacitorPlugin(name = "TodoAlarm")
public class TodoAlarmPlugin extends Plugin {

    private AlarmStore store() { return new AlarmStore(getContext()); }

    @PluginMethod
    public void sync(PluginCall call) {
        try {
            JSArray alarms = call.getArray("alarms", new JSArray());
            JSONArray clean = new JSONArray();
            for (int i = 0; i < alarms.length(); i++) {
                JSONObject o = alarms.optJSONObject(i);
                if (o != null && o.optLong("at") > 0 && !o.optString("id").isEmpty()) clean.put(o);
            }
            AlarmStore s = store();
            s.setAlarms(clean);
            s.clearExtras(); // snoozes made on the phone are now part of the app's own list
            s.setPrefs(call.getInt("ringMins", 3), call.getBoolean("vibrate", true));
            int n = AlarmScheduler.scheduleAll(getContext());
            JSObject r = new JSObject(); r.put("count", n); call.resolve(r);
        } catch (Exception e) { call.reject("sync failed: " + e.getMessage()); }
    }

    /** Data for the home-screen widget: today's open tasks and today's spend. */
    @PluginMethod
    public void widget(PluginCall call) {
        try {
            TodoWidget.save(getContext(), call.getData().toString());
            call.resolve();
        } catch (Exception e) { call.reject("widget failed: " + e.getMessage()); }
    }

    @PluginMethod
    public void takeActions(PluginCall call) {
        JSObject r = new JSObject();
        try { r.put("actions", new JSArray(store().takeActions().toString())); } catch (Exception e) { r.put("actions", new JSArray()); }
        call.resolve(r);
    }

    private boolean notificationsOk() { return NotificationManagerCompat.from(getContext()).areNotificationsEnabled(); }
    private boolean fullScreenOk() {
        if (Build.VERSION.SDK_INT < 34) return true;
        return ((NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE)).canUseFullScreenIntent();
    }

    @PluginMethod
    public void getSettings(PluginCall call) {
        JSObject r = new JSObject();
        AlarmStore s = store();
        String title = s.toneTitle();
        if (title.isEmpty()) title = "Phone default alarm";
        r.put("toneTitle", title);
        r.put("canExact", AlarmScheduler.canExact(getContext()));
        r.put("notifications", notificationsOk());
        r.put("fullScreen", fullScreenOk());
        r.put("callDirect", ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED);
        call.resolve(r);
    }

    /** Asks for whatever is still missing, one screen at a time. */
    @PluginMethod
    public void ensurePermissions(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{ Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS }, 7001);
            } else if (!notificationsOk()) {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
                getActivity().startActivity(i);
            } else if (!AlarmScheduler.canExact(getContext()) && Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getContext().getPackageName()));
                getActivity().startActivity(i);
            } else if (!fullScreenOk()) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getContext().getPackageName()));
                getActivity().startActivity(i);
            } else if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{ Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS }, 7002);
            }
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void pickRingtone(PluginCall call) {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        String cur = store().toneUri();
        if (!cur.isEmpty()) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur));
        startActivityForResult(call, i, "onRingtonePicked");
    }

    @ActivityCallback
    private void onRingtonePicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        Uri uri = data == null ? null : data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) { call.resolve(); return; }
        String title = "Alarm sound";
        try { Ringtone r = RingtoneManager.getRingtone(getContext(), uri); if (r != null) title = r.getTitle(getContext()); } catch (Exception ignored) { }
        store().setTone(uri.toString(), title);
        JSObject r = new JSObject(); r.put("title", title); call.resolve(r);
    }

    @PluginMethod
    public void test(PluginCall call) {
        try {
            long at = System.currentTimeMillis() + call.getInt("seconds", 10) * 1000L;
            JSONObject a = new JSONObject();
            a.put("id", "test@" + at).put("taskId", "test").put("at", at).put("title", "Test alarm").put("body", "This is how your tasks will ring");
            store().addExtra(a);
            AlarmScheduler.scheduleOne(getContext(), a.getString("id"), at);
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void call(PluginCall call) {
        String phone = call.getString("phone", "");
        if (phone.isEmpty()) { call.reject("no number"); return; }
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{ Manifest.permission.CALL_PHONE }, 7003);
        }
        Actions.call(getContext(), phone);
        call.resolve();
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        try { Actions.openUrl(getContext(), call.getString("url", "")); call.resolve(); }
        catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void whatsappCall(PluginCall call) {
        JSObject r = new JSObject();
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{ Manifest.permission.READ_CONTACTS }, 7004);
            r.put("ok", false); r.put("reason", "permission"); call.resolve(r); return;
        }
        r.put("ok", Actions.whatsappCall(getContext(), call.getString("phone", "")));
        call.resolve(r);
    }

    @PluginMethod
    public void pickContact(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(call, i, "onContactPicked");
    }

    @ActivityCallback
    private void onContactPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (data == null || data.getData() == null) { call.resolve(); return; }
        try (Cursor c = getContext().getContentResolver().query(data.getData(),
                new String[]{ ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME }, null, null, null)) {
            JSObject r = new JSObject();
            if (c != null && c.moveToFirst()) { r.put("phone", c.getString(0)); r.put("name", c.getString(1)); }
            call.resolve(r);
        } catch (Exception e) { call.reject("could not read contact"); }
    }
}
