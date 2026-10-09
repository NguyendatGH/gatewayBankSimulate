package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.QrPaymentService;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.util.Locale;

public final class QrCheckoutPage {

    private QrCheckoutPage() {
    }
    public record Slots(String status, String action, String extraTestControls, String script) {}

    static String render(QrPaymentService.CheckoutView v) {
        return renderWith(v, null);
    }

    public static String renderWith(QrPaymentService.CheckoutView v, Slots custom) {
        String safeId = HtmlUtils.htmlEscape(v.tradeNo(), "UTF-8");
        String qrPanel = v.qrPayload() == null ? "" : "<section class=\"qr-panel\"><p class=\"eyebrow\">Mã QR mô phỏng</p>"
                + QrSvg.render(v.qrPayload()) + "<pre>" + HtmlUtils.htmlEscape(v.qrPayload(), "UTF-8") + "</pre></section>";
        boolean hasCustom = v.pending() && custom != null;
        String statusPanel;
        String paymentAction;
        if (!v.pending()) {
            String message = switch (v.status()) {
                case "PAID" -> "Giao dịch đã hoàn tất. Bạn có thể quay lại trang cửa hàng.";
                case "EXPIRED" -> "Giao dịch đã hết hạn.";
                default -> "Giao dịch này không còn nhận xác nhận.";
            };
            statusPanel = "<div class=\"notice\" role=\"status\">" + message + "</div>";
            paymentAction = "<a class=\"button secondary\" href=\"" + HtmlUtils.htmlEscape(v.backUrl(), "UTF-8")
                    + "\">Quay lại cửa hàng</a>";
        } else {
            String title = "QR".equals(v.method()) ? "Quét mã bằng ứng dụng ngân hàng" : "Xác nhận trên ví / ứng dụng ngân hàng";
            statusPanel = "<div class=\"flow-card\"><span class=\"flow-icon\" aria-hidden=\"true\">↗</span><div><strong>"
                    + title + "</strong><p>Bấm \"Xác nhận thanh toán\" để mô phỏng ngân hàng của bạn duyệt giao dịch.</p></div></div>";
            paymentAction = "<form method=\"post\" action=\"/checkout/" + safeId + "/succeed\"><button class=\"button primary\" "
                    + "type=\"submit\">Xác nhận thanh toán<span aria-hidden=\"true\">→</span></button></form>";
            if (hasCustom) {
                statusPanel = custom.status();
                paymentAction = custom.action();
            }
        }
        String extraTest = hasCustom ? custom.extraTestControls() : "";
        String testControls = v.pending() ? """
                <details class="test-controls"><summary>Tuỳ chọn kiểm thử sandbox</summary>
                  %s
                  <div class="test-actions">
                    <form method="post" action="/checkout/%s/fail"><button class="button test-fail" type="submit">Mô phỏng thất bại</button></form>
                    <form method="post" action="/checkout/%s/expire"><button class="button secondary" type="submit">Mô phỏng hết hạn</button></form>
                  </div>
                </details>
                """.formatted(extraTest, safeId, safeId) : "";
        return TEMPLATE
                .replace("__MERCHANT__", HtmlUtils.htmlEscape(v.merchantName(), "UTF-8"))
                .replace("__METHOD__", HtmlUtils.htmlEscape(methodTitle(v.method()), "UTF-8"))
                .replace("__ORDER__", HtmlUtils.htmlEscape(v.orderCode(), "UTF-8"))
                .replace("__AMOUNT__", NumberFormat.getIntegerInstance(Locale.US).format(v.amount()))
                .replace("__STATUS__", statusPanel)
                .replace("__QR__", qrPanel)
                .replace("__ACTION__", paymentAction)
                .replace("__TEST_CONTROLS__", testControls)
                .replace("__SCRIPT__", hasCustom ? custom.script() : "");
    }

    private static String methodTitle(String method) {
        return switch (method) {
            case "PAYNOW" -> "PayNow";
            case "GOOGLE_PAY" -> "Google Pay";
            case "APPLE_PAY" -> "Apple Pay";
            case "QR" -> "QR / VietQR";
            default -> method;
        };
    }

    private static final String TEMPLATE = """
                <!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="light"><title>Thanh toán an toàn · Encore</title>
                <style>
                :root{font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#17152a;background:#f5f4fa;font-synthesis:none;text-rendering:optimizeLegibility}
                *{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(ellipse at 50% -15%,#e6e0ff 0,transparent 55%),#f7f6fb}
                .topbar{height:68px;border-bottom:1px solid #e9e7f0;background:#ffffffd9;display:flex;align-items:center}.topbar-inner{width:min(100% - 40px,1020px);margin:auto;display:flex;align-items:center;justify-content:space-between}
                .brand{display:flex;align-items:center;gap:10px;font-size:17px;font-weight:750;letter-spacing:-.04em}.brand-mark{width:30px;height:30px;border-radius:10px;background:#635bdb;color:white;display:grid;place-items:center;font-weight:800}.secure{color:#69677a;font-size:12px;display:flex;align-items:center;gap:7px}.secure b{color:#27704b;font-weight:650}
                main{width:min(100% - 32px,520px);margin:52px auto 72px}.intro{text-align:center;margin-bottom:24px}.intro h1{font-size:25px;letter-spacing:-.04em;margin:0 0 8px}.intro p{color:#777487;font-size:14px;margin:0}
                .card{background:#fff;border:1px solid #e9e7f0;border-radius:20px;box-shadow:0 18px 55px #32245f12;overflow:hidden}.merchant{padding:22px 26px;border-bottom:1px solid #eeedf3;display:flex;justify-content:space-between;align-items:center}.eyebrow{font-size:10px;letter-spacing:.12em;text-transform:uppercase;color:#858296;font-weight:700;margin:0 0 7px}.merchant-name{font-size:14px;font-weight:650}.tag{font-size:11px;border-radius:20px;padding:6px 10px;background:#f0efff;color:#5b52c9;font-weight:650}
                .summary{padding:27px 26px 23px}.summary-top{display:flex;justify-content:space-between;gap:20px;align-items:flex-start}.summary-label{font-size:13px;color:#777487;margin:0 0 6px}.order{font-size:13px;font-weight:650;margin:0}.amount{text-align:right;white-space:nowrap}.amount strong{font-size:27px;letter-spacing:-.05em}.amount span{font-size:13px;color:#777487;margin-left:5px}
                .divider{height:1px;background:#eeedf3;margin:22px 0}.flow-card{display:flex;align-items:flex-start;gap:12px;padding:15px;border:1px solid #e9e7f0;border-radius:13px;background:#fbfaff}.flow-icon{width:26px;height:26px;flex:0 0 26px;border-radius:50%;background:#ebe9ff;color:#5c53ca;display:grid;place-items:center;font-size:14px;font-weight:800}.flow-card strong{font-size:13px}.flow-card p{font-size:12px;line-height:1.5;color:#777487;margin:4px 0 0}.qr-panel{margin-top:16px;padding:16px;border:1px solid #e9e7f0;border-radius:13px;background:#fbfaff;text-align:center}.qr-panel svg{display:block;width:min(100%,220px);height:auto;margin:12px auto;background:#fff;border:10px solid #fff;border-radius:8px}.qr-panel pre{font:12px/1.55 ui-monospace,monospace;color:#39364c;white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0 0;text-align:left}
                .card-form{margin-top:18px;display:grid;gap:11px}.card-form label{display:block;font-size:12px;font-weight:650;margin-bottom:6px}.card-form input{width:100%;height:46px;border:1px solid #cbc8dc;border-radius:10px;padding:0 12px;font:inherit}.card-fields{display:grid;grid-template-columns:1fr 1fr .8fr;gap:10px}.card-verified{margin-top:14px;padding:13px 15px;border:1px solid #bce3ce;border-radius:12px;background:#f0fbf4;color:#23643f;font-size:12px;display:grid;gap:4px}.card-verified span{color:#4f765f}.sandbox-card-hint{font-size:11px;line-height:1.5;color:#777487;margin:0}
                .notice{border-radius:12px;padding:14px;background:#f5f4fa;color:#605d70;font-size:13px;line-height:1.5}.actions{padding:0 26px 25px}.button{appearance:none;border:0;border-radius:12px;min-height:50px;padding:0 18px;font:inherit;font-size:14px;font-weight:650;display:flex;align-items:center;justify-content:center;gap:12px;cursor:pointer;text-decoration:none;transition:transform .15s,background .15s}.button:focus-visible,summary:focus-visible,.otp-input:focus-visible{outline:3px solid #a9a4ff;outline-offset:3px}.button:hover{transform:translateY(-1px)}.primary{width:100%;background:#5e56d6;color:white;box-shadow:0 7px 16px #5e56d633}.primary:hover{background:#5048c5}.secondary{background:#f1f0f6;color:#484558}.otp-label{display:block;font-size:12px;font-weight:650;margin:0 0 8px}.otp-input{width:100%;height:48px;border:1px solid #cbc8dc;border-radius:10px;padding:0 14px;font:inherit;letter-spacing:.16em;margin-bottom:12px}.otp-error{font-size:12px;color:#a43f37;margin:0 0 12px}.actions-note{text-align:center;color:#8a8799;font-size:11px;line-height:1.5;margin:12px 0 0}.test-controls{margin:0 26px 22px;border-top:1px solid #eeedf3;padding-top:13px;color:#858296;font-size:12px}.test-controls summary{cursor:pointer;width:max-content}.test-actions{display:flex;gap:10px;flex-wrap:wrap;padding-top:12px}.test-actions form{flex:1}.test-actions .button{width:100%;min-height:40px;font-size:12px}.test-fail{background:#fff0ef;color:#a43f37}
                .foot{text-align:center;color:#9693a2;font-size:11px;margin-top:18px}.foot a{color:#777487}
                @media(max-width:520px){main{margin:32px auto 48px}.intro h1{font-size:22px}.merchant,.summary{padding-left:19px;padding-right:19px}.actions{padding-left:19px;padding-right:19px}.test-controls{margin-left:19px;margin-right:19px}.amount strong{font-size:24px}.card-fields{grid-template-columns:1fr 1fr}.card-fields>div:last-child{grid-column:span 2}}
                </style></head><body><header class="topbar"><div class="topbar-inner"><div class="brand"><span class="brand-mark" aria-hidden="true">e</span>ENCORE</div><div class="secure"><span aria-hidden="true">▣</span><b>Thanh toán mô phỏng an toàn</b></div></div></header>
                <main><div class="intro"><h1>Hoàn tất thanh toán</h1><p>Kiểm tra thông tin đơn hàng trước khi xác nhận.</p></div>
                <section class="card" aria-label="Thông tin thanh toán"><div class="merchant"><div><p class="eyebrow">Đơn vị chấp nhận</p><div class="merchant-name">__MERCHANT__</div></div><span class="tag">SANDBOX · __METHOD__</span></div>
                <div class="summary"><div class="summary-top"><div><p class="summary-label">Mã đơn hàng</p><p class="order">#__ORDER__</p></div><div class="amount"><p class="summary-label">Tổng thanh toán</p><strong>__AMOUNT__</strong><span>VND</span></div></div>
                <div class="divider"></div>__STATUS____QR__</div><div class="actions">__ACTION__<p class="actions-note">Đây là giao dịch mô phỏng trong môi trường thử nghiệm. Không có tiền thật được chuyển.</p></div>__TEST_CONTROLS__</section>
                <p class="foot">Được bảo vệ bởi <a href="#">Encore Payment Gateway</a></p></main>__SCRIPT__</body></html>
                """;
}
