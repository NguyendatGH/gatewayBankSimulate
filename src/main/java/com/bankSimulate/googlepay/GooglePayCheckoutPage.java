package com.bankSimulate.googlepay;

import com.bankSimulate.application.QrPaymentService;
import com.bankSimulate.infrastructure.web.QrCheckoutPage;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;


@Component
public class GooglePayCheckoutPage {

    private final GooglePaySandboxBank bank;

    public GooglePayCheckoutPage(GooglePaySandboxBank bank) {
        this.bank = bank;
    }

    public boolean supports(QrPaymentService.CheckoutView v) {
        return "GOOGLE_PAY".equals(v.method()) && v.pending();
    }

    public String render(QrPaymentService.CheckoutView v) {
        String id = HtmlUtils.htmlEscape(v.tradeNo(), "UTF-8");
        String status = "<div class=\"flow-card\"><span class=\"flow-icon\" aria-hidden=\"true\">G</span><div><strong>"
                + "Thanh toán bằng Google Pay</strong><p>Bấm nút Google Pay, chọn thẻ thử nghiệm trong cửa sổ của Google. "
                + "Ngân hàng mô phỏng sẽ duyệt giao dịch.</p></div></div>";
        String action = "<div id=\"gpay\" data-trade=\"" + id + "\" data-env=\"" + HtmlUtils.htmlEscape(bank.environment(), "UTF-8")
                + "\" data-merchant=\"" + HtmlUtils.htmlEscape(v.merchantName(), "UTF-8") + "\" data-amount=\"" + v.amount()
                + "\"></div><p id=\"gpay-msg\" class=\"otp-error\" role=\"alert\" aria-live=\"polite\"></p>";
        return QrCheckoutPage.renderWith(v, new QrCheckoutPage.Slots(status, action, scenarioPicker(), SCRIPT));
    }

    private String scenarioPicker() {
        return bank.scenariosEnabled() ? """
                  <label class="otp-label" for="gpay-scenario" style="margin-top:12px">Kịch bản ngân hàng mô phỏng (chỉ sandbox)</label>
                  <select id="gpay-scenario" class="otp-input" style="letter-spacing:0">
                    <option value="APPROVED">APPROVED — ngân hàng duyệt</option>
                    <option value="DECLINED">DECLINED — ngân hàng từ chối</option>
                    <option value="BANK_TIMEOUT">BANK_TIMEOUT — ngân hàng không phản hồi</option>
                  </select>
                """ : "";
    }


    private static final String SCRIPT = """
            <script>
            (function(){
              var root=document.getElementById('gpay'), msg=document.getElementById('gpay-msg'), busy=false, client;
              var base={apiVersion:2,apiVersionMinor:0};
              var method={type:'CARD',parameters:{allowedAuthMethods:['PAN_ONLY','CRYPTOGRAM_3DS'],allowedCardNetworks:['VISA','MASTERCARD']},
                tokenizationSpecification:{type:'PAYMENT_GATEWAY',parameters:{gateway:'example',gatewayMerchantId:'exampleGatewayMerchantId'}}};
              function say(t){msg.textContent=t||'';}
              function pay(){
                if(busy) return; busy=true; say('Đang mở Google Pay…');
                var req=Object.assign({},base,{allowedPaymentMethods:[method],merchantInfo:{merchantName:root.dataset.merchant},
                  transactionInfo:{totalPriceStatus:'FINAL',totalPrice:root.dataset.amount,currencyCode:'VND',countryCode:'VN'}});
                client.loadPaymentData(req).then(function(pd){
                  say('Đang xử lý thanh toán…');
                  var pick=document.getElementById('gpay-scenario');
                  return fetch('/checkout/'+root.dataset.trade+'/google-pay',{method:'POST',headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({token:pd.paymentMethodData.tokenizationData.token,scenario:pick?pick.value:null})})
                    .then(function(r){return r.json().then(function(j){return {ok:r.ok,body:j};});});
                }).then(function(res){
                  if(!res.ok) throw new Error(res.body&&res.body.message||'Thanh toán thất bại');
                  if(res.body.status==='PENDING'){busy=false; say('Ngân hàng chưa phản hồi. Giao dịch vẫn đang chờ, bạn có thể thử lại.'); return;}
                  location.assign(res.body.redirectUrl);
                }).catch(function(e){
                  busy=false;
                  say(e&&e.statusCode==='CANCELED' ? 'Bạn đã hủy thanh toán Google Pay.' : (e&&e.message)||'Không thể hoàn tất thanh toán Google Pay.');
                });
              }
              window.initGooglePay=function(){
                client=new google.payments.api.PaymentsClient({environment:root.dataset.env});
                client.isReadyToPay(Object.assign({},base,{allowedPaymentMethods:[method]})).then(function(r){
                  if(!r.result){say('Google Pay không khả dụng trên trình duyệt này. Hãy đăng nhập tài khoản Google hoặc chọn phương thức khác.');return;}
                  root.appendChild(client.createButton({buttonType:'pay',buttonColor:'black',buttonSizeMode:'fill',onClick:pay})); say('');
                }).catch(function(){say('Không kiểm tra được Google Pay.');});
              };
              say('Đang tải Google Pay…');
            })();
            </script>
            <script async src="https://pay.google.com/gp/p/js/pay.js" onload="initGooglePay()"
              onerror="document.getElementById('gpay-msg').textContent='Không tải được Google Pay (kiểm tra kết nối mạng).'"></script>
            """;
}
