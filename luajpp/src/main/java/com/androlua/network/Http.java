package com.androlua.network;

import org.luaj.LuaValue;

import java.util.Map;

/**
 * 异步 HTTP。
 * Http.get(url, cb)
 */
public class Http {

    // ==================== 请求头 ====================

    public static Map<String, String> getDefaultHeaders() {
        return HttpCore.getDefaultHeaders();
    }

    public static void setDefaultHeaders(Map<String, String> h) {
        HttpCore.setDefaultHeaders(h);
    }

    // ==================== GET ====================

    public static void get(String url, LuaValue cb) {
        HttpCore.get(url).async(cb);
    }

    public static void get(String url, Map<String, String> h, LuaValue cb) {
        HttpCore.get(url, h).async(cb);
    }

    // ==================== HEAD ====================

    public static void head(String url, LuaValue cb) {
        HttpCore.head(url).async(cb);
    }

    public static void head(String url, Map<String, String> h, LuaValue cb) {
        HttpCore.head(url, h).async(cb);
    }

    // ==================== POST ====================

    public static void post(String url, Object body, LuaValue cb) {
        HttpCore.post(url, body).async(cb);
    }

    public static void post(String url, Object body, Map<String, String> h, LuaValue cb) {
        HttpCore.post(url, body, h).async(cb);
    }

    // ==================== PUT ====================

    public static void put(String url, Object body, LuaValue cb) {
        HttpCore.put(url, body).async(cb);
    }

    public static void put(String url, Object body, Map<String, String> h, LuaValue cb) {
        HttpCore.put(url, body, h).async(cb);
    }

    // ==================== DELETE ====================

    public static void delete(String url, LuaValue cb) {
        HttpCore.delete(url).async(cb);
    }

    public static void delete(String url, Map<String, String> h, LuaValue cb) {
        HttpCore.delete(url, h).async(cb);
    }

    // ==================== 上传 ====================

    public static void upload(String url, Map<String, String> d, Map<String, String> f, LuaValue cb) {
        HttpCore.upload(url, d, f).async(cb);
    }

    public static void upload(String url, Map<String, String> d, Map<String, String> f, Map<String, String> h, LuaValue cb) {
        HttpCore.upload(url, d, f, h).async(cb);
    }
}