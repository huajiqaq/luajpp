package com.androlua.core;

import android.content.Context;

import org.luaj.Globals;
import org.luaj.lib.ResourceFinder;

import java.util.ArrayList;
import java.util.Map;

/**
 * Lua 上下文接口。
 * <p>
 * 定义 Activity/Service 与 Lua 引擎交互的核心契约。
 * 实现类通过 {@link com.androlua.internal.ActivityDelegate} 或
 * {@link com.androlua.internal.ServiceDelegate} 委托具体逻辑。
 */
public interface LuaContext extends ResourceFinder {

    ArrayList<ClassLoader> getClassLoaders();

    void call(String func, Object... args);

    void set(String name, Object value);

    String getLuaPath();

    String getLuaPath(String path);

    String getLuaPath(String dir, String name);

    String getLuaDir();

    String getLuaDir(String dir);

    String getLuaExtDir();

    String getLuaExtDir(String dir);

    void setLuaExtDir(String dir);

    String getLuaExtPath(String path);

    String getLuaExtPath(String dir, String name);

    String getRootDir();

    Context getContext();

    Globals getLuaState();

    Object doFile(String path, Object... arg);

    void sendMsg(String msg);

    void sendError(String title, Exception msg);

    int getWidth();

    int getHeight();
    float getDensity();

    Map<String, Object> getGlobalData();

    Map<String, ?> getSharedData();

    Object getSharedData(String key);

    Object getSharedData(String key, Object def);

    boolean setSharedData(String key, Object value);

    void regGc(LuaGcable obj);
}
