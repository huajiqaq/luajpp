package com.androlua.util;

import java.util.HashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;

/**
 * LuaJ 动态加载类加载器
 * 用于加载 dex/jar/apk 中的类，支持缓存
 */
@SuppressWarnings("unused")
public class LuaDexClassLoader extends DexClassLoader {

    private final Map<String, Class<?>> mClassCache = new HashMap<>();
    private final String mDexPath;

    public LuaDexClassLoader(String dexPath, String optimizedDirectory, String libraryPath, ClassLoader parent) {
        super(dexPath, optimizedDirectory, libraryPath, parent);
        mDexPath = dexPath;
    }

    public String getDexPath() {
        return mDexPath;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = mClassCache.get(name);
        if (clazz == null) {
            clazz = super.findClass(name);
            mClassCache.put(name, clazz);
        }
        return clazz;
    }

    /**
     * 清除类缓存
     */
    public void clearCache() {
        mClassCache.clear();
    }
}
