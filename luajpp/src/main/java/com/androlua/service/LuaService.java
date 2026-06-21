package com.androlua.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.ServiceDelegate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

/**
 * Lua 服务。
 * <p>
 * 使用 {@link ServiceDelegate} 消除重复委托代码，
 * 本类只负责 Service 生命周期转发。
 */
@SuppressWarnings("unused")
public class LuaService extends Service implements LuaContext {

    private static LuaService sInstance;
    private static final String sHostName = "service";
    private static String sLuaPath = "service.lua";

    private final ServiceDelegate mDelegate = new ServiceDelegate(this, sHostName);

    // ==================== 单例 & 开关 ====================

    public static LuaService getInstance() {
        return sInstance;
    }

    public static void setEnabled(Context context) {
        setEnabled(context, null);
    }

    public static void setEnabled(Context context, String luaPath) {
        if (luaPath != null) sLuaPath = luaPath;
        context.startService(new Intent(context, LuaService.class));
    }

    public static void setDisabled(Context context) {
        context.stopService(new Intent(context, LuaService.class));
    }

    // ==================== 生命周期 ====================

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mDelegate.init(sLuaPath, new Object[0]);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mDelegate.runFunc("onStartCommand", intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mDelegate.destroy();
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LuaBinder();
    }

    // ==================== Binder ====================

    public class LuaBinder extends Binder {
        public LuaService getService() {
            return LuaService.this;
        }
    }

    // ==================== 便捷方法 ====================

    public Object runFunc(String name, Object... args) {
        return mDelegate.runFunc(name, args);
    }

    public boolean runBooleanFunc(String name, Object... args) {
        return mDelegate.runBooleanFunc(name, args);
    }

    public void sendMsg(String msg) {
        mDelegate.sendMsg(msg);
    }

    public void showToast(String text) {
        mDelegate.showToast(text);
    }

    public org.luaj.Globals getLuaState() {
        return mDelegate.getLuaState();
    }

    public ServiceDelegate getDelegate() {
        return mDelegate;
    }

    // ==================== LuaContext 接口（纯转发到 mDelegate） ====================

    @Override
    public ArrayList<ClassLoader> getClassLoaders() {
        return mDelegate.getClassLoaders();
    }

    @Override
    public void call(String func, Object... args) {
        mDelegate.call(func, args);
    }

    @Override
    public void set(String name, Object value) {
        mDelegate.set(name, value);
    }

    @Override
    public String getLuaPath() {
        return mDelegate.getLuaPath();
    }

    @Override
    public String getLuaPath(String path) {
        return mDelegate.getLuaPath(path);
    }

    @Override
    public String getLuaPath(String dir, String name) {
        return mDelegate.getLuaPath(dir, name);
    }

    @Override
    public String getLuaDir() {
        return mDelegate.getLuaDir();
    }

    @Override
    public String getLuaDir(String dir) {
        return mDelegate.getLuaDir(dir);
    }

    @Override
    public String getLuaExtDir() {
        return mDelegate.getLuaExtDir();
    }

    @Override
    public String getLuaExtDir(String dir) {
        return mDelegate.getLuaExtDir(dir);
    }

    @Override
    public void setLuaExtDir(String dir) {
        mDelegate.setLuaExtDir(dir);
    }

    @Override
    public String getLuaExtPath(String path) {
        return mDelegate.getLuaExtPath(path);
    }

    @Override
    public String getLuaExtPath(String dir, String name) {
        return mDelegate.getLuaExtPath(dir, name);
    }

    @Override
    public String getRootDir() {
        return mDelegate.getRootDir();
    }

    @Override
    public Context getContext() {
        return mDelegate.getContext();
    }

    @Override
    public Object doFile(String path, Object... arg) {
        return mDelegate.doFile(path, arg);
    }

    @Override
    public InputStream findResource(String name) {
        return mDelegate.findResource(name);
    }

    @Override
    public String findFile(String filename) {
        return mDelegate.findFile(filename);
    }

    @Override
    public void sendError(String title, Exception msg) {
        mDelegate.sendError(title, msg);
    }

    @Override
    public int getWidth() {
        return mDelegate.getWidth();
    }

    @Override
    public int getHeight() {
        return mDelegate.getWidth();
    }

    @Override
    public float getDensity() {
        return mDelegate.getDensity();
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return mDelegate.getGlobalData();
    }

    @Override
    public Map<String, ?> getSharedData() {
        return mDelegate.getSharedData();
    }

    @Override
    public Object getSharedData(String key) {
        return mDelegate.getSharedData(key);
    }

    @Override
    public Object getSharedData(String key, Object def) {
        return mDelegate.getSharedData(key, def);
    }

    @Override
    public boolean setSharedData(String key, Object value) {
        return mDelegate.setSharedData(key, value);
    }

    @Override
    public void regGc(LuaGcable obj) {
        mDelegate.regGc(obj);
    }

    // ==================== Activity 跳转 ====================

    public void newActivity(String path) throws FileNotFoundException {
        mDelegate.newActivity(path);
    }

    public void newActivity(String path, Object[] arg) throws FileNotFoundException {
        mDelegate.newActivity(path, arg);
    }

    public void newActivity(int req, String path) throws FileNotFoundException {
        mDelegate.newActivity(req, path);
    }

    public void newActivity(int req, String path, Object[] arg) throws FileNotFoundException {
        mDelegate.newActivity(req, path, arg);
    }

    public void newActivity(int req, String path, Object[] arg, boolean newDocument) throws FileNotFoundException {
        mDelegate.newActivity(req, path, arg, newDocument);
    }

    // ==================== 文件相关 ====================

    public android.net.Uri getUriForFile(File path) {
        return mDelegate.getUriForFile(path);
    }

    public String getPathFromUri(android.net.Uri uri) {
        return mDelegate.getPathFromUri(uri);
    }

    public void installApk(String path) {
        mDelegate.installApk(path);
    }

    public void openFile(String path) {
        mDelegate.openFile(path);
    }

    public void shareFile(String path) {
        mDelegate.shareFile(path);
    }
}