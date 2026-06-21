package com.androlua.internal;

import android.content.Context;
import android.net.Uri;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;

import org.luaj.Globals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

/**
 * Delegate 基类，提取 ActivityDelegate 和 ServiceDelegate 的公共代码。
 */
public abstract class BaseDelegate implements LuaContext {

    private final Context mContext;
    private final LuaEngine mEngine;

    protected BaseDelegate(Context context, LuaEngine engine) {
        mContext = context;
        mEngine = engine;
    }

    // ==================== 引擎生命周期 ====================

    protected LuaEngine getEngine() {
        return mEngine;
    }

    public void setOnInitListener(LuaEngine.OnInitListener listener) {
        mEngine.setOnInitListener(listener);
    }

    public void init(String luaPath, Object[] arg) {
        mEngine.init(luaPath, arg);
    }

    public void destroy() {
        mEngine.destroy();
    }

    // ==================== 脚本执行 ====================

    public Object runFunc(String name, Object... args) {
        return mEngine.runFunc(name, args);
    }

    public void sendMsg(String msg) {
        mEngine.sendMsg(msg);
    }

    public void sendError(String title, Exception msg) {
        mEngine.sendError(title, msg);
    }

    // ==================== Activity 跳转 ====================

    public void newActivity(String path) throws FileNotFoundException {
        LuaIntentHelper.newActivity(mContext, getLuaDir(), path);
    }

    public void newActivity(String path, Object[] arg) throws FileNotFoundException {
        LuaIntentHelper.newActivity(mContext, getLuaDir(), path, arg);
    }

    public void newActivity(int req, String path) throws FileNotFoundException {
        LuaIntentHelper.newActivity(mContext, getLuaDir(), req, path);
    }

    public void newActivity(int req, String path, Object[] arg) throws FileNotFoundException {
        LuaIntentHelper.newActivity(mContext, getLuaDir(), req, path, arg);
    }

    public void newActivity(int req, String path, Object[] arg, boolean newDocument) throws FileNotFoundException {
        LuaIntentHelper.newActivity(mContext, getLuaDir(), req, path, arg, newDocument);
    }

    // ==================== 文件工具 ====================

    public Uri getUriForFile(File path) {
        return LuaIntentHelper.getUriForFile(mContext, path);
    }

    public String getPathFromUri(Uri uri) {
        return LuaIntentHelper.getPathFromUri(mContext, uri);
    }

    public void installApk(String path) {
        LuaIntentHelper.installApk(mContext, path);
    }

    public void openFile(String path) {
        LuaIntentHelper.openFile(mContext, path);
    }

    public void shareFile(String path) {
        LuaIntentHelper.shareFile(mContext, path);
    }

    // ==================== LuaContext 接口实现 ====================

    @Override
    public ArrayList<ClassLoader> getClassLoaders() {
        return mEngine.getClassLoaders();
    }

    @Override
    public void call(String func, Object... args) {
        mEngine.call(func, args);
    }

    @Override
    public void set(String name, Object value) {
        mEngine.set(name, value);
    }

    @Override
    public String getLuaPath() {
        return mEngine.getLuaPath();
    }

    @Override
    public String getLuaPath(String path) {
        return mEngine.getLuaPath(path);
    }

    @Override
    public String getLuaPath(String dir, String name) {
        return mEngine.getPathResolver().getLuaPath(dir, name);
    }

    @Override
    public String getLuaDir() {
        return mEngine.getLuaDir();
    }

    @Override
    public String getLuaDir(String dir) {
        return mEngine.getPathResolver().getLuaDir(dir);
    }

    @Override
    public String getLuaExtDir() {
        return mEngine.getPathResolver().getExtDir();
    }

    @Override
    public String getLuaExtDir(String dir) {
        return mEngine.getPathResolver().getExtDir(dir);
    }

    @Override
    public void setLuaExtDir(String dir) {
        mEngine.getPathResolver().setExtDir(dir);
    }

    @Override
    public String getLuaExtPath(String path) {
        return mEngine.getPathResolver().getExtPath(path);
    }

    @Override
    public String getLuaExtPath(String dir, String name) {
        return mEngine.getPathResolver().getExtPath(dir, name);
    }

    @Override
    public String getRootDir() {
        return mEngine.getRootDir();
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public Globals getLuaState() {
        return mEngine.getLuaState();
    }

    @Override
    public Object doFile(String path, Object... arg) {
        return mEngine.doFile(path, arg);
    }

    @Override
    public int getWidth() {
        return mEngine.getWidth();
    }

    @Override
    public int getHeight() {
        return mEngine.getWidth();
    }

    @Override
    public float getDensity() {
        return mEngine.getDensity();
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return com.androlua.LuaApplication.getInstance().getGlobalData();
    }

    @Override
    public Map<String, ?> getSharedData() {
        return com.androlua.LuaApplication.getInstance().getSharedData();
    }

    @Override
    public Object getSharedData(String key) {
        return com.androlua.LuaApplication.getInstance().getSharedData(key);
    }

    @Override
    public Object getSharedData(String key, Object def) {
        return com.androlua.LuaApplication.getInstance().getSharedData(key, def);
    }

    @Override
    public boolean setSharedData(String key, Object value) {
        return com.androlua.LuaApplication.getInstance().setSharedData(key, value);
    }

    @Override
    public void regGc(LuaGcable obj) {
        mEngine.regGc(obj);
    }

    @Override
    public InputStream findResource(String name) {
        return mEngine.findResource(name);
    }

    @Override
    public String findFile(String filename) {
        return mEngine.findFile(filename);
    }
}