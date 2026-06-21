package com.androlua.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.androlua.LuaApplication;
import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.BaseDelegate;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaEngine;

import com.google.android.material.textview.MaterialTextView;

import org.luaj.Globals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 欢迎/启动页 Activity，负责版本检测、资源解压、执行 welcome.lua。
 */
public class Welcome extends AppCompatActivity implements LuaContext {

    private final BaseDelegate mDelegate = new BaseDelegate(this,
            new LuaEngine(this, this, getHostName())) {};
    private String mVersionName;
    private String mOldVersionName;
    private boolean isVersionChanged;

    // ==================== 可覆盖的配置方法 ====================

    /**
     * 版本更新后跳转的目标 Activity
     */
    protected Class<?> getTargetActivity() {
        return Main.class;
    }

    /**
     * 欢迎脚本文件名
     */
    protected String getWelcomeScriptName() {
        return "welcome.lua";
    }

    /**
     * 宿主名称
     */
    protected String getHostName() {
        return "welcome";
    }

    /**
     * 资源解压完成回调
     */
    protected void onUpdateExtracted() {
    }

    // ==================== 生命周期 ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());

        checkAndUpdate();

        String welcomePath = mDelegate.getLuaPath(getWelcomeScriptName());
        if (new File(welcomePath).exists()) {
            mDelegate.init(welcomePath, new Object[]{mVersionName, mOldVersionName});
        } else {
            startMainActivity();
        }
    }

    /**
     * 创建默认视图
     */
    protected View createContentView() {
        MaterialTextView tv = new MaterialTextView(this);
        tv.setText("Powered by LuaJ++");
        tv.setTextColor(0xff888888);
        tv.setGravity(Gravity.TOP);
        return tv;
    }

    // ==================== 版本检测 & 资源解压 ====================

    private void checkAndUpdate() {
        try {
            PackageInfo pkgInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            SharedPreferences sp = getSharedPreferences("appInfo", 0);

            long lastTime = pkgInfo.lastUpdateTime;
            long oldLastTime = sp.getLong("lastUpdateTime", 0);

            mVersionName = pkgInfo.versionName;
            mOldVersionName = sp.getString("versionName", "");

            if (!mVersionName.equals(mOldVersionName)) {
                sp.edit().putString("versionName", mVersionName).apply();
                isVersionChanged = true;
            }

            if (oldLastTime != lastTime) {
                sp.edit().putLong("lastUpdateTime", lastTime).apply();
                LuaApplication app = (LuaApplication) getApplication();
                unzipFromApk("lua/", new File(app.getMdDir()));
                unzipFromApk("assets/", new File(app.getLocalDir()));
                onUpdateExtracted();
            }
        } catch (PackageManager.NameNotFoundException e) {
            LuaConfig.logError("checkAndUpdate", e);
        }
    }

    /**
     * 从 APK 中解压指定目录
     */
    protected void unzipFromApk(String sourceDir, File destDir) {
        if (destDir.exists()) deleteDirectory(destDir);
        if (!destDir.mkdirs()) return;

        try (ZipInputStream zis = new ZipInputStream(
                new FileInputStream(getApplicationInfo().publicSourceDir))) {
            byte[] buffer = new byte[8192];
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith(sourceDir) && !entry.isDirectory()) {
                    String relativePath = entryName.substring(sourceDir.length());
                    File targetFile = new File(destDir, relativePath);

                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }

                    if (relativePath.endsWith(".dex")) {
                        targetFile.setReadOnly();
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            LuaConfig.logError("unzipFromApk", e);
        }
    }

    private void deleteDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteDirectory(child);
                else child.delete();
            }
        }
        dir.delete();
    }

    // ==================== 跳转主界面 ====================

    /**
     * 启动目标 Activity
     */
    public void startMainActivity() {
        Intent intent = new Intent(this, getTargetActivity());
        if (isVersionChanged) {
            intent.putExtra("isVersionChanged", true);
            intent.putExtra("newVersionName", mVersionName);
            intent.putExtra("oldVersionName", mOldVersionName);
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        mDelegate.destroy();
        super.onDestroy();
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
    public Globals getLuaState() {
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
    public void sendMsg(String msg) {
        mDelegate.sendMsg(msg);
    }

    @Override
    public void sendError(String title, Exception msg) {
        mDelegate.sendError(title, msg);
    }

    public int getWidth() {
        return mDelegate.getWidth();
    }

    public int getHeight() {
        return mDelegate.getWidth();
    }

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
}