package com.giriraj.todolist;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widget: the shopping list, tap an item to tick it off. */
public class ListWidget extends AppWidgetProvider {
    static final String ACTION = "com.giriraj.todolist.ITEM_TICK";
    static final int[] ROW = {R.id.l_row0, R.id.l_row1, R.id.l_row2, R.id.l_row3, R.id.l_row4, R.id.l_row5, R.id.l_row6, R.id.l_row7}, CHK = {R.id.l_chk0, R.id.l_chk1, R.id.l_chk2, R.id.l_chk3, R.id.l_chk4, R.id.l_chk5, R.id.l_chk6, R.id.l_chk7}, TTL = {R.id.l_ttl0, R.id.l_ttl1, R.id.l_ttl2, R.id.l_ttl3, R.id.l_ttl4, R.id.l_ttl5, R.id.l_ttl6, R.id.l_ttl7}, TIM = {R.id.l_tim0, R.id.l_tim1, R.id.l_tim2, R.id.l_tim3, R.id.l_tim4, R.id.l_tim5, R.id.l_tim6, R.id.l_tim7};

    @Override public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, build(ctx)); }

    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (!ACTION.equals(intent.getAction())) return;
        String list = intent.getStringExtra("list"), item = intent.getStringExtra("item");
        if (list == null || item == null) return;
        JSONObject d = TodoWidget.load(ctx);
        JSONObject l = d.optJSONObject("list");
        if (l != null && list.equals(l.optString("id"))) {
            JSONArray it = l.optJSONArray("items"), keep = new JSONArray();
            if (it != null) for (int i = 0; i < it.length(); i++) { JSONObject o = it.optJSONObject(i); if (o != null && !item.equals(o.optString("id"))) keep.put(o); }
            try { l.put("items", keep); l.put("done", l.optInt("done") + 1); } catch (Exception ignored) { }
        }
        try { new AlarmStore(ctx).addAction(new JSONObject().put("action", "item").put("list", list).put("item", item)); } catch (Exception ignored) { }
        TodoWidget.store(ctx, d);
        TodoWidget.updateAll(ctx);
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.list_widget);
        JSONObject l = TodoWidget.load(ctx).optJSONObject("list");
        JSONArray it = l == null ? null : l.optJSONArray("items");
        int n = it == null ? 0 : it.length();
        v.setTextViewText(R.id.l_title, l == null ? "Shopping list" : "🛒 " + l.optString("name", "List"));
        v.setTextViewText(R.id.l_sum, l == null ? "" : n == 0 ? (l.optInt("done") > 0 ? "All done ✓" : "Empty") : n + " to get");
        String lid = l == null ? "" : l.optString("id");
        for (int r = 0; r < ROW.length; r++) {
            if (r >= n) { v.setViewVisibility(ROW[r], View.GONE); continue; }
            JSONObject o = it.optJSONObject(r);
            v.setViewVisibility(ROW[r], View.VISIBLE);
            v.setTextViewText(TTL[r], o.optString("t"));
            v.setTextViewText(TIM[r], "");
            Intent t = new Intent(ctx, ListWidget.class).setAction(ACTION).putExtra("list", lid).putExtra("item", o.optString("id"));
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 400 + r, t, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(CHK[r], pi);
            v.setOnClickPendingIntent(TTL[r], pi);
        }
        int more = n - ROW.length;
        v.setViewVisibility(R.id.l_more, more > 0 ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.l_more, "+" + more + " more");
        v.setViewVisibility(R.id.l_empty, n == 0 ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.l_empty, l == null ? "Make a list in the app (Lists button at the top)" : "Nothing to buy 🎉");
        String what = l == null ? "lists" : "list:" + lid;
        v.setOnClickPendingIntent(R.id.l_head, TodoWidget.openApp(ctx, what, 41));
        v.setOnClickPendingIntent(R.id.l_btn, TodoWidget.openApp(ctx, what, 42));
        v.setOnClickPendingIntent(R.id.l_empty, TodoWidget.openApp(ctx, what, 43));
        v.setOnClickPendingIntent(R.id.l_more, TodoWidget.openApp(ctx, what, 44));
        return v;
    }
}
