package com.androlua.internal;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Callable;

/**
 * 框架全局配置中心，管理 debug 开关、日志标签、HTTP 超时等。
 */
public final class LuaConfig {

    private static final String DEFAULT_TAG = "LuaJ";
    private static final int MIN_TIMEOUT = 1000;
    private static final int MAX_TIMEOUT = 30000;
    private static final int MIN_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 32;

    private static volatile boolean sDebug = false;
    private static volatile String sTag = DEFAULT_TAG;
    private static volatile int sHttpTimeout = 6000;
    private static volatile int sIoPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static volatile boolean sSslTrustAll = true;
    private static volatile LogLevel sLogLevel = LogLevel.DEBUG;

    private LuaConfig() {
    }

    // ==================== 基础配置 ====================

    public static boolean isDebug() {
        return sDebug;
    }

    public static void setDebug(boolean debug) {
        if (sDebug != debug) {
            sDebug = debug;
            LuaLog.getInstance().setDebug(debug);
            log("Debug mode " + (debug ? "enabled" : "disabled"));
        }
    }

    @NonNull
    public static String getTag() {
        return sTag;
    }

    public static void setTag(@NonNull String tag) {
        sTag = !tag.isEmpty() ? tag : DEFAULT_TAG;
    }

    // ==================== 网络配置 ====================

    public static int getHttpTimeout() {
        return sHttpTimeout;
    }

    public static void setHttpTimeout(int timeoutMs) {
        sHttpTimeout = clamp(timeoutMs, MIN_TIMEOUT, MAX_TIMEOUT);
    }

    public static boolean isSslTrustAll() {
        return sSslTrustAll;
    }

    public static void setSslTrustAll(boolean trustAll) {
        sSslTrustAll = trustAll;
    }

    // ==================== 线程池配置 ====================

    public static int getIoPoolSize() {
        return sIoPoolSize;
    }

    public static void setIoPoolSize(int size) {
        sIoPoolSize = clamp(size, MIN_POOL_SIZE, MAX_POOL_SIZE);
    }

    // ==================== 日志配置 ====================

    public enum LogLevel {
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR),
        NONE(-1);

        final int priority;

        LogLevel(int priority) {
            this.priority = priority;
        }
    }

    public static void setLogLevel(LogLevel level) {
        sLogLevel = level != null ? level : LogLevel.DEBUG;
    }

    public static LogLevel getLogLevel() {
        return sLogLevel;
    }

    // ==================== 日志输出 ====================

    public static void log(String msg) {
        log(LogLevel.DEBUG, msg, null);
    }

    public static void log(String msg, Throwable t) {
        log(LogLevel.DEBUG, msg, t);
    }

    public static void logInfo(String msg) {
        log(LogLevel.INFO, msg, null);
    }

    public static void logInfo(String msg, Throwable t) {
        log(LogLevel.INFO, msg, t);
    }

    public static void logWarn(String msg) {
        log(LogLevel.WARN, msg, null);
    }

    public static void logWarn(String msg, Throwable t) {
        log(LogLevel.WARN, msg, t);
    }

    public static void logError(String msg) {
        log(LogLevel.ERROR, msg, null);
    }

    public static void logError(String msg, Throwable t) {
        log(LogLevel.ERROR, msg, t);
    }

    private static void log(@NonNull LogLevel level, @Nullable String msg, @Nullable Throwable t) {
        if (sLogLevel.priority > level.priority) return;

        String text = msg != null ? msg : "";
        if (t != null && level.priority >= LogLevel.WARN.priority) {
            text += "\n" + Log.getStackTraceString(t);
        }

        switch (level) {
            case DEBUG -> {
                if (sDebug) Log.d(sTag, text);
            }
            case INFO -> Log.i(sTag, text);
            case WARN -> Log.w(sTag, text);
            case ERROR -> Log.e(sTag, text);
        }
    }

    // ==================== 安全执行 ====================

    /**
     * 安全执行 Runnable
     */
    public static void runSafely(@Nullable Runnable action, @NonNull String tag) {
        if (action == null) return;
        try {
            action.run();
        } catch (Exception e) {
            logError(tag, e);
        }
    }

    /**
     * 安全执行带返回值的操作，失败返回 null
     */
    @Nullable
    public static <T> T runSafely(@Nullable Callable<T> action, @NonNull String tag) {
        if (action == null) return null;
        try {
            return action.call();
        } catch (Exception e) {
            logError(tag, e);
            return null;
        }
    }

    /**
     * 安全执行，带默认值
     */
    @Nullable
    public static <T> T runSafely(@Nullable Callable<T> action, @Nullable T defaultValue, @NonNull String tag) {
        T result = runSafely(action, tag);
        return result != null ? result : defaultValue;
    }

    // ==================== 调试辅助 ====================

    public static void dumpConfig() {
        if (!sDebug) return;
        String config = "=== LuaConfig ===\n"
                + "  debug: " + sDebug + "\n"
                + "  tag: " + sTag + "\n"
                + "  httpTimeout: " + sHttpTimeout + "\n"
                + "  ioPoolSize: " + sIoPoolSize + "\n"
                + "  sslTrustAll: " + sSslTrustAll + "\n"
                + "  logLevel: " + sLogLevel;
        Log.d(sTag, config);
    }

    // ==================== 内部方法 ====================

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}