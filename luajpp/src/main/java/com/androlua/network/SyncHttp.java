package com.androlua.network;

import android.os.Looper;

import com.androlua.internal.LuaConfig;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 同步 HTTP。
 * SyncHttp.get(url) 直接返回 HttpResult，主线程安全。
 */
public class SyncHttp {

    private static final ExecutorService EXEC = Executors.newCachedThreadPool();

    // ==================== 请求头 ====================

    public static Map<String, String> getDefaultHeaders() {
        return HttpCore.getDefaultHeaders();
    }

    public static void setDefaultHeaders(Map<String, String> h) {
        HttpCore.setDefaultHeaders(h);
    }

    // ==================== GET ====================

    public static HttpCore.HttpResult get(String url) {
        return exec(HttpCore.get(url));
    }

    public static HttpCore.HttpResult get(String url, Map<String, String> h) {
        return exec(HttpCore.get(url, h));
    }

    // ==================== HEAD ====================

    public static HttpCore.HttpResult head(String url) {
        return exec(HttpCore.head(url));
    }

    public static HttpCore.HttpResult head(String url, Map<String, String> h) {
        return exec(HttpCore.head(url, h));
    }

    // ==================== POST ====================

    public static HttpCore.HttpResult post(String url, Object body) {
        return exec(HttpCore.post(url, body));
    }

    public static HttpCore.HttpResult post(String url, Object body, Map<String, String> h) {
        return exec(HttpCore.post(url, body, h));
    }

    // ==================== PUT ====================

    public static HttpCore.HttpResult put(String url, Object body) {
        return exec(HttpCore.put(url, body));
    }

    public static HttpCore.HttpResult put(String url, Object body, Map<String, String> h) {
        return exec(HttpCore.put(url, body, h));
    }

    // ==================== DELETE ====================

    public static HttpCore.HttpResult delete(String url) {
        return exec(HttpCore.delete(url));
    }

    public static HttpCore.HttpResult delete(String url, Map<String, String> h) {
        return exec(HttpCore.delete(url, h));
    }

    // ==================== 上传 ====================

    public static HttpCore.HttpResult upload(String url, Map<String, String> d, Map<String, String> f) {
        return exec(HttpCore.upload(url, d, f));
    }

    public static HttpCore.HttpResult upload(String url, Map<String, String> d, Map<String, String> f, Map<String, String> h) {
        return exec(HttpCore.upload(url, d, f, h));
    }

    // ==================== 核心 ====================

    private static HttpCore.HttpResult exec(HttpCore.HttpTask task) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return task.sync();
        }

        Future<HttpCore.HttpResult> f = EXEC.submit(task::sync);
        try {
            return f.get(LuaConfig.getHttpTimeout() + 500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            return new HttpCore.HttpResult(-1, "timeout", null, null, Collections.emptyMap(), null);
        } catch (Exception e) {
            return new HttpCore.HttpResult(-1, "error: " + e.getMessage(), null, null, Collections.emptyMap(), null);
        }
    }
}