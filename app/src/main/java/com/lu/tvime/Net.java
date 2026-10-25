package com.lu.tvime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/** 本机地址、口令、二维码。 */
final class Net {

    private Net() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("tvime", Context.MODE_PRIVATE);
    }

    static String localIp() {
        String best = null;
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String n = ni.getName();
                        if (n.startsWith("wlan") || n.startsWith("eth")) {
                            return a.getHostAddress();
                        }
                        if (best == null) {
                            best = a.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    /** 口令：首次随机生成并保存，二维码里带着它，只有扫码的手机能用。可在设置页重置。 */
    static String token(Context c) {
        String t = prefs(c).getString("token", null);
        if (t == null || t.length() < 6) {
            t = newToken();
            prefs(c).edit().putString("token", t).commit();
        }
        return t;
    }

    static String resetToken(Context c) {
        String t = newToken();
        prefs(c).edit().putString("token", t).commit();
        return t;
    }

    private static String newToken() {
        byte[] b = new byte[5];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xff));
        }
        return sb.toString();
    }

    static String url(Context c) {
        String ip = localIp();
        if (ip == null) {
            return null;
        }
        return "http://" + ip + ":" + WebServer.port() + "/?k=" + token(c);
    }

    static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int w = m.getWidth();
            int h = m.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, w, 0, 0, w, h);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }
}
