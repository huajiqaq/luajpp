package com.androlua.service;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.ServiceDelegate;

import org.luaj.Globals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

/**
 * Lua 壁纸服务。
 */
@SuppressWarnings("unused")
public class LuaWallpaperService extends WallpaperService implements LuaContext {

    private static LuaWallpaperService sInstance;
    private static final String sHostName = "wallpaper";
    private static String mLuaPath = "wallpaper.lua";

    private final ServiceDelegate mDelegate = new ServiceDelegate(this, sHostName);
    private SurfaceHolder mHolder;

    // ==================== 单例 & 开关 ====================

    public static LuaWallpaperService getInstance() {
        return sInstance;
    }

    public SurfaceHolder getHolder() {
        return mHolder;
    }

    public static void setEnabled(Context context) {
        setEnabled(context, null);
    }

    public static void setEnabled(Context context, String luaPath) {
        if (luaPath != null) mLuaPath = luaPath;
        ComponentName cn = new ComponentName(context, LuaWallpaperService.class);
        context.getPackageManager().setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        context.startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, cn));
    }

    public static void setDisabled(Context context) {
        ComponentName cn = new ComponentName(context, LuaWallpaperService.class);
        context.getPackageManager().setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        if (sInstance != null) sInstance.stopSelf();
    }

    // ==================== 生命周期 ====================

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mDelegate.init(mLuaPath, new Object[0]);
    }

    @Override
    public Engine onCreateEngine() {
        return new LuaWallpaperEngine();
    }

    @Override
    public void onDestroy() {
        mDelegate.destroy();
        sInstance = null;
        super.onDestroy();
    }

    // ==================== 壁纸引擎 ====================

    private class LuaWallpaperEngine extends Engine {
        @Override
        public void onVisibilityChanged(boolean visible) {
            mDelegate.runFunc("onVisibilityChanged", visible);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            mHolder = holder;
            mDelegate.runFunc("onSurfaceCreated", holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            mDelegate.runFunc("onSurfaceDestroyed", holder);
            mHolder = null;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mHolder = holder;
            mDelegate.runFunc("onSurfaceChanged", holder, format, width, height);
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            mHolder = holder;
            mDelegate.runFunc("onSurfaceRedrawNeeded", holder);
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

    public Globals getLuaState() {
        return mDelegate.getLuaState();
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

    public Uri getUriForFile(File path) {
        return mDelegate.getUriForFile(path);
    }

    public String getPathFromUri(Uri uri) {
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