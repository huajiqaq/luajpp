package com.androlua.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;

import com.androlua.LuaApplication;
import com.androlua.internal.LuaLog;
import com.androlua.internal.LuaResources;

import org.luaj.LuaError;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;

/**
 * LuaJ 动态加载器
 * 用于加载 dex/jar/apk 中的类和资源
 */
@SuppressWarnings("JavaReflectionMemberAccess")
public class LuaDexLoader {

    private static final Map<String, LuaDexClassLoader> sDexCache = new HashMap<>();
    private final ArrayList<ClassLoader> mDexList = new ArrayList<>();
    private final Map<String, String> mLibCache = new HashMap<>();

    private final Context mContext;
    private final String mLuaDir;
    private final String mOdexDir;

    private AssetManager mAssetManager;
    private LuaResources mResources;
    private Resources.Theme mTheme;

    public LuaDexLoader(Context context, String dir) {
        mContext = context;
        mLuaDir = dir;
        mOdexDir = context.getDir("odex", Context.MODE_PRIVATE).getAbsolutePath();
    }

    public Resources.Theme getTheme() {
        return mTheme;
    }

    public ArrayList<ClassLoader> getClassLoaders() {
        return mDexList;
    }

    public LuaDexClassLoader loadApp(String packageName) {
        try {
            LuaDexClassLoader dex = sDexCache.get(packageName);
            if (dex == null) {
                PackageManager pm = mContext.getPackageManager();
                ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                if (info.publicSourceDir == null) return null;
                dex = new LuaDexClassLoader(info.publicSourceDir, mOdexDir, info.nativeLibraryDir, mContext.getClassLoader());
                sDexCache.put(packageName, dex);
            }
            if (!mDexList.contains(dex)) {
                mDexList.add(dex);
            }
            return dex;
        } catch (PackageManager.NameNotFoundException e) {
            LuaLog.getInstance().addError("LuaDexLoader", e);
            return null;
        }
    }

    public void loadLibs() throws LuaError {
        File libsDir = new File(mLuaDir + "/libs");
        File[] libs = libsDir.listFiles();
        if (libs == null) return;

        for (File f : libs) {
            if (f.isDirectory()) continue;
            loadDex(f.getAbsolutePath());
        }
    }

    public void loadLib(String name) throws LuaError {
        String fileName = name;
        int dotIndex = name.indexOf(".");
        if (dotIndex > 0) {
            fileName = name.substring(0, dotIndex);
        }
        if (fileName.startsWith("lib")) {
            fileName = fileName.substring(3);
        }

        String libDir = mContext.getDir(fileName, Context.MODE_PRIVATE).getAbsolutePath();
        String libPath = libDir + "/lib" + fileName + ".so";
        File libFile = new File(libPath);

        if (!libFile.exists()) {
            String sourcePath = mLuaDir + "/libs/lib" + fileName + ".so";
            if (!new File(sourcePath).exists()) {
                throw new LuaError("can not find lib " + name);
            }
            LuaUtil.copyFile(sourcePath, libPath);
        }
        mLibCache.put(fileName, libPath);
    }

    public Map<String, String> getLibrarys() {
        return mLibCache;
    }

    public DexClassLoader loadDex(String path) throws LuaError {
        LuaDexClassLoader dex = sDexCache.get(path);
        if (dex == null) {
            dex = loadApp(path);
        }

        if (dex == null) {
            String fullPath = path;
            if (!fullPath.startsWith("/")) {
                fullPath = mLuaDir + "/" + path;
            }

            if (!new File(fullPath).exists()) {
                if (new File(fullPath + ".dex").exists()) {
                    fullPath += ".dex";
                } else if (new File(fullPath + ".jar").exists()) {
                    fullPath += ".jar";
                } else {
                    throw new LuaError(fullPath + " not found");
                }
            }

            File file = new File(fullPath);
            String cacheKey = LuaUtil.getFileMD5(file);
            if (cacheKey == null || cacheKey.equals("0")) {
                cacheKey = path;
            }

            dex = sDexCache.get(cacheKey);
            if (dex == null) {
                dex = new LuaDexClassLoader(fullPath, mOdexDir,
                        LuaApplication.getInstance().getApplicationInfo().nativeLibraryDir,
                        mContext.getClassLoader());
                sDexCache.put(cacheKey, dex);
            }
        }

        if (!mDexList.contains(dex)) {
            mDexList.add(dex);
            String dexPath = dex.getDexPath();
            if (dexPath.endsWith(".jar")) {
                loadResources(dexPath);
            }
        }
        return dex;
    }

    public void loadResources(String path) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            Object result = addAssetPath.invoke(assetManager, path);
            if (result == null || (result instanceof Integer i && i == 0)) return;

            mAssetManager = assetManager;
            Resources superRes = mContext.getResources();
            mResources = new LuaResources(assetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
            mResources.setSuperResources(superRes);
            mTheme = mResources.newTheme();
            mTheme.setTo(mContext.getTheme());
        } catch (Exception e) {
            LuaLog.getInstance().addError("LuaDexLoader", e);
        }
    }

    public AssetManager getAssets() {
        return mAssetManager;
    }

    public Resources getResources() {
        return mResources;
    }
}