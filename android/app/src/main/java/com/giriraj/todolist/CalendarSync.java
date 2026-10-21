package com.giriraj.todolist;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Puts the app's tasks into a phone calendar (e.g. Google Calendar) and reads that calendar's events back. */
public class CalendarSync {
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("todo_cal", Context.MODE_PRIVATE); }
    private static JSONObject map(Context c) { try { return new JSONObject(sp(c).getString("map", "{}")); } catch (Exception e) { return new JSONObject(); } }
    private static void saveMap(Context c, JSONObject m, long calId) { sp(c).edit().putString("map", m.toString()).putLong("cal", calId).apply(); }

    /** Calendars the user can write to. */
    public static JSONArray calendars(Context ctx) {
        JSONArray out = new JSONArray();
        String[] p = { CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.IS_PRIMARY };
        String sel = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=" + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR + " AND " + CalendarContract.Calendars.VISIBLE + "=1";
        try (Cursor c = ctx.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, p, sel, null, null)) {
            while (c != null && c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0)); o.put("name", c.getString(1)); o.put("account", c.getString(2));
                o.put("type", c.getString(3)); o.put("color", String.format("#%06X", 0xFFFFFF & c.getInt(4)));
                o.put("primary", c.getInt(5) == 1);
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static long utcMidnight(long localMs) {
        Calendar l = Calendar.getInstance(); l.setTimeInMillis(localMs);
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC")); u.clear();
        u.set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return u.getTimeInMillis();
    }

    private static ContentValues values(long calId, JSONObject t) {
        ContentValues v = new ContentValues();
        long start = t.optLong("start");
        boolean allDay = t.optBoolean("allDay");
        v.put(CalendarContract.Events.CALENDAR_ID, calId);
        v.put(CalendarContract.Events.TITLE, t.optString("title"));
        String note = t.optString("note");
        v.put(CalendarContract.Events.DESCRIPTION, (note.isEmpty() ? "" : note + "\n\n") + "From To Do List");
        if (allDay) {
            long d = utcMidnight(start);
            v.put(CalendarContract.Events.DTSTART, d); v.put(CalendarContract.Events.DTEND, d + 86400000L);
            v.put(CalendarContract.Events.ALL_DAY, 1); v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
        } else {
            v.put(CalendarContract.Events.DTSTART, start); v.put(CalendarContract.Events.DTEND, start + 30 * 60000L);
            v.put(CalendarContract.Events.ALL_DAY, 0); v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        }
        return v;
    }

    private static void delete(ContentResolver cr, long ev) {
        try { cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev), null, null); } catch (Exception ignored) { }
    }

    /** Make the calendar match the list: add new tasks, update changed ones, remove finished/deleted ones. */
    public static int sync(Context ctx, long calId, JSONArray tasks) {
        ContentResolver cr = ctx.getContentResolver();
        JSONObject m = map(ctx);
        if (sp(ctx).getLong("cal", -1) != calId) { // calendar changed or turned off: remove what we added before
            for (Iterator<String> it = m.keys(); it.hasNext(); ) delete(cr, m.optJSONObject(it.next()).optLong("ev"));
            m = new JSONObject();
        }
        if (calId <= 0) { saveMap(ctx, new JSONObject(), -1); return 0; }
        Set<String> keep = new HashSet<>();
        int n = 0;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.optJSONObject(i);
            if (t == null || t.optString("id").isEmpty() || t.optLong("start") <= 0) continue;
            String id = t.optString("id"), h = t.optString("title") + "|" + t.optLong("start") + "|" + t.optBoolean("allDay") + "|" + t.optString("note");
            keep.add(id);
            JSONObject e = m.optJSONObject(id);
            try {
                if (e != null && h.equals(e.optString("h"))) { n++; continue; }
                ContentValues v = values(calId, t);
                long ev = -1;
                if (e != null && cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.optLong("ev")), v, null, null) > 0) ev = e.optLong("ev");
                if (ev < 0) { Uri u = cr.insert(CalendarContract.Events.CONTENT_URI, v); if (u != null) ev = Long.parseLong(u.getLastPathSegment()); }
                if (ev > 0) { m.put(id, new JSONObject().put("ev", ev).put("h", h)); n++; }
            } catch (Exception ignored) { }
        }
        JSONArray gone = new JSONArray();
        for (Iterator<String> it = m.keys(); it.hasNext(); ) { String k = it.next(); if (!keep.contains(k)) gone.put(k); }
        for (int i = 0; i < gone.length(); i++) { String k = gone.optString(i); delete(cr, m.optJSONObject(k).optLong("ev")); m.remove(k); }
        saveMap(ctx, m, calId);
        return n;
    }

    /** Events between from and to (all visible calendars), leaving out the ones this app created. */
    public static JSONArray events(Context ctx, long from, long to) {
        JSONArray out = new JSONArray();
        Set<Long> mine = new HashSet<>();
        JSONObject m = map(ctx);
        for (Iterator<String> it = m.keys(); it.hasNext(); ) mine.add(m.optJSONObject(it.next()).optLong("ev"));
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, from); ContentUris.appendId(b, to);
        String[] p = { CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME, CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.EVENT_LOCATION };
        SimpleDateFormat utcDay = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH); utcDay.setTimeZone(TimeZone.getTimeZone("UTC"));
        try (Cursor c = ctx.getContentResolver().query(b.build(), p, CalendarContract.Instances.VISIBLE + "=1", null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext() && out.length() < 600) {
                if (mine.contains(c.getLong(0))) continue;
                JSONObject o = new JSONObject();
                boolean allDay = c.getInt(4) == 1;
                o.put("id", c.getLong(0) + "@" + c.getLong(2)); o.put("title", c.getString(1) == null ? "(No title)" : c.getString(1));
                o.put("begin", c.getLong(2)); o.put("end", c.getLong(3)); o.put("allDay", allDay);
                if (allDay) { o.put("day", utcDay.format(c.getLong(2))); o.put("endDay", utcDay.format(c.getLong(3) - 1)); }
                o.put("cal", c.getString(5)); o.put("color", String.format("#%06X", 0xFFFFFF & c.getInt(6)));
                String loc = c.getString(7); if (loc != null && !loc.isEmpty()) o.put("where", loc);
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out;
    }
}
