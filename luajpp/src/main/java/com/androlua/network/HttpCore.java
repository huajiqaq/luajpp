package com.androlua.network;

import androidx.annotation.NonNull;

import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaScheduler;
import com.androlua.util.LuaUtil;

import org.luaj.LuaConstants;
import org.luaj.LuaError;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class HttpCore {

    public static Map<String, String> getDefaultHeaders() {
        return Executor.sDefaultHeaders;
    }

    public static void setDefaultHeaders(Map<String, String> h) {
        Executor.sDefaultHeaders = h;
    }

    // ==================== 工厂方法 ====================

    public static HttpTask get(String url) {
        return request("GET", url, null, null);
    }

    public static HttpTask get(String url, Map<String, String> h) {
        return request("GET", url, null, h);
    }

    public static HttpTask head(String url) {
        return request("HEAD", url, null, null);
    }

    public static HttpTask head(String url, Map<String, String> h) {
        return request("HEAD", url, null, h);
    }

    public static HttpTask post(String url, Object body) {
        return request("POST", url, body, null);
    }

    public static HttpTask post(String url, Object body, Map<String, String> h) {
        return request("POST", url, body, h);
    }

    public static HttpTask put(String url, Object body) {
        return request("PUT", url, body, null);
    }

    public static HttpTask put(String url, Object body, Map<String, String> h) {
        return request("PUT", url, body, h);
    }

    public static HttpTask delete(String url) {
        return request("DELETE", url, null, null);
    }

    public static HttpTask delete(String url, Map<String, String> h) {
        return request("DELETE", url, null, h);
    }

    public static HttpTask upload(String url, Map<String, String> data, Map<String, String> files) {
        return uploadRequest(url, data, files, null);
    }

    public static HttpTask upload(String url, Map<String, String> data, Map<String, String> files, Map<String, String> h) {
        return uploadRequest(url, data, files, h);
    }

    // ==================== 内部统一入口 ====================

    private static HttpTask request(String method, String url, Object body, Map<String, String> headers) {
        return new HttpTask(method, url, headers, body, false);
    }

    private static HttpTask uploadRequest(String url, Map<String, String> data, Map<String, String> files, Map<String, String> headers) {
        return new HttpTask("POST", url, headers, new Object[]{data, files}, true);
    }

    // ==================== HttpTask ====================

    public static class HttpTask {
        final String method, url;
        final Map<String, String> headers;
        final Object body;
        final boolean multipart;

        HttpTask(String m, String url, Map<String, String> h, Object b, boolean multi) {
            method = m;
            this.url = url;
            headers = h;
            body = b;
            multipart = multi;
        }

        public HttpResult sync() {
            return HttpResult.from(Executor.execute(method, url, headers, body, multipart));
        }

        public void async(LuaValue cb) {
            LuaScheduler.getInstance().runOnIo(this::sync, r -> {
                try {
                    cb.jcall(r);
                } catch (LuaError e) {
                    LuaConfig.log(e.getMessage());
                }
            });
        }
    }

    // ==================== HttpResult ====================

    public static class HttpResult extends Varargs {
        public final int code;
        public final String text;
        public final byte[] bytes;
        public final String cookies;
        public final Map<String, List<String>> headers;
        public final String contentType;

        HttpResult(int code, String text, byte[] bytes, String cookies,
                   Map<String, List<String>> headers, String contentType) {
            this.code = code;
            this.text = text;
            this.bytes = bytes;
            this.cookies = cookies != null ? cookies : "";
            this.headers = headers;
            this.contentType = contentType;
        }

        static HttpResult from(Response r) {
            return new HttpResult(r.code, r.textBody, r.rawBody, r.cookies, r.headers, r.contentType);
        }

        public boolean isOk() {
            return code >= 200 && code < 300;
        }

        public boolean isText() {
            return text != null;
        }

        public boolean isBinary() {
            return bytes != null;
        }

        @Override
        public int narg() {
            return 4;
        }

        @Override
        public LuaValue arg1() {
            return LuaValue.valueOf(text != null ? text : "");
        }

        @Override
        public LuaValue arg(int i) {
            return switch (i) {
                case 1 -> LuaValue.valueOf(text != null ? text : "");
                case 2 -> LuaValue.valueOf(cookies);
                case 3 -> LuaValue.valueOf(code);
                case 4 -> CoerceJavaToLua.coerce(headers);
                default -> LuaConstants.NIL;
            };
        }

        @NonNull
        @Override
        public String toString() {
            return text != null ? text : "[binary " + (bytes != null ? bytes.length : 0) + " bytes]";
        }

        @Override
        public Varargs subargs(int start) {
            if (start < 1 || start > 4) return LuaConstants.NIL;
            LuaValue[] v = new LuaValue[5 - start];
            for (int i = 0; i < v.length; i++) v[i] = arg(start + i);
            return LuaValue.varargsOf(v);
        }
    }

    // ==================== Response ====================

    static class Response {
        final int code;
        final String textBody;
        final byte[] rawBody;
        final String cookies;
        final Map<String, List<String>> headers;
        final String contentType;

        Response(int code, byte[] raw, String cookies, Map<String, List<String>> headers) {
            this.code = code;
            this.rawBody = raw;
            this.cookies = cookies;
            this.headers = headers;
            this.contentType = extractContentType(headers);
            this.textBody = isText() ? new String(raw, detectCharset(headers)) : null;
        }

        private static String extractContentType(Map<String, List<String>> h) {
            List<String> ct = h.get("Content-Type");
            if (ct == null || ct.isEmpty()) return null;
            String s = ct.get(0);
            int i = s.indexOf(';');
            return i == -1 ? s : s.substring(0, i);
        }

        private static Charset detectCharset(Map<String, List<String>> h) {
            List<String> ct = h.get("Content-Type");
            if (ct != null) {
                for (String s : ct) {
                    int i = s.indexOf("charset=");
                    if (i != -1) {
                        i += 8;
                        int e = s.indexOf(";", i);
                        try {
                            return Charset.forName(s.substring(i, e == -1 ? s.length() : e));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            return StandardCharsets.UTF_8;
        }

        boolean isText() {
            if (contentType == null) return true;
            String ct = contentType.toLowerCase();
            return ct.startsWith("text/")
                    || ct.contains("json")
                    || ct.contains("xml")
                    || ct.contains("javascript")
                    || ct.contains("x-www-form-urlencoded");
        }
    }

    // ==================== 执行器 ====================

    static class Executor {
        static final String BOUNDARY = "----qwertyuiopasdfghjklzxcvbnm";
        static Map<String, String> sDefaultHeaders;

        static {
            try {
                SSLContext c = SSLContext.getInstance("TLS");
                c.init(null, new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] a, String b) {
                    }

                    public void checkServerTrusted(X509Certificate[] a, String b) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(c.getSocketFactory());
            } catch (Exception ignored) {
            }
        }

        static Response execute(String m, String url, Map<String, String> h, Object b, boolean multipart) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(LuaConfig.getHttpTimeout());
                conn.setDoInput(true);
                put(conn, sDefaultHeaders);
                put(conn, h);
                conn.setRequestMethod(m);

                byte[] post = null;
                if (multipart && b instanceof Object[] arr) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> data = (Map<String, String>) arr[0];
                    @SuppressWarnings("unchecked")
                    Map<String, String> files = (Map<String, String>) arr[1];
                    post = buildMultipart(data, files);
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
                } else if (b != null) {
                    post = formatBody(b);
                    if (b instanceof Map) {
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    }
                }

                if (post != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Length", String.valueOf(post.length));
                }

                conn.connect();

                if (post != null) try (OutputStream os = conn.getOutputStream()) {
                    os.write(post);
                }

                return handleResponse(conn);
            } catch (Exception e) {
                return new Response(-1, Objects.requireNonNull(e.getMessage()).getBytes(StandardCharsets.UTF_8), null, Collections.emptyMap());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        private static Response handleResponse(HttpURLConnection c) throws IOException {
            int code = c.getResponseCode();
            Map<String, List<String>> h = c.getHeaderFields();
            byte[] body = readAllBytes(c);
            return new Response(code, body, cookies(h), h);
        }

        private static byte[] readAllBytes(HttpURLConnection c) throws IOException {
            try (InputStream i = c.getInputStream()) {
                return readStream(i);
            } catch (IOException e) {
                try (InputStream i = c.getErrorStream()) {
                    if (i != null) return readStream(i);
                    throw e;
                }
            }
        }

        private static byte[] readStream(InputStream is) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toByteArray();
        }

        private static String cookies(Map<String, List<String>> h) {
            List<String> l = h.get("Set-Cookie");
            if (l == null || l.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String x : l) sb.append(x).append(";");
            return sb.toString();
        }

        private static byte[] formatBody(Object b) throws IOException {
            switch (b) {
                case String s -> {
                    return s.getBytes(StandardCharsets.UTF_8);
                }
                case byte[] bytes -> {
                    return bytes;
                }
                case File file -> {
                    return LuaUtil.readAll(new FileInputStream(file));
                }
                case Map<?, ?> ignored -> {
                    @SuppressWarnings("unchecked") Map<String, String> m = (Map<String, String>) b;
                    return formatMap(m).getBytes(StandardCharsets.UTF_8);
                }
                case null, default -> {
                    return null;
                }
            }
        }

        private static String formatMap(Map<String, String> m) {
            if (m == null || m.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : m.entrySet())
                sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
            sb.setLength(sb.length() - 1);
            return sb.toString();
        }

        private static byte[] buildMultipart(Map<String, String> data, Map<String, String> files) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String cs = "UTF-8";

            if (data != null) {
                for (Map.Entry<String, String> e : data.entrySet()) {
                    baos.write(String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", BOUNDARY, e.getKey(), e.getValue()).getBytes(cs));
                }
            }

            if (files != null) {
                for (Map.Entry<String, String> e : files.entrySet()) {
                    String path = e.getValue();
                    String name = path.substring(path.lastIndexOf('/') + 1);
                    baos.write(String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n", BOUNDARY, e.getKey(), name, "application/octet-stream").getBytes(cs));
                    baos.write(LuaUtil.readAll(new FileInputStream(path)));
                    baos.write("\r\n".getBytes(cs));
                }
            }

            baos.write(String.format("--%s--\r\n", BOUNDARY).getBytes(cs));
            return baos.toByteArray();
        }

        private static void put(HttpURLConnection c, Map<String, String> h) {
            if (h != null) for (Map.Entry<String, String> e : h.entrySet())
                c.setRequestProperty(e.getKey(), e.getValue());
        }
    }
}