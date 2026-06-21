package com.androlua;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.androlua.util.CrashHandler;
import com.androlua.util.LuaUtil;

import org.luaj.LuaTable;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lua 应用基类，管理全局状态、通知渠道和崩溃处理。
 */
public class LuaApplication extends Application {

    private static LuaApplication sInstance;
    private static final Map<String, Object> sGlobalData = new HashMap<>();

    private String mLocalDir;
    private String mLuaMdDir;

    public static LuaApplication getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        mLocalDir = getFilesDir().getAbsolutePath();
        mLuaMdDir = getDir("lua", Context.MODE_PRIVATE).getAbsolutePath();

        // 清理 dex 缓存
        File dexDir = getExternalFilesDir("dexfiles");
        if (dexDir != null) {
            LuaUtil.rmDir(dexDir);
        }

        CrashHandler.getInstance().init(this);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "lua_service_channel",
                    "Lua 服务",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Lua 后台服务通知");
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public String getLocalDir() {
        return mLocalDir;
    }

    public String getMdDir() {
        return mLuaMdDir;
    }

    // 全局数据

    public Map<String, Object> getGlobalData() {
        return sGlobalData;
    }

    // SharedData

    public Map<String, ?> getSharedData() {
        return PreferenceManager.getDefaultSharedPreferences(this).getAll();
    }

    public Object getSharedData(String key) {
        return PreferenceManager.getDefaultSharedPreferences(this).getAll().get(key);
    }

    public Object getSharedData(String key, Object defValue) {
        Object value = PreferenceManager.getDefaultSharedPreferences(this).getAll().get(key);
        return value != null ? value : defValue;
    }

    public boolean setSharedData(String key, Object value) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();

        switch (value) {
            case null:
                editor.remove(key);
                break;
            case String s:
                editor.putString(key, s);
                break;
            case Long l:
                editor.putLong(key, l);
                break;
            case Integer i:
                editor.putInt(key, i);
                break;
            case Float v:
                editor.putFloat(key, v);
                break;
            case Boolean b:
                editor.putBoolean(key, b);
                break;
            case LuaTable luaTable: {
                Set<String> set = new HashSet<>();
                for (Object item : luaTable.values()) {
                    set.add(String.valueOf(item));
                }
                editor.putStringSet(key, set);
                break;
            }
            case Set<?> set1: {
                @SuppressWarnings("unchecked")
                Set<String> set = (Set<String>) set1;
                editor.putStringSet(key, set);
                break;
            }
            default:
                return false;
        }

        return editor.commit();
    }
}
