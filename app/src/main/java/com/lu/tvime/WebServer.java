package com.lu.tvime;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 电视上的小 HTTP 服务（默认 8765）：手机扫码打开网页，输入的文字实时送进电视输入框。所有接口都要口令 k。 */
final class WebServer {

    private static final int DEFAULT_PORT = 8765;
    private static volatile ServerSocket server;
    private static volatile ExecutorService pool;
    private static volatile int port = DEFAULT_PORT;
    private static volatile Context app;
    private static boolean starting;

    private WebServer() {
    }

    static int port() {
        return port;
    }

    static synchronized void start(Context c) {
        app = c.getApplicationContext();
        if (server != null || starting) {
            return;
        }
        starting = true;
        pool = Executors.newCachedThreadPool();
        new Thread(new Runnable() {
            @Override
            public void run() {
                serve();
            }
        }, "tvime-http").start();
    }

    static synchronized void stop() {
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException ignored) {
        }
        server = null;
        starting = false;
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private static void serve() {
        ServerSocket ss = null;
        int want = Net.prefs(app).getInt("port", DEFAULT_PORT);
        for (int p = want; p < want + 10; p++) {
            try {
                ss = new ServerSocket(p, 20);
                port = p;
                break;
            } catch (IOException ignored) {
            }
        }
        if (ss == null) {
            synchronized (WebServer.class) {
                starting = false;
            }
            return;
        }
        Net.prefs(app).edit().putInt("port", port).commit();
        synchronized (WebServer.class) {
            server = ss;
            starting = false;
        }
        while (server == ss) {
            try {
                final Socket s = ss.accept();
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        handle(s);
                    }
                });
            } catch (Throwable e) {
                break;
            }
        }
    }

    // ---------- HTTP ----------

    private static final class Req {
        String path;
        Map<String, String> query = new HashMap<String, String>();
        byte[] body = new byte[0];
    }

    private static void handle(Socket s) {
        try {
            s.setSoTimeout(15000);
            Req r = readReq(s.getInputStream());
            if (r != null) {
                route(r, s.getOutputStream());
                s.getOutputStream().flush();
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static Req readReq(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (head.size() < 16384) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            head.write(b);
            if (b == '\r' && (state == 0 || state == 2)) {
                state++;
            } else if (b == '\n' && (state == 1 || state == 3)) {
                state++;
                if (state == 4) {
                    break;
                }
            } else {
                state = 0;
            }
        }
        String[] lines = head.toString("UTF-8").split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) {
            return null;
        }
        Req r = new Req();
        String target = first[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        if (q >= 0) {
            for (String kv : target.substring(q + 1).split("&")) {
                int e = kv.indexOf('=');
                try {
                    r.query.put(URLDecoder.decode(e < 0 ? kv : kv.substring(0, e), "UTF-8"),
                            e < 0 ? "" : URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
                } catch (Exception ignored) {
                }
            }
        }
        int len = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(lines[i].substring(15).trim());
            }
        }
        if (len < 0 || len > 256 * 1024) {
            return null;
        }
        r.body = new byte[len];
        int got = 0;
        while (got < len) {
            int n = in.read(r.body, got, len - got);
            if (n < 0) {
                return null;
            }
            got += n;
        }
        return r;
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String msg = code == 200 ? "OK" : code == 403 ? "Forbidden" : "Not Found";
        out.write(("HTTP/1.1 " + code + " " + msg + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes("UTF-8"));
        out.write(body);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static void json(OutputStream out, int code, boolean ok, String msg) throws IOException {
        send(out, code, "application/json; charset=utf-8",
                ("{\"ok\":" + ok + ",\"msg\":\"" + esc(msg) + "\"}").getBytes("UTF-8"));
    }

    private static void route(Req r, OutputStream out) throws Exception {
        if (r.path.equals("/") || r.path.equals("/index.html")) {
            InputStream is = app.getAssets().open("phone.html");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            is.close();
            send(out, 200, "text/html; charset=utf-8", bo.toByteArray());
            return;
        }
        if (!Net.token(app).equals(r.query.get("k"))) {
            json(out, 403, false, "口令不对：请重新扫描电视上的二维码");
            return;
        }
        String p = r.path;
        String act;
        String text = "";
        int code = 0;
        if (p.equals("/api/live")) {
            act = "live";
            text = new String(r.body, "UTF-8");
        } else if (p.equals("/api/text")) {
            act = "text";
            text = new String(r.body, "UTF-8");
        } else if (p.equals("/api/ime")) {
            act = r.query.get("act");
        } else if (p.equals("/api/key")) {
            act = "key";
            code = Integer.parseInt(r.query.get("code"));
            // 只放行方向键/确定/返回/回车，不让网页发电源、音量等
            if (!(code == 19 || code == 20 || code == 21 || code == 22 || code == 23 || code == 4 || code == 66)) {
                json(out, 200, false, "不支持这个按键");
                return;
            }
        } else {
            send(out, 404, "text/plain", new byte[0]);
            return;
        }
        String m = ImeService.phoneAct(act == null ? "" : act, text, code);
        json(out, 200, "ok".equals(m), m);
    }
}
