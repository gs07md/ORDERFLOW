package com.giriraj.todolist;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small persistent store for alarms, pending button actions and alarm settings. */
public class AlarmStore {
    private static final String PREFS = "todo_alarms";
    private final SharedPreferences sp;

    public AlarmStore(Context ctx) { sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public JSONArray getAlarms() { return arr("alarms"); }
    public void setAlarms(JSONArray a) { sp.edit().putString("alarms", a.toString()).apply(); }

    public JSONObject findAlarm(String id) {
        JSONArray a = getAlarms();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        JSONArray x = arr("extra");
        for (int i = 0; i < x.length(); i++) {
            JSONObject o = x.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    public void removeAlarm(String id) {
        sp.edit().putString("alarms", without(getAlarms(), id).toString()).putString("extra", without(arr("extra"), id).toString()).apply();
    }

    /** One-off alarms made on the phone itself (snooze, test) until the app re-syncs. */
    public void addExtra(JSONObject o) {
        JSONArray x = arr("extra");
        JSONArray keep = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < x.length(); i++) {
            JSONObject e = x.optJSONObject(i);
            if (e != null && e.optLong("at") > now - 3600_000L) keep.put(e);
        }
        keep.put(o);
        sp.edit().putString("extra", keep.toString()).apply();
    }
    public JSONArray getExtras() { return arr("extra"); }
    public void clearExtras() { sp.edit().putString("extra", "[]").apply(); }

    public synchronized void addAction(JSONObject o) {
        JSONArray a = arr("actions");
        a.put(o);
        sp.edit().putString("actions", a.toString()).commit();
    }
    public synchronized JSONArray takeActions() {
        JSONArray a = arr("actions");
        sp.edit().putString("actions", "[]").commit();
        return a;
    }

    public String toneUri() { return sp.getString("toneUri", ""); }
    public String toneTitle() { return sp.getString("toneTitle", ""); }
    public void setTone(String uri, String title) { sp.edit().putString("toneUri", uri).putString("toneTitle", title).apply(); }
    public int ringMins() { return sp.getInt("ringMins", 3); }
    public boolean vibrate() { return sp.getBoolean("vibrate", true); }
    public void setPrefs(int ringMins, boolean vibrate) { sp.edit().putInt("ringMins", ringMins).putBoolean("vibrate", vibrate).apply(); }
    /** "notify" = normal notification, "alarm" = ringing full-screen alarm */
    public String style() { return sp.getString("style", "notify"); }
    public void setStyle(String s) { sp.edit().putString("style", "alarm".equals(s) ? "alarm" : "notify").apply(); }

    public java.util.Set<String> scheduledIds() { return new java.util.HashSet<>(sp.getStringSet("scheduled", new java.util.HashSet<>())); }
    public void setScheduledIds(java.util.Set<String> ids) { sp.edit().putStringSet("scheduled", ids).apply(); }

    private JSONArray arr(String key) {
        try { return new JSONArray(sp.getString(key, "[]")); } catch (JSONException e) { return new JSONArray(); }
    }
    private static JSONArray without(JSONArray a, String id) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) out.put(o);
        }
        return out;
    }
}
