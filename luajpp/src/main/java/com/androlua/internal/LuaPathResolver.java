package com.androlua.internal;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import com.androlua.LuaApplication;

import java.io.File;

/**
 * Lua 路径解析器，统一管理 luaDir/rootDir/extDir 等路径。
 */
public class LuaPathResolver {

    private final Application mApplication;
    private String mLuaDir;
    private String mRootDir;
    private String mExtDir;

    public LuaPathResolver() {
        mApplication = LuaApplication.getInstance();
    }

    /**
     * 从路径向上查找项目根目录
     */
    public static String findRoot(String startPath) {
        String path = startPath.replaceAll("/$", "");
        String[] targetDirs = {"files", "assets", "assets_bin"};

        for (int i = 0; i < 10; i++) {
            for (String dir : targetDirs) {
                if (path.endsWith("/" + dir)) {
                    return path;
                }
            }
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash <= 0) break;
            path = path.substring(0, lastSlash);
        }

        return startPath;
    }

    /**
     * 构建 Lua require 搜索路径
     */
    public static String buildLuaPath(String dir) {
        return dir + "/?.lua;" + dir + "/lua/?.lua;" + dir + "/?/init.lua";
    }

    // ==================== luaDir ====================
    public void setLuaDir(String luaDir) {
        mLuaDir = luaDir;
    }

    public String getLuaDir() {
        if (mLuaDir == null) {
            mLuaDir = mApplication.getFilesDir().getAbsolutePath();
        }
        return mLuaDir;
    }

    public String getLuaDir(String dir) {
        return new File(getLuaDir(), dir).getAbsolutePath();
    }

    // ==================== rootDir ====================
    public void setRootDir(String rootDir) {
        mRootDir = rootDir;
    }

    public String getRootDir() {
        if (mRootDir == null) {
            String searchPath = (mLuaDir != null) ? mLuaDir : mApplication.getFilesDir().getAbsolutePath();
            mRootDir = findRoot(searchPath);
        }
        return mRootDir;
    }

    // ==================== extDir ====================
    public void setExtDir(String extDir) {
        mExtDir = extDir;
    }

    public String getExtDir() {
        if (mExtDir != null) return mExtDir;
        File d = new File(Environment.getExternalStorageDirectory(), "LuaJ");
        if (!d.exists()) d.mkdirs();
        mExtDir = d.getAbsolutePath();
        return mExtDir;
    }

    public String getExtDir(String dir) {
        File d = new File(getExtDir(), dir);
        if (!d.exists()) d.mkdirs();
        return d.getAbsolutePath();
    }

    // ==================== 路径组合 ====================
    public String getLuaPath(String path) {
        return new File(getLuaDir(), path).getAbsolutePath();
    }

    public String getLuaPath(String dir, String name) {
        return new File(getLuaDir(dir), name).getAbsolutePath();
    }

    public String getExtPath(String path) {
        return new File(getExtDir(), path).getAbsolutePath();
    }

    public String getExtPath(String dir, String name) {
        return new File(getExtDir(dir), name).getAbsolutePath();
    }

    /**
     * 应用内置 Lua 库目录
     */
    public String getAppLuaDir() {
        return mApplication.getDir("lua", Context.MODE_PRIVATE).getAbsolutePath();
    }
}