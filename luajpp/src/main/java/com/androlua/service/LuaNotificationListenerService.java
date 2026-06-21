package com.androlua.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.ServiceDelegate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

/**
 * Lua 通知监听服务。
 */
@SuppressWarnings("unused")
public class LuaNotificationListenerService extends NotificationListenerService implements LuaContext {

    private static LuaNotificationListenerService sInstance = null;
    private static final String sHostName = "notification";
    private static String mLuaPath = "notification.lua";
    private final ServiceDelegate mDelegate = new ServiceDelegate(this, sHostName);
    private TextToSpeech mTts;

    // ==================== 单例 & 开关 ====================

    public static LuaNotificationListenerService getInstance() {
        return sInstance;
    }

    public static void setEnabled(Context context) {
        setEnabled(context, null);
    }

    public static void setEnabled(Context context, String luaPath) {
        if (luaPath != null) mLuaPath = luaPath;
        ComponentName cn = new ComponentName(context, LuaNotificationListenerService.class);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        context.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    public static void setDisabled(Context context) {
        ComponentName cn = new ComponentName(context, LuaNotificationListenerService.class);
        context.getPackageManager().setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        if (sInstance != null) sInstance.stopSelf();
    }

    // ==================== 生命周期 ====================

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mDelegate.init(mLuaPath, new Object[0]);

        String defEngine = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH);
        initTTS(defEngine);
    }

    @Override
    public void onDestroy() {
        if (mTts != null) mTts.shutdown();
        mDelegate.destroy();
        sInstance = null;
        super.onDestroy();
    }

    // ==================== TTS ====================

    private void initTTS(String engine) {
        if (mTts != null) mTts.shutdown();
        mTts = new TextToSpeech(this, status -> LuaConfig.log("TTS init: " + status));
        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                mDelegate.runFunc("onTTSStart", utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                mDelegate.runFunc("onTTSDone", utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                mDelegate.runFunc("onTTSError", utteranceId);
            }
        });
    }

    public void speak(String text) {
        if (TextUtils.isEmpty(text)) return;
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), "");
    }

    public void stop() {
        mTts.stop();
    }

    public boolean isSpeaking() {
        return mTts.isSpeaking();
    }

    // ==================== 通知事件 ====================

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        mDelegate.runFunc("onNotificationPosted", sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        mDelegate.runFunc("onNotificationRemoved", sbn);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        mDelegate.runFunc("onListenerConnected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        mDelegate.runFunc("onListenerDisconnected");
        sInstance = null;
    }

    // ==================== 便捷方法 ====================

    public Object runFunc(String name, Object... args) {
        return mDelegate.runFunc(name, args);
    }

    public void sendMsg(String msg) {
        mDelegate.sendMsg(msg);
    }

    public void showToast(String text) {
        mDelegate.showToast(text);
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

    public org.luaj.Globals getLuaState() {
        return mDelegate.getLuaState();
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