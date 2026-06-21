package com.androlua.internal;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 集中式 Lua 日志管理器。
 */
public final class LuaLog {

    private static final LuaLog INSTANCE = new LuaLog();
    private static final int MAX_LOG_SIZE = 500;
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private final ArrayList<String> mLogs = new ArrayList<>();
    private boolean mDebug = false;

    private LuaLog() {
    }

    public static LuaLog getInstance() {
        return INSTANCE;
    }

    // ==================== 添加日志 ====================

    /**
     * 添加普通日志
     */
    public void add(String msg) {
        String line = FORMAT.format(new Date()) + " " + msg;
        synchronized (mLogs) {
            trimIfNeeded();
            mLogs.add(line);
        }
        if (mDebug) Log.i(LuaConfig.getTag(), msg);
    }

    /**
     * 添加错误日志
     */
    public void addError(String tag, Throwable e) {
        add(tag + ": " + e.getMessage());
        LuaConfig.logError(tag, e);
    }

    // ==================== 读取日志 ====================

    /**
     * 获取日志列表引用
     * 注意：此列表会随日志更新而变化，不要直接修改
     */
    public List<String> getLogs() {
        synchronized (mLogs) {
            return mLogs;
        }
    }

    public int size() {
        synchronized (mLogs) {
            return mLogs.size();
        }
    }

    public String get(int index) {
        synchronized (mLogs) {
            return index >= 0 && index < mLogs.size() ? mLogs.get(index) : null;
        }
    }

    public void clear() {
        synchronized (mLogs) {
            mLogs.clear();
        }
    }

    // ==================== 配置 ====================

    public boolean isDebug() {
        return mDebug;
    }

    public void setDebug(boolean debug) {
        mDebug = debug;
    }

    // ==================== 内部方法 ====================

    private void trimIfNeeded() {
        if (mLogs.size() >= MAX_LOG_SIZE) {
            mLogs.subList(0, mLogs.size() / 2).clear();
        }
    }
}