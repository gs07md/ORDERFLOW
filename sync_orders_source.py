"""
sync_orders.py
----------------
Value-FAS (Access .MDB) -> Orders.xlsx (OneDrive folder, opened on phone)

ZERO manual config needed for OneDrive: this script auto-detects your
OneDrive folder from Windows itself and creates a "Giriraj" folder inside
it with Orders.xlsx. Just double-click Run_Me.bat (see the other file) --
nothing to type or edit for that part.

Built from the REAL schema found in business_reports.py / reorder_pro.py:
  - DB lives at H:\\VALUE\\GAPnn\\GAPnn.MDB, one file per Indian fiscal year
    (April-March). GAP26 = FY starting Apr-2026. Auto-detects the current
    year's file the same way those scripts do.
  - Sale-order / Estimate lines are stored in InvMast (bill header) +
    InvTran (bill lines). This script tries Entry='EST' first; if that
    finds ZERO rows, it prints every distinct Entry value it can see in
    InvMast, so you can just read the answer off the screen and tell
    Claude which one is right -- no need to open Access yourself.

SETUP:
    Just double-click Run_Me.bat. It installs what's needed and runs this.
"""

import pyodbc
import openpyxl
from openpyxl.styles import Font, PatternFill, Border, Side, Alignment
from openpyxl.formatting.rule import FormulaRule
from openpyxl.utils import get_column_letter
from datetime import date, timedelta, datetime
from pathlib import Path
import os
import json
import urllib.request
import urllib.error

# =====================================================================
# CONFIG -- only touch ORDER_ENTRY_CODE if the script tells you to
# =====================================================================
DB_BASE = r"H:\VALUE"                # same as business_reports.py / reorder_pro.py
ORDER_ENTRY_CODE = "ORD"             # script self-checks this -- see fetch_new_orders()
LOOKBACK_DAYS = 0                    # 0 = ONLY today's orders (Entry stays 'ORD' forever, even after billing)
FIREBASE_API_KEY = "AIzaSyAWGy4fQTmqBmurJRcz1OiNuOeTNslGmxc"       # same 2 values as in OrderFlow.html
FIREBASE_PROJECT_ID = "giriraj-bills"
# =====================================================================

FONT = "Arial"
HEADER_FILL = PatternFill(start_color="2F3E52", end_color="2F3E52", fill_type="solid")
HEADER_FONT = Font(name=FONT, bold=True, color="FFFFFF", size=11)
STAFF_FILL = PatternFill(start_color="DCE6F1", end_color="DCE6F1", fill_type="solid")
LOCKED_FILL = PatternFill(start_color="F2F2F2", end_color="F2F2F2", fill_type="solid")
MISMATCH_FILL = PatternFill(start_color="FFC7CE", end_color="FFC7CE", fill_type="solid")
MISMATCH_FONT = Font(name=FONT, color="9C0006")
thin = Side(style="thin", color="B0B0B0")
BORDER = Border(left=thin, right=thin, top=thin, bottom=thin)
HEADERS = ["Sr", "Order No.", "Code", "Description", "Ordered Qty", "MRP/Rate",
           "Actual Qty", "Actual Rate", "Final Amount"]
HEADER_ROW = 3
FIRST_DATA_ROW = HEADER_ROW + 1


# ---------------------------------------------------------------------
# Auto-detect OneDrive -- no manual path needed
# ---------------------------------------------------------------------
def find_onedrive_folder():
    for var in ("OneDriveCommercial", "OneDriveConsumer", "OneDrive"):
        val = os.environ.get(var)
        if val and Path(val).exists():
            return Path(val)
    fallback = Path(os.path.expanduser("~")) / "Desktop"
    print(f"[warn] OneDrive not found on this PC -- using {fallback} instead. "
          f"Orders.xlsx won't sync to your phone until OneDrive is set up.")
    return fallback


def get_output_path():
    folder = find_onedrive_folder() / "Giriraj"
    folder.mkdir(parents=True, exist_ok=True)
    return str(folder / "Orders.xlsx")


# ---------------------------------------------------------------------
# Fiscal-year DB path (mirrors business_reports.py's _current_fy_year /
# _db_path_for_fy / _latest_existing_fy logic)
# ---------------------------------------------------------------------
def _current_fy_year(today=None):
    today = today or date.today()
    return today.year if today.month >= 4 else today.year - 1


def _db_path_for_fy(fy_year):
    folder = f"GAP{fy_year % 100:02d}"
    return str(Path(DB_BASE) / folder / f"{folder}.MDB")


def _latest_existing_fy(max_year=None):
    base = Path(DB_BASE)
    if not base.exists():
        return None
    best = None
    for entry in base.iterdir():
        if entry.is_dir() and entry.name.upper().startswith("GAP"):
            suffix = entry.name[3:]
            if suffix.isdigit():
                yr = 2000 + int(suffix)
                if max_year is None or yr <= max_year:
                    if best is None or yr > best:
                        best = yr
    return best


def resolve_db_path():
    fy = _current_fy_year()
    path = _db_path_for_fy(fy)
    if Path(path).exists():
        return path
    fallback_fy = _latest_existing_fy(max_year=fy)
    if fallback_fy is None:
        raise RuntimeError(f"Could not find {path} (or any earlier GAP*.MDB) under {DB_BASE}")
    return _db_path_for_fy(fallback_fy)


# ---------------------------------------------------------------------
# Pull pending orders from Value-FAS
# ---------------------------------------------------------------------
def fetch_new_orders():
    db_path = resolve_db_path()
    conn_str = (
        r"DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};"
        rf"DBQ={db_path};ReadOnly=1;"
    )
    conn = pyodbc.connect(conn_str)
    cur = conn.cursor()

    cutoff = date.today() - timedelta(days=LOOKBACK_DAYS)

    query = """
        SELECT m.BillC, m.Bill, t.ItemCode, im.ItemName, t.Qty, t.NetRate,
               ac.AcName, ac.City, m.AreaCode, m.BillDate
        FROM ((InvMast AS m
              INNER JOIN InvTran AS t ON m.BillC = t.BillC AND m.Bill = t.Bill)
              INNER JOIN ItemMast AS im ON t.ItemCode = im.ItemCode)
              LEFT JOIN Account AS ac ON m.AcCode = ac.AcCode
        WHERE m.Entry = ? AND m.Book = 'SAL' AND m.BillDate >= ?
        ORDER BY m.BillC, m.Bill, t.AutoNo
    """
    cur.execute(query, (ORDER_ENTRY_CODE, cutoff))
    rows = cur.fetchall()

    if not rows:
        cur.execute("SELECT COUNT(*) FROM InvMast WHERE Entry = ?", (ORDER_ENTRY_CODE,))
        total_for_code = cur.fetchone()[0]
        if total_for_code == 0:
            print(f"[info] No rows found with Entry='{ORDER_ENTRY_CODE}' at all.")
            print("[info] Distinct Entry values actually in InvMast:")
            cur.execute("SELECT DISTINCT Entry, COUNT(*) FROM InvMast GROUP BY Entry")
            for entry_val, count in cur.fetchall():
                print(f"       Entry = '{entry_val}'  ({count} bills)")
            print("[info] Tell Claude which one looks like pending sale-orders/estimates,")
            print("       then change ORDER_ENTRY_CODE near the top of this file to that value.")
        else:
            print(f"[info] Entry='{ORDER_ENTRY_CODE}' has {total_for_code} bills total, "
                  f"but none in the last {LOOKBACK_DAYS} day(s). That's normal if nothing new was ordered.")
            cur.execute("SELECT MAX(BillDate) FROM InvMast WHERE Entry = ?", (ORDER_ENTRY_CODE,))
            most_recent = cur.fetchone()[0]
            print(f"[info] Most recent Entry='{ORDER_ENTRY_CODE}' BillDate on file: {most_recent}")
            print(f"[info] Cutoff used for this run: {cutoff}")

    conn.close()
    def clean_bill(b):
        # Bill numbers come back as floats (3068.0) from Access -- show as plain ints
        try:
            return str(int(float(b)))
        except (TypeError, ValueError):
            return str(b)
    return [
        (f"{r[0]}{clean_bill(r[1])}", str(r[2]), str(r[3]), float(r[4] or 0), float(r[5] or 0),
         str(r[6]).strip() if r[6] else "Unknown Party", str(r[7]).strip() if r[7] else str(r[8] or "").strip(),
         r[9].date().isoformat() if r[9] else date.today().isoformat())
        for r in rows
    ]


def push_orders_to_firestore(new_orders):
    """Group flat (order_no, code, desc, qty, rate, party, location) rows
    into one Firestore doc per order, matching OrderFlow.html's schema
    exactly. Skipped silently if Firebase isn't configured yet."""
    if not FIREBASE_API_KEY:
        return
    grouped = {}
    party_of = {}
    location_of = {}
    date_of = {}
    seen_codes = {}
    for order_no, code, desc, qty, rate, party, location, billdate in new_orders:
        party_of[order_no] = party
        location_of[order_no] = location
        date_of[order_no] = billdate
        seen = seen_codes.setdefault(order_no, set())
        if code in seen:
            continue  # dedupe: same item code already added for this order
        seen.add(code)
        grouped.setdefault(order_no, []).append({
            "code": code, "desc": desc, "oqty": qty, "mrp": rate,
            "aqty": None, "arate": None,
        })

    # Track which orders we've already pushed in a LOCAL file, instead of
    # asking Firestore "does this exist?" every single cycle. At a 3-second
    # cycle that existence-check alone would burn through the free daily
    # read quota in minutes.
    known_path = Path(os.path.dirname(os.path.abspath(__file__))) / ".known_orders.json"
    try:
        known = set(json.loads(known_path.read_text())) if known_path.exists() else set()
    except Exception:
        known = set()

    base_url = f"https://firestore.googleapis.com/v1/projects/{FIREBASE_PROJECT_ID}/databases/(default)/documents/orders"
    pushed = 0
    for order_no, items in grouped.items():
        doc_id = f"vfs-{order_no}"
        if doc_id in known:
            continue  # already pushed earlier -- never re-check Firestore, never overwrite staff progress

        order = {
            "id": doc_id,
            "orderNo": order_no,
            "party": party_of.get(order_no, "Unknown Party"),
            "location": location_of.get(order_no, ""),
            "date": date_of.get(order_no, date.today().isoformat()),
            "status": "pending",
            "items": items,
        }
        body = json.dumps({"fields": {"json": {"stringValue": json.dumps(order)}}}).encode("utf-8")
        url = f"{base_url}/{doc_id}?key={FIREBASE_API_KEY}"
        req = urllib.request.Request(url, data=body, method="PATCH",
                                      headers={"Content-Type": "application/json"})
        try:
            urllib.request.urlopen(req, timeout=10)
            pushed += 1
            known.add(doc_id)
        except urllib.error.URLError as e:
            print(f"[warn] Firestore push failed for {order_no}: {e}")
    known_path.write_text(json.dumps(list(known)))
    if pushed:
        print(f"[info] Pushed {pushed} NEW order(s) to Firestore -> phones will see them within 15 sec.")
    else:
        print("[info] No new orders to push (all already synced).")





def push_stock_to_firestore():
    """Push ItemMast stock (Qty, rack 'location' from ItemCtg, rate) to
    Firestore -- but ONLY items whose Qty/Rate/Location actually changed
    since the last run, and at most MAX_STOCK_PUSH_PER_RUN per call so a
    huge catalog's first-time sync doesn't block order syncing (which
    needs to run every few seconds)."""
    MAX_STOCK_PUSH_PER_RUN = 300

    snapshot_path = Path(os.path.dirname(os.path.abspath(__file__))) / ".stock_snapshot.json"
    try:
        snapshot = json.loads(snapshot_path.read_text()) if snapshot_path.exists() else {}
    except Exception:
        snapshot = {}

    db_path = resolve_db_path()
    conn_str = (
        r"DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};"
        rf"DBQ={db_path};ReadOnly=1;"
    )
    conn = pyodbc.connect(conn_str)
    cur = conn.cursor()
    cur.execute("""
        SELECT im.ItemCode, im.ItemName, im.SitQty, im.MinQty, im.SRate, ic.ICtgName
        FROM ItemMast AS im
        LEFT JOIN ItemCtg AS ic ON im.ICtgCode = ic.ICtgCode
        WHERE im.NotShow <> 'Y'
    """)
    rows = cur.fetchall()
    conn.close()

    base_url = f"https://firestore.googleapis.com/v1/projects/{FIREBASE_PROJECT_ID}/databases/(default)/documents/stock"
    pushed = 0
    checked = 0
    for r in rows:
        if pushed >= MAX_STOCK_PUSH_PER_RUN:
            break  # leave the rest for the next cycle (a few seconds later) -- keeps order sync snappy
        item_code, item_name, sit_qty, min_qty, rate, loc_name = r
        if not item_code:
            continue
        checked += 1
        code = str(item_code).strip()
        doc_id = code.replace("/", "_")
        item = {
            "code": code,
            "name": str(item_name or "").strip(),
            "qty": float(sit_qty or 0),
            "minQty": float(min_qty or 0),
            "rate": float(rate or 0),
            "location": str(loc_name).strip() if loc_name else "",
        }
        # compare against last known snapshot -- skip the write entirely
        # if nothing actually changed for this item
        prev = snapshot.get(doc_id)
        if prev == item:
            continue

        item_with_ts = dict(item, updated=datetime.now().isoformat())
        body = json.dumps({"fields": {"json": {"stringValue": json.dumps(item_with_ts)}}}).encode("utf-8")
        req = urllib.request.Request(f"{base_url}/{doc_id}?key={FIREBASE_API_KEY}", data=body,
                                      method="PATCH", headers={"Content-Type": "application/json"})
        try:
            urllib.request.urlopen(req, timeout=10)
            pushed += 1
            snapshot[doc_id] = item  # remember what we just pushed (without timestamp)
        except urllib.error.URLError as e:
            print(f"[warn] Stock push failed for {item_code}: {e}")

    snapshot_path.write_text(json.dumps(snapshot))
    print(f"[info] Stock sync: {pushed} changed item(s) pushed out of {checked} checked.")


def style_data_row(ws, r):
    for col in range(1, 10):
        cell = ws.cell(row=r, column=col)
        cell.font = Font(name=FONT, size=10)
        cell.border = BORDER
        if col in (1, 2, 3, 5, 6):
            cell.fill = LOCKED_FILL
        elif col in (7, 8):
            cell.fill = STAFF_FILL
        if col in (5, 7):
            cell.alignment = Alignment(horizontal="center")
        if col in (6, 8, 9):
            cell.number_format = '#,##0.00'


def load_or_create_workbook(output_path):
    if os.path.exists(output_path):
        wb = openpyxl.load_workbook(output_path)
        ws = wb["Orders"]
    else:
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.title = "Orders"
        ws.merge_cells("A1:I1")
        ws["A1"] = "Order Entry -> Packing Check -> Bill  |  Grey = from Value-FAS  |  Blue = staff fills  |  Red row = mismatch"
        ws["A1"].font = Font(name=FONT, italic=True, size=9, color="555555")
        ws.append([])
        ws.append(HEADERS)
        for col_idx, h in enumerate(HEADERS, start=1):
            c = ws.cell(row=HEADER_ROW, column=col_idx, value=h)
            c.font = HEADER_FONT
            c.fill = HEADER_FILL
            c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
            c.border = BORDER
        widths = {1: 5, 2: 10, 3: 11, 4: 34, 5: 11, 6: 10, 7: 11, 8: 11, 9: 13}
        for col, w in widths.items():
            ws.column_dimensions[get_column_letter(col)].width = w
        ws.freeze_panes = f"A{FIRST_DATA_ROW}"
    return wb, ws


def existing_keys(ws, last_row):
    keys = set()
    for r in range(FIRST_DATA_ROW, last_row + 1):
        order_no = ws.cell(row=r, column=2).value
        code = ws.cell(row=r, column=3).value
        if order_no and code:
            keys.add((str(order_no), str(code)))
    return keys


def main():
    print("[version] sync_orders.py v6 (real BillDate stored, not sync-run date)")
    output_path = get_output_path()
    print(f"[info] Orders.xlsx will be saved to: {output_path}")

    new_orders = fetch_new_orders()
    push_orders_to_firestore(new_orders)
    push_stock_to_firestore()
    wb, ws = load_or_create_workbook(output_path)

    last_row = HEADER_ROW
    r = FIRST_DATA_ROW
    while ws.cell(row=r, column=1).value not in (None, ""):
        last_row = r
        r += 1

    known = existing_keys(ws, last_row)
    sr = last_row - HEADER_ROW

    added = 0
    for order_no, code, desc, qty, rate, party, location, billdate in new_orders:
        if (order_no, code) in known:
            continue
        last_row += 1
        sr += 1
        ws.cell(row=last_row, column=1, value=sr)
        ws.cell(row=last_row, column=2, value=order_no)
        ws.cell(row=last_row, column=3, value=code)
        ws.cell(row=last_row, column=4, value=desc)
        ws.cell(row=last_row, column=5, value=qty)
        ws.cell(row=last_row, column=6, value=rate)
        ws.cell(row=last_row, column=7, value=f"=E{last_row}")
        ws.cell(row=last_row, column=8, value=f"=F{last_row}")
        ws.cell(row=last_row, column=9, value=f"=G{last_row}*H{last_row}")
        style_data_row(ws, last_row)
        added += 1

    if added:
        ws.conditional_formatting._cf_rules.clear()
        rng = f"A{FIRST_DATA_ROW}:I{last_row}"
        formula = f"OR($E{FIRST_DATA_ROW}<>$G{FIRST_DATA_ROW},$F{FIRST_DATA_ROW}<>$H{FIRST_DATA_ROW})"
        ws.conditional_formatting.add(rng, FormulaRule(formula=[formula], fill=MISMATCH_FILL, font=MISMATCH_FONT))

        total_row = last_row + 2
        ws.cell(row=total_row, column=8, value="Total").font = Font(name=FONT, bold=True)
        ws.cell(row=total_row, column=9, value=f"=SUM(I{FIRST_DATA_ROW}:I{last_row})").font = Font(name=FONT, bold=True)
        ws.cell(row=total_row, column=9).number_format = '#,##0.00'

        wb.save(output_path)
        print(f"[done] Added {added} new order line(s) to {output_path}")
    else:
        print("[done] No new orders found -- Orders.xlsx already up to date (or check the Entry codes printed above).")


if __name__ == "__main__":
    main()
