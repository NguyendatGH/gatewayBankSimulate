package com.bankSimulate.infrastructure.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Map;

public final class QrSvg {
    private QrSvg() {}

    public static String render(String payload) {
        try {
            var bits = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 256, 256,
                    Map.of(EncodeHintType.MARGIN, 2));
            StringBuilder svg = new StringBuilder("<svg role=\"img\" aria-label=\"Mã QR mô phỏng\" viewBox=\"0 0 ")
                    .append(bits.getWidth()).append(' ').append(bits.getHeight())
                    .append("\" xmlns=\"http://www.w3.org/2000/svg\"><path fill=\"#fff\" d=\"M0 0h")
                    .append(bits.getWidth()).append('v').append(bits.getHeight()).append("H0z\"/><path fill=\"#17152a\" d=\"");
            for (int y = 0; y < bits.getHeight(); y++)
                for (int x = 0; x < bits.getWidth(); x++)
                    if (bits.get(x, y)) svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
            return svg.append("\"/></svg>").toString();
        } catch (WriterException ex) {
            throw new IllegalStateException("Could not encode sandbox QR", ex);
        }
    }
}
