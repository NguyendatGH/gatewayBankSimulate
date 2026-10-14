package com.bankSimulate.infrastructure.web;

import org.springframework.web.util.HtmlUtils;

final class CheckoutStatusPoll {

    private static final int INTERVAL_MS = 3000;

    record Status(String status, String redirectUrl) {
    }

    private CheckoutStatusPoll() {
    }

    static String script(String statusUrl, String currentStatus) {
        return SCRIPT
                .replace("__URL__", HtmlUtils.htmlEscape(statusUrl, "UTF-8"))
                .replace("__STATUS__", HtmlUtils.htmlEscape(currentStatus, "UTF-8"))
                .replace("__INTERVAL__", String.valueOf(INTERVAL_MS));
    }

    private static final String SCRIPT = """
            <script>
            (() => {
              const url = '__URL__', initial = '__STATUS__';
              let stopped = false;
              const reload = () => { stopped = true; location.reload(); };
              const check = async () => {
                // Tab ẩn thì không hỏi; tab này đang tự thanh toán (submit form / Google Pay) thì để luồng đó tự chuyển trang
                if (stopped || document.hidden || window.checkoutBusy) return;
                try {
                  const r = await fetch(url, { cache: 'no-store', headers: { Accept: 'application/json' } });
                  if (r.status === 410) return reload();
                  if (!r.ok) return;
                  const body = await r.json();
                  if (body.redirectUrl) { stopped = true; location.replace(body.redirectUrl); return; }
                  if (body.status !== initial) reload();
                } catch (e) {
                  // mất mạng: thử lại ở lần sau
                }
              };
              document.addEventListener('submit', () => { stopped = true; });
              document.addEventListener('visibilitychange', () => { if (!document.hidden) check(); });
              setInterval(check, __INTERVAL__);
            })();
            </script>
            """;
}
