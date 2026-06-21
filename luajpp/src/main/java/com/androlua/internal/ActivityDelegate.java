package com.androlua.internal;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import androidx.core.content.ContextCompat;

import com.androlua.service.LuaService;
import com.androlua.util.LuaBroadcastReceiver;

import org.luaj.Globals;
import org.luaj.LuaFunction;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

/**
 * Activity 委托类，处理生命周期、UI 交互、权限、Service 绑定等逻辑。
 */
public class ActivityDelegate extends BaseDelegate {

    /** 回调接口，让 LuaActivity 提供 UI 操作能力 */
    public interface UICallback {
        void applyDefaultView();
        void setTitle(CharSequence title);
        boolean isSetViewed();
        void handleError(Exception e);
        void handleVersionChanged(Bundle savedInstanceState);
    }

    private final Activity mActivity;
    private final UICallback mUICallback;
    private String mLuaPath = "main.lua";

    private LuaBroadcastReceiver mReceiver;
    private final HashMap<Integer, LuaFunction> mCallback = new HashMap<>();
    private LuaValue mOnKeyShortcut;
    private LuaValue mOnKeyDown;
    private LuaValue mOnKeyUp;
    private LuaValue mOnKeyLongPress;
    private LuaValue mOnTouchEvent;

    public ActivityDelegate(Activity activity, String hostName, UICallback callback) {
        super(activity, new LuaEngine(activity, activity, hostName));
        mActivity = activity;
        mUICallback = callback;
    }

    // ==================== 引擎生命周期（封装完整初始化流程） ====================

    /**
     * 初始化并启动 Lua 引擎。接管 LuaActivity.onCreate 中的全部初始化逻辑。
     */
    public void initEngine(Bundle savedInstanceState) {
        String luaPath = null;
        Intent intent = mActivity.getIntent();
        Uri data = intent != null ? intent.getData() : null;

        if (data != null) {
            String path = data.getPath();
            if (path != null && !path.isEmpty()) {
                File f = new File(path);
                if (f.isFile() && f.canRead()) {
                    mUICallback.setTitle(f.getName());
                    luaPath = path;
                }
            }
        } else {
            String path = getEngine().getLuaContext().getLuaPath();
            if (path == null || !new File(path).canRead()) {
                mUICallback.applyDefaultView();
                sendMsg("Lua path error: " + (path == null ? "path is null" : "file not readable"));
                return;
            }
            luaPath = new File(path).getAbsolutePath();
        }

        if (luaPath != null) mLuaPath = luaPath;

        final Object[] arg = java.util.Objects.requireNonNullElse(
                (Object[]) intent.getSerializableExtra("arg"), new Object[0]);

        setOnInitListener(new LuaEngine.OnInitListener() {
            @Override
            public void onSuccess() {
                mActivity.runOnUiThread(() -> {
                    onLuaInitialized();
                    try {
                        runMainFunc(arg);
                        if (!mUICallback.isSetViewed()) mUICallback.applyDefaultView();
                        mUICallback.handleVersionChanged(savedInstanceState);
                    } catch (Exception e) {
                        mUICallback.handleError(e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                mActivity.runOnUiThread(() -> mUICallback.handleError(e));
            }
        });

        init(mLuaPath, arg);
    }

    /**
     * 销毁引擎并清理资源。
     */
    public void destroyEngine() {
        runFunc("onDestroy");
        unregisterReceiver();
        destroy();
    }

    private void onLuaInitialized() {
        Globals g = getLuaState();
        mOnKeyShortcut = getLuaFunc(g, "onKeyShortcut");
        mOnKeyDown = getLuaFunc(g, "onKeyDown");
        mOnKeyUp = getLuaFunc(g, "onKeyUp");
        mOnKeyLongPress = getLuaFunc(g, "onKeyLongPress");
        mOnTouchEvent = getLuaFunc(g, "onTouchEvent");
    }

    private void runMainFunc(Object[] arg) {
        LuaEngine engine = getEngine();
        String name = new File(engine.getLuaPath()).getName();
        int idx = name.lastIndexOf(".");
        if (idx > 0) name = name.substring(0, idx);
        if (name.equals("welcome")) return;

        Globals g = engine.getLuaState();
        LuaValue f = g.get(name);
        if (!f.isfunction()) f = g.get("main");
        if (f.isfunction()) f.jcall(arg);
    }

    private LuaValue getLuaFunc(Globals g, String name) {
        LuaValue f = g.get(name);
        return f.isnil() ? null : f;
    }

    // ==================== 生命周期回调 ====================

    public void onNewIntent(Intent intent) {
        mActivity.setIntent(intent);
        runFunc("onNewIntent", intent);
    }

    public void onStart() {
        runFunc("onStart");
    }

    public void onResume() {
        runFunc("onResume");
    }

    public void onPause() {
        runFunc("onPause");
    }

    public void onStop() {
        runFunc("onStop");
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        runFunc("onRequestPermissionsResult", requestCode, permissions, grantResults);
    }

    // ==================== 菜单 ====================

    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        runFunc("onCreateOptionsMenu", menu);
        return true;
    }

    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        Object ret = !item.hasSubMenu() ? runFunc("onOptionsItemSelected", item) : null;
        if (ret instanceof Boolean && (Boolean) ret) return true;
        if (!item.hasSubMenu()) runFunc("onMenuItemSelected", item.getItemId(), item);
        return false;
    }

    // ==================== 按键事件 ====================

    public boolean onKeyShortcut(int keyCode, android.view.KeyEvent event) {
        if (mOnKeyShortcut != null) {
            try {
                Object ret = mOnKeyShortcut.jcall(keyCode, event);
                if (ret instanceof Boolean && (Boolean) ret) return true;
            } catch (org.luaj.LuaError e) {
                sendError("onKeyShortcut", e);
            }
        }
        return false;
    }

    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (mOnKeyDown != null) {
            try {
                Object ret = mOnKeyDown.jcall(keyCode, event);
                if (ret instanceof Boolean && (Boolean) ret) return true;
            } catch (org.luaj.LuaError e) {
                sendError("onKeyDown", e);
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (mOnKeyUp != null) {
            try {
                Object ret = mOnKeyUp.jcall(keyCode, event);
                if (ret instanceof Boolean && (Boolean) ret) return true;
            } catch (org.luaj.LuaError e) {
                sendError("onKeyUp", e);
            }
        }
        return false;
    }

    public boolean onKeyLongPress(int keyCode, android.view.KeyEvent event) {
        if (mOnKeyLongPress != null) {
            try {
                Object ret = mOnKeyLongPress.jcall(keyCode, event);
                if (ret instanceof Boolean && (Boolean) ret) return true;
            } catch (org.luaj.LuaError e) {
                sendError("onKeyLongPress", e);
            }
        }
        return false;
    }

    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (mOnTouchEvent != null) {
            try {
                Object ret = mOnTouchEvent.jcall(event);
                if (ret instanceof Boolean && (Boolean) ret) return true;
            } catch (org.luaj.LuaError e) {
                sendError("onTouchEvent", e);
            }
        }
        return false;
    }

    // ==================== 广播 ====================

    public Intent registerReceiver(IntentFilter filter) {
        if (mReceiver != null) {
            LuaConfig.runSafely(() -> mActivity.unregisterReceiver(mReceiver), "unregisterReceiver");
        }
        mReceiver = new LuaBroadcastReceiver((context, intent) -> runFunc("onReceive", context, intent));
        return ContextCompat.registerReceiver(
                mActivity, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void unregisterReceiver() {
        if (mReceiver != null) {
            LuaConfig.runSafely(() -> mActivity.unregisterReceiver(mReceiver), "unregisterReceiver");
            mReceiver = null;
        }
    }

    // ==================== Service ====================

    public boolean bindService(int flag) {
        return bindService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName comp, IBinder binder) {
                runFunc("onServiceConnected", comp, ((LuaService.LuaBinder) binder).getService());
            }

            @Override
            public void onServiceDisconnected(ComponentName comp) {
                runFunc("onServiceDisconnected", comp);
            }
        }, flag);
    }

    public boolean bindService(ServiceConnection conn, int flag) {
        return bindService("service.lua", conn, flag);
    }

    public boolean bindService(String path, ServiceConnection conn, int flag) {
        try {
            String fullPath = LuaIntentHelper.resolveLuaPath(getLuaDir(), path);
            LuaService.setEnabled(mActivity, fullPath);
            return mActivity.bindService(new Intent(mActivity, LuaService.class), conn, flag);
        } catch (FileNotFoundException e) {
            throw new org.luaj.LuaError(e);
        }
    }

    public void startService(String path, Object[] arg) throws FileNotFoundException {
        String fullPath = LuaIntentHelper.resolveLuaPath(getLuaDir(), path);
        LuaService.setEnabled(mActivity, fullPath);
    }

    public boolean stopService() {
        return mActivity.stopService(new Intent(mActivity, LuaService.class));
    }

    // ==================== ActivityResult 回调 ====================

    public void registerCallback(int code, LuaFunction function) {
        mCallback.put(code, function);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        LuaFunction func = mCallback.remove(requestCode);
        if (func != null) func.call(CoerceJavaToLua.coerce(data));

        if (data != null) {
            String name = data.getStringExtra("name");
            if (name != null) {
                Object[] res = (Object[]) data.getSerializableExtra("data");
                if (res == null) {
                    runFunc("onResult", name);
                } else {
                    Object[] arg = new Object[res.length + 1];
                    arg[0] = name;
                    System.arraycopy(res, 0, arg, 1, res.length);
                    Object ret = runFunc("onResult", arg);
                    if (ret instanceof Boolean && (Boolean) ret) return true;
                }
            }
        }
        runFunc("onActivityResult", requestCode, resultCode, data);
        return false;
    }

    // ==================== LuaMetaTable 支持 ====================

    public LuaValue __index(LuaValue key) {
        return getLuaState().get(key);
    }

    public void __newindex(LuaValue key, LuaValue value) {
        getLuaState().set(key, value);
    }
}