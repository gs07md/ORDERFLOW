package com.giriraj.todolist;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads bank / UPI alert SMS from the inbox (on the phone only) so the app can turn them into expenses. */
public class BankSms {
    private static final Pattern MONEY = Pattern.compile("(?i)(rs\\.?|inr|₹)\\s*:?\\s*[\\d,]+|\\b(debited|credited)\\s+(by|for|with)\\s+[\\d,]+");
    private static final Pattern WORDS = Pattern.compile("(?i)\\b(debit|debited|credit|credited|spent|sent|paid|withdrawn|withdrawal|purchase|txn|transferred|received|deposited|refund)");

    public static JSONArray read(Context ctx, long since, int max) {
        JSONArray out = new JSONArray();
        String[] p = { "_id", "address", "body", "date" };
        try (Cursor c = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"), p, "date > ?", new String[] { String.valueOf(since) }, "date DESC")) {
            int scanned = 0;
            while (c != null && c.moveToNext() && scanned < 3000 && out.length() < max) {
                scanned++;
                String addr = c.getString(1), body = c.getString(2);
                if (addr == null || body == null) continue;
                if (!addr.matches(".*[A-Za-z].*")) continue; // banks send from names like VM-HDFCBK, not phone numbers
                if (!MONEY.matcher(body).find() || !WORDS.matcher(body).find()) continue;
                JSONObject o = new JSONObject();
                o.put("id", c.getString(0)); o.put("from", addr); o.put("body", body.length() > 600 ? body.substring(0, 600) : body); o.put("date", c.getLong(3));
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out;
    }
}
