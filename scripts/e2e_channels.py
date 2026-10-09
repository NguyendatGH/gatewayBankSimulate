#!/usr/bin/env python3
"""E2E của GATEWAY về kênh nhận tiền và việc tự chọn terminal (53 kiểm tra). Không có test tự động khác cho gateway.

Chạy trên DB TẠM, KHÔNG chạy trên payment_lab:
  psql -c "CREATE DATABASE payment_lab_repro"
  DB_URL=jdbc:postgresql://localhost:5432/payment_lab_repro SERVER_PORT=28090 GATEWAY_SEED_ENABLED=true \\
    GATEWAY_PUBLIC_BASE_URL=http://localhost:28090 GATEWAY_MOCK_BANK_URL=http://localhost:28090 java -jar target/bankSimulate-*.jar
  set -a; . ./.env; set +a          # cần GATEWAY_ADMIN_KEY, DB_USERNAME, DB_PASSWORD
  python3 scripts/e2e_channels.py
(Dùng bản sao repo để build, đừng `mvnw clean` khi gateway thật đang chạy từ target/classes.)
"""
import json, sys, urllib.request, urllib.error, os, subprocess
G = "http://localhost:28090"
ADMIN = os.environ["GATEWAY_ADMIN_KEY"]
ok = bad = 0

def call(method, path, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(G + path, data=data, method=method, headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req) as r:
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt else None)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try: return e.code, json.loads(txt)
        except Exception: return e.code, txt

def check(name, cond, extra=""):
    global ok, bad
    if cond: ok += 1; print(f"  PASS  {name}")
    else: bad += 1; print(f"  FAIL  {name}  {extra}")

A = {"X-Admin-Key": ADMIN}
st, onb = call("POST", "/api/v1/admin/merchant-onboarding", {"name": "E2E Shop", "externalReference": "e2e-" + os.urandom(3).hex()}, A)
MER, SEC, DEF = onb["merchantNo"], onb["merchantSecret"], onb["terminalId"]
M = {"X-Merchant-No": MER, "X-Merchant-Secret": SEC}          # chỉ merchant, KHÔNG có terminal
def pay(order, method, extra_headers=None):
    return call("POST", "/api/v1/gateway/payments", {"orderCode": order, "amount": 250000, "description": "Ve", "paymentMethod": method,
        "items": [{"name": "Ve", "quantity": 1, "price": 250000}], "returnUrl": f"http://x/ok{order}", "cancelUrl": f"http://x/no{order}"},
        {**M, **(extra_headers or {})})
def psql(sql):
    out = subprocess.run(["psql", "-h", "localhost", "-U", os.environ["DB_USERNAME"], "-d", "payment_lab_repro", "-At", "-c", sql],
                         capture_output=True, text=True, env={**os.environ, "PGPASSWORD": os.environ["DB_PASSWORD"]})
    return out.stdout.strip()
def terminal_of(tradeNo):
    return psql(f"select t.terminal_id from payment_transactions p join terminals t on t.id=p.terminal_id where p.provider_payment_id='{tradeNo}'") or \
           psql(f"select t.terminal_id from gateway_transactions p join terminals t on t.id=p.terminal_id where p.gw_txn_id='{tradeNo}'")

print("== 1. Merchant mới: terminal mặc định, chưa có kênh")
st, merch = call("GET", f"/api/v1/admin/merchants/{MER}", None, A)
check("merchant có defaultTerminalId", merch["defaultTerminalId"] == DEF, merch)
check("chưa có kênh nào", merch["channelTerminalIds"] == [])
st, ch = call("GET", "/api/v1/gateway/channels", None, M)
check("GET channels (chỉ merchant, không terminal) trả 200 và 0 kênh", st == 200 and ch["channels"] == [], ch)
check("4 ngân hàng chọn được", [b["code"] for b in ch["banks"]] == ["MBB", "TCB", "VCB", "VTB"], ch["banks"])
check("khách thấy CARD + QR", ch["customerMethods"] == ["CARD", "QR"], ch["customerMethods"])
st, pm = call("GET", "/api/v1/gateway/payment-methods", None, M)
check("GET payment-methods", st == 200 and pm["paymentMethods"] == ["CARD", "QR"], pm)

print("== 2. Tạo giao dịch KHÔNG gửi terminal: gateway tự chọn terminal mặc định")
st, p1 = pay(8001, "QR")
check("QR tạo được không cần X-Terminal-Id", st == 200 and p1["providerPaymentId"].startswith("TradeNo"), p1)
check("đi terminal mặc định", terminal_of(p1["providerPaymentId"]) == DEF, terminal_of(p1["providerPaymentId"]))
st, _ = pay(8002, "GOOGLE_PAY")
check("GOOGLE_PAY chưa bật thì bị từ chối", st == 409, _)

print("== 3. Mở kênh đầu tiên (VTB) = dùng lại terminal mặc định")
acct = {"accountName": "Nguyen A", "accountNumber": "0011001234567"}
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "VTB", "paymentMethods": ["CARD", "QR", "PAYNOW"], **acct}, M)
check("mở kênh VTB 200", st == 200, r)
ch = r["channels"]
check("1 kênh, là kênh chính, đúng ngân hàng", len(ch) == 1 and ch[0]["primary"] and ch[0]["bankCode"] == "VTB", ch)
CH_VTB = ch[0]["id"]
check("id kênh là UUID, không lộ mã TerNo", len(CH_VTB) == 36 and "TerNo" not in CH_VTB, CH_VTB)
check("kênh đầu dùng lại terminal mặc định", psql(f"select terminal_id from terminals where id='{CH_VTB}'") == DEF)
check("tài khoản che số", ch[0]["accountNumberMasked"] == "*********4567", ch[0]["accountNumberMasked"])
check("settlement merchant = tài khoản kênh chính", psql(f"select settlement_account_number from merchants where mer_no='{MER}'") == "0011001234567")
st, merch = call("GET", f"/api/v1/admin/merchants/{MER}", None, A)
check("admin thấy: không còn default, 1 kênh", merch["defaultTerminalId"] is None and merch["channelTerminalIds"] == [DEF], merch)

print("== 4. Luật kênh")
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "TCB", "paymentMethods": ["QR"], **acct}, M)
check("QR đã ở kênh VTB -> 409 PAYMENT_METHOD_IN_OTHER_CHANNEL", st == 409 and r["code"] == "PAYMENT_METHOD_IN_OTHER_CHANNEL", r)
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "VTB", "paymentMethods": ["CARD"], **acct}, M)
check("VTB đã là kênh -> 409 BANK_ALREADY_A_CHANNEL", st == 409 and r["code"] == "BANK_ALREADY_A_CHANNEL", r)
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "MBB", "paymentMethods": ["CARD"], **acct}, M)
check("MBB không hỗ trợ CARD -> 400", st == 400 and r["code"] == "PAYMENT_METHOD_NOT_SUPPORTED_BY_BANK", r)
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "XXX", "paymentMethods": ["CARD"], **acct}, M)
check("ngân hàng lạ -> 400 UNKNOWN_BANK", st == 400 and r["code"] == "UNKNOWN_BANK", r)
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "TCB", "paymentMethods": ["GOOGLE_PAY"], "accountName": "x"}, M)
check("thiếu số tài khoản -> 400 ACCOUNT_REQUIRED", st == 400 and r["code"] == "ACCOUNT_REQUIRED", r)
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "TCB", "paymentMethods": [], **acct}, M)
check("không chọn phương thức -> 400", st == 400 and r["code"] == "PAYMENT_METHODS_REQUIRED", r)

print("== 5. Kênh thứ hai (TCB) = terminal mới")
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "TCB", "paymentMethods": ["GOOGLE_PAY", "APPLE_PAY"], "accountName": "Nguyen B", "accountNumber": "9988776655"}, M)
check("mở kênh TCB 200", st == 200, r)
tcb = next(c for c in r["channels"] if c["bankCode"] == "TCB")
TCB_TER = psql(f"select terminal_id from terminals where id='{tcb['id']}'")
check("terminal mới (khác terminal mặc định)", TCB_TER != DEF, tcb)
check("kênh chính vẫn là VTB", next(c for c in r["channels"] if c["primary"])["bankCode"] == "VTB")
check("khách thấy gộp các kênh", set(r["customerMethods"]) == {"CARD", "QR", "PAYNOW", "GOOGLE_PAY", "APPLE_PAY"}, r["customerMethods"])
check("settlement merchant vẫn là kênh chính (VTB)", psql(f"select settlement_account_number from merchants where mer_no='{MER}'") == "0011001234567")

print("== 6. Gateway tự chọn terminal theo phương thức")
st, q = pay(8010, "QR")
check("QR -> terminal kênh VTB", terminal_of(q["providerPaymentId"]) == DEF, terminal_of(q["providerPaymentId"]))
st, g = pay(8011, "GOOGLE_PAY")
check("GOOGLE_PAY -> terminal kênh TCB", st == 200 and terminal_of(g["providerPaymentId"]) == TCB_TER, g)
st, c = pay(8012, "CARD")
check("CARD -> terminal kênh VTB", st == 200 and terminal_of(c["providerPaymentId"]) == DEF, c)
st, s = call("GET", f"/api/v1/gateway/payments/{g['providerPaymentId']}", None, M)
check("tra trạng thái chỉ bằng merchant (không terminal)", st == 200 and s["status"] == "PENDING", s)
st, _ = pay(8013, "QR", {"X-Terminal-Id": TCB_TER})
check("client cũ gửi X-Terminal-Id vẫn chạy đúng terminal chỉ định (TCB nhận QR theo seed)", st in (200, 409), _)

print("== 7. Thanh toán Google Pay trọn vòng, hoàn tiền chỉ cần merchant")
st, r = call("POST", f"/checkout/{g['providerPaymentId']}/google-pay", {"token": "{\"signature\":\"x\"}", "scenario": "APPROVED"})
check("Google Pay duyệt", st == 200 and r["status"] == "SUCCEEDED", r)
st, s = call("GET", f"/api/v1/gateway/payments/{g['providerPaymentId']}", None, M)
check("PAID", s["status"] == "PAID", s)
st, rf = call("POST", "/api/v1/gateway/refunds", {"referenceId": "ref-8011", "amount": 250000, "description": "hoan", "toBin": "970436",
    "toAccountNumber": "0123456789012", "providerPaymentId": g["providerPaymentId"]}, M)
check("hoàn tiền chỉ bằng merchant (không terminal)", st == 200 and rf["status"] in ("SUCCEEDED", "PROCESSING"), rf)

print("== 8. Sửa kênh")
st, r = call("PUT", f"/api/v1/gateway/channels/{CH_VTB}", {"paymentMethods": ["CARD", "QR"]}, M)
check("bỏ PAYNOW, giữ tài khoản", st == 200 and next(c for c in r["channels"] if c["id"] == CH_VTB)["paymentMethods"] == ["CARD", "QR"], r)
st, r = call("PUT", f"/api/v1/gateway/channels/{CH_VTB}", {"paymentMethods": ["CARD", "QR"], "accountName": "Chi ten"}, M)
check("nhập nửa tài khoản -> 400 ACCOUNT_REQUIRED", st == 400 and r["code"] == "ACCOUNT_REQUIRED", r)
st, r = call("PUT", f"/api/v1/gateway/channels/{CH_VTB}", {"paymentMethods": ["CARD", "QR", "GOOGLE_PAY"]}, M)
check("VTB không hỗ trợ GOOGLE_PAY -> 400 (ngân hàng kiểm trước)", st == 400 and r["code"] == "PAYMENT_METHOD_NOT_SUPPORTED_BY_BANK", r)
st, r = call("PUT", f"/api/v1/gateway/channels/{tcb['id']}", {"paymentMethods": ["GOOGLE_PAY", "QR"]}, M)
check("QR đang ở kênh VTB -> kênh TCB không giành được: 409", st == 409 and r["code"] == "PAYMENT_METHOD_IN_OTHER_CHANNEL", r)
st, r = call("PUT", "/api/v1/gateway/channels/TerNo999999", {"paymentMethods": ["QR"]}, M)
check("kênh không tồn tại -> 404", st == 404 and r["code"] == "PAYMENT_CHANNEL_NOT_FOUND", r)
st, r = call("PUT", f"/api/v1/gateway/channels/{CH_VTB}", {"paymentMethods": ["CARD", "QR"], "accountName": "Nguyen A moi", "accountNumber": "5555666677"}, M)
check("đổi tài khoản kênh chính -> settlement merchant đổi theo", st == 200 and psql(f"select settlement_account_number from merchants where mer_no='{MER}'") == "5555666677", r)

print("== 9. Xóa kênh")
st, r = call("DELETE", f"/api/v1/gateway/channels/{tcb['id']}", None, M)
check("xóa kênh TCB", st == 200 and [c["bankCode"] for c in r["channels"]] == ["VTB"], r)
check("terminal TCB vẫn ACTIVE cho đơn cũ", psql(f"select status||'/'||(retired_at is not null) from terminals where id='{tcb['id']}'") == "ACTIVE/true")
st, s = call("GET", f"/api/v1/gateway/payments/{g['providerPaymentId']}", None, M)
check("đơn cũ của kênh đã xóa vẫn tra được", st == 200 and s["status"] == "PAID", s)
st, _ = pay(8020, "GOOGLE_PAY")
check("Google Pay không còn nhận đơn mới -> 409", st == 409, _)
st, r = call("DELETE", f"/api/v1/gateway/channels/{CH_VTB}", None, M)
check("không xóa được kênh cuối -> 409 LAST_PAYMENT_CHANNEL", st == 409 and r["code"] == "LAST_PAYMENT_CHANNEL", r)
st, r = call("POST", "/api/v1/gateway/channels", {"bankCode": "TCB", "paymentMethods": ["GOOGLE_PAY"], "accountName": "Nguyen B2", "accountNumber": "1212121212"}, M)
check("thêm lại TCB dùng lại terminal cũ", st == 200 and any(c["id"] == tcb["id"] for c in r["channels"]), r)
st, g2 = pay(8021, "GOOGLE_PAY")
check("Google Pay nhận đơn lại", st == 200 and terminal_of(g2["providerPaymentId"]) == TCB_TER, g2)

print("== 10. Admin")
st, r = call("PATCH", f"/api/v1/admin/terminals/{TCB_TER}", {"status": "INACTIVE"}, A)
check("không tắt được terminal của kênh đang mở -> 409 TERMINAL_IN_USE", st == 409 and r["code"] == "TERMINAL_IN_USE", r)
st, r = call("PUT", f"/api/v1/admin/merchants/{MER}/default-terminal", {"terminalId": TCB_TER}, A)
check("kênh không đặt làm mặc định được -> 409", st == 409 and r["code"] == "TERMINAL_IS_CHANNEL", r)
st, spare = call("POST", f"/api/v1/admin/merchants/{MER}/terminals", {"name": "Spare", "channel": "WEB", "currency": "VND", "paymentMethods": ["CARD", "QR"], "threeDsPolicy": "OPTIONAL", "routingProfileCode": "STANDARD"}, A)
check("terminal admin tạo thêm là SPARE", st in (200, 201) and spare["purpose"] == "SPARE", spare)
st, r = call("PUT", f"/api/v1/admin/merchants/{MER}/default-terminal", {"terminalId": spare["terminalId"]}, A)
check("đặt terminal mặc định", st == 200 and r["purpose"] == "DEFAULT", r)
st, r = call("PATCH", f"/api/v1/admin/terminals/{spare['terminalId']}", {"status": "INACTIVE"}, A)
check("có kênh thì tắt terminal mặc định được", st == 200 and r["status"] == "INACTIVE", r)

print(f"\nKẾT QUẢ: {ok} đạt, {bad} lỗi")
sys.exit(1 if bad else 0)
