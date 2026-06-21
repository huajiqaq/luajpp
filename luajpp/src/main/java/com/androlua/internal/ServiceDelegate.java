package com.androlua.internal;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;

import androidx.core.content.ContextCompat;

import com.androlua.service.LuaService;
import com.androlua.util.LuaBroadcastReceiver;

import org.luaj.Globals;
import org.luaj.LuaValue;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Service 委托类，继承 BaseDelegate 获得引擎生命周期管理能力。
 */
public class ServiceDelegate extends BaseDelegate {

    private final Service mService;
    private String mLuaPath = "main.lua";

    private LuaBroadcastReceiver mReceiver;

    public ServiceDelegate(Service service, String hostName) {
        super(service, new LuaEngine(service, service, hostName));
        mService = service;
    }

    // ==================== 引擎生命周期 ====================

    public void initEngine() {
        String luaPath = getEngine().getLuaContext().getLuaPath();
        if (luaPath == null || !new File(luaPath).canRead()) {
            sendMsg("Lua path error: " + (luaPath == null ? "path is null" : "file not readable"));
            return;
        }
        mLuaPath = new File(luaPath).getAbsolutePath();

        setOnInitListener(new LuaEngine.OnInitListener() {
            @Override
            public void onSuccess() {
                runMainFunc();
            }

            @Override
            public void onError(Exception e) {
                sendError("Service Init", e);
            }
        });

        init(mLuaPath, null);
    }

    public void destroyEngine() {
        runFunc("onDestroy");
        unregisterReceiver();
        destroy();
    }

    private void runMainFunc() {
        String name = new java.io.File(getEngine().getLuaPath()).getName();
        int idx = name.lastIndexOf(".");
        if (idx > 0) name = name.substring(0, idx);

        Globals g = getEngine().getLuaState();
        LuaValue f = g.get(name);
        if (!f.isfunction()) f = g.get("main");
        if (f.isfunction()) f.jcall();
    }

    // ==================== Service 生命周期回调 ====================

    public void onNewIntent(Intent intent) {
        runFunc("onNewIntent", intent);
    }

    public void onStart() {
        runFunc("onStart");
    }

    public void onDestroy() {
        runFunc("onDestroy");
    }

    // ==================== 广播 ====================

    public Intent registerReceiver(IntentFilter filter) {
        if (mReceiver != null) {
            LuaConfig.runSafely(() -> mService.unregisterReceiver(mReceiver), "unregisterReceiver");
        }
        mReceiver = new LuaBroadcastReceiver((context, intent) -> runFunc("onReceive", context, intent));
        return ContextCompat.registerReceiver(
                mService, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void unregisterReceiver() {
        if (mReceiver != null) {
            LuaConfig.runSafely(() -> mService.unregisterReceiver(mReceiver), "unregisterReceiver");
            mReceiver = null;
        }
    }

    // ==================== Service 绑定 ====================

    public boolean bindService(ServiceConnection conn, int flag) {
        return bindService("service.lua", conn, flag);
    }

    public boolean bindService(String path, ServiceConnection conn, int flag) {
        try {
            String fullPath = LuaIntentHelper.resolveLuaPath(getLuaDir(), path);
            LuaService.setEnabled(mService, fullPath);
            return mService.bindService(new Intent(mService, LuaService.class), conn, flag);
        } catch (FileNotFoundException e) {
            throw new org.luaj.LuaError(e);
        }
    }

    public void startService(String path, Object[] arg) throws FileNotFoundException {
        String fullPath = LuaIntentHelper.resolveLuaPath(getLuaDir(), path);
        LuaService.setEnabled(mService, fullPath);
    }

    public boolean stopService() {
        return mService.stopService(new Intent(mService, LuaService.class));
    }

    // ==================== 便捷方法 ====================

    public boolean runBooleanFunc(String name, Object... args) {
        return getEngine().runBooleanFunc(name, args);
    }

    public void showToast(String text) {
        getEngine().showToast(text);
    }

    // ==================== LuaMetaTable 支持 ====================

    public org.luaj.LuaValue __index(org.luaj.LuaValue key) {
        return getLuaState().get(key);
    }

    public void __newindex(org.luaj.LuaValue key, org.luaj.LuaValue value) {
        getLuaState().set(key, value);
    }
}