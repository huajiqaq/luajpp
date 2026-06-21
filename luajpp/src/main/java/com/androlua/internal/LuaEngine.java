package com.androlua.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.Toast;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.network.Http;
import com.androlua.network.SyncHttp;
import com.androlua.util.LuaDexLoader;

import org.luaj.Globals;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.android.file;
import org.luaj.android.json;
import org.luaj.android.loadbitmap;
import org.luaj.android.loadlayout;
import org.luaj.android.loadmenu;
import org.luaj.android.print;
import org.luaj.android.printf;
import org.luaj.android.res;
import org.luaj.android.task;
import org.luaj.android.thread;
import org.luaj.android.timer;
import org.luaj.lib.jse.CoerceJavaToLua;
import org.luaj.lib.jse.JavaPackage;
import org.luaj.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lua 执行引擎，负责 Lua 状态管理和脚本执行。
 */
public class LuaEngine implements LuaContext {

    protected final Context mContext;
    protected final Object mHost;
    protected final String mHostName;
    protected final LuaContext mLuaContext;
    protected String mLuaFileName;
    protected String mLuaFile;

    protected Globals mGlobals;
    protected LuaDexLoader mLuaDexLoader;
    protected final LuaPathResolver mPathResolver;
    private final ArrayList<LuaGcable> mGcList = new ArrayList<>();

    private final Map<String, Object> mGlobalData = new HashMap<>();
    private final Map<String, Object> mSharedData = new HashMap<>();
    private String mLuaExtDir;

    protected StringBuilder mToastBuilder = new StringBuilder();
    protected Toast mToast;
    protected long mLastShow;

    protected int mWidth;
    protected int mHeight;
    protected float mDensity;

    private OnInitListener mInitListener;

    public interface OnInitListener {
        void onSuccess();

        void onError(Exception e);
    }

    public void setOnInitListener(OnInitListener listener) {
        mInitListener = listener;
    }

    public LuaEngine(Context context, Object host, String hostName) {
        mContext = context;
        mHost = host;
        mHostName = hostName;
        mPathResolver = new LuaPathResolver();
        mLuaContext = mContext instanceof LuaContext ? (LuaContext) mContext : this;
    }

    public void init(String luaPath) {
        init(luaPath, null);
    }

    public void init(String luaPath, Object[] arg) {
        if (luaPath == null || luaPath.isEmpty()) {
            throw new IllegalArgumentException("luaPath cannot be null or empty");
        }

        mLuaFile = luaPath;
        File f = new File(luaPath);
        String luaDir = f.getParent();
        if (luaDir == null) {
            luaDir = mContext.getFilesDir().getAbsolutePath();
        }
        mPathResolver.setLuaDir(luaDir);
        mLuaFileName = f.getName();

        try {
            mPathResolver.setRootDir(LuaPathResolver.findRoot(luaDir));
        } catch (RuntimeException e) {
            mPathResolver.setRootDir(luaDir);
            sendMsg("Warning: " + e.getMessage());
        }

        initSize();

        mLuaDexLoader = new LuaDexLoader(mContext, mContext.getFilesDir().getAbsolutePath());
        mLuaDexLoader.loadLibs();

        mGlobals = JsePlatform.standardGlobals();
        mGlobals.finder = this;
        initEnv();
        mGlobals.luajavaLib.classLoaders = mLuaDexLoader.getClassLoaders();
        mGlobals.luajavaLib.setLuaContext(getLuaContext());

        String luaPathStr = LuaPathResolver.buildLuaPath(luaDir);
        String appLuaPath = LuaPathResolver.buildLuaPath(mPathResolver.getAppLuaDir());
        String rootLuaPath = LuaPathResolver.buildLuaPath(mPathResolver.getRootDir());
        mGlobals.package_.setLuaPath(luaPathStr + ";" + appLuaPath + ";" + rootLuaPath);

        try {
            registerBaseModules();
            mGlobals.jset(mHostName, mHost);
            mGlobals.jset("this", mHost);

            if (arg != null && arg.length > 0) {
                mGlobals.loadfile(mLuaFile).jcall(arg);
            } else {
                mGlobals.loadfile(mLuaFile).jcall();
            }
            if (mInitListener != null) mInitListener.onSuccess();
        } catch (Exception e) {
            sendError("Lua init error", e);
            if (mInitListener != null) mInitListener.onError(e);
        }
    }

    /**
     * 注册基础 Lua 模块
     */
    protected void registerBaseModules() {
        mGlobals.set("print", new print(getLuaContext()));
        mGlobals.set("printf", new printf(getLuaContext()));
        mGlobals.set("loadlayout", new loadlayout(getLuaContext()));
        mGlobals.set("loadbitmap", new loadbitmap(getLuaContext()));
        mGlobals.set("loadmenu", new loadmenu(getLuaContext()));
        mGlobals.set("task", new task(getLuaContext()));
        mGlobals.set("thread", new thread(getLuaContext()));
        mGlobals.set("timer", new timer(getLuaContext()));
        mGlobals.load(new res(getLuaContext()));
        mGlobals.load(new json());
        mGlobals.load(new file());
        mGlobals.jset("Http", Http.class);
        mGlobals.jset("http", SyncHttp.class);
        mGlobals.set("android", new JavaPackage("android"));
        mGlobals.set("java", new JavaPackage("java"));
        mGlobals.set("com", new JavaPackage("com"));
        mGlobals.set("org", new JavaPackage("org"));
    }

    /**
     * 读取 init.lua 配置
     */
    protected void initEnv() {
        File initFile = new File(mPathResolver.getLuaDir() + "/init.lua");
        if (!initFile.exists()) return;

        try {
            LuaTable env = new LuaTable();
            mGlobals.loadfile("init.lua", env).call();

            LuaValue debug = env.get("debugmode");
            if (debug.isnil()) debug = env.get("debug_mode");
            if (debug.isboolean()) LuaConfig.setDebug(debug.toboolean());
        } catch (Exception e) {
            sendMsg(e.getMessage());
        }
    }

    /**
     * 获取屏幕尺寸
     */
    @SuppressLint("Deprecated")
    protected void initSize() {
        var wm = (android.view.WindowManager)
                mContext.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        var metrics = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        mWidth = metrics.widthPixels;
        mHeight = metrics.heightPixels;
        mDensity = metrics.density;
    }

    public Object runFunc(String name, Object... args) {
        try {
            LuaValue func = mGlobals.get(name);
            if (func.isfunction()) return func.jcall(args);
        } catch (Exception e) {
            sendError(name, e);
        }
        return null;
    }

    public boolean runBooleanFunc(String name, Object... args) {
        try {
            LuaValue func = mGlobals.get(name);
            if (func == null || func.isnil()) return false;
            int len = args != null ? args.length : 0;
            LuaValue[] luaArgs = new LuaValue[len];
            for (int i = 0; i < len; i++) {
                luaArgs[i] = CoerceJavaToLua.coerce(args[i]);
            }
            return func.invoke(luaArgs).toboolean(1);
        } catch (Exception e) {
            sendError(name, e);
            return false;
        }
    }

    @SuppressLint("ShowToast")
    public void showToast(String text) {
        if (!LuaConfig.isDebug()) return;
        long now = System.currentTimeMillis();
        if (mToast == null || now - mLastShow > 1000) {
            mToastBuilder.setLength(0);
            mToast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
            mToastBuilder.append(text);
            mToast.show();
        } else {
            mToastBuilder.append("\n").append(text);
            mToast.setText(mToastBuilder.toString());
            mToast.setDuration(Toast.LENGTH_LONG);
        }
        mLastShow = now;
    }

    @Override
    public void sendMsg(String msg) {
        LuaLog.getInstance().add(msg);
        showToast(msg);
    }

    @Override
    public void sendError(String title, Exception e) {
        LuaLog.getInstance().addError(title, e);
    }

    public void destroy() {
        // 可能部分场景 mGlobals 并未初始化
        if (mGlobals == null) return;
        runFunc("onDestroy");
        for (LuaGcable gc : mGcList) {
            try {
                gc.gc();
            } catch (Exception ignored) {
            }
        }
        mGcList.clear();
    }

    // ==================== ResourceFinder ====================

    @Override
    public InputStream findResource(String name) {
        try {
            if (new File(name).exists()) return new FileInputStream(name);
        } catch (Exception ignored) {
        }
        try {
            String path = mPathResolver.getLuaPath(name);
            if (new File(path).exists()) return new FileInputStream(path);
        } catch (Exception ignored) {
        }
        try {
            return mContext.getAssets().open(name);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public String findFile(String filename) {
        if (filename.startsWith("/")) return filename;
        return mPathResolver.getLuaPath(filename);
    }

    // ==================== LuaContext 接口实现 ====================

    @Override
    public ArrayList<ClassLoader> getClassLoaders() {
        return mLuaDexLoader != null ? mLuaDexLoader.getClassLoaders() : null;
    }

    @Override
    public void call(String func, Object... args) {
        mGlobals.get(func).jcall(args);
    }

    @Override
    public void set(String name, Object value) {
        mGlobals.jset(name, value);
    }

    @Override
    public String getLuaPath() {
        return mLuaFile;
    }

    @Override
    public String getLuaPath(String path) {
        return mPathResolver.getLuaPath(path);
    }

    @Override
    public String getLuaPath(String dir, String name) {
        return mPathResolver.getLuaPath(dir, name);
    }

    @Override
    public String getLuaDir() {
        return mPathResolver.getLuaDir();
    }

    @Override
    public String getLuaDir(String dir) {
        return mPathResolver.getLuaDir(dir);
    }

    @Override
    public String getLuaExtDir() {
        return mLuaExtDir != null ? mLuaExtDir
                : mContext.getExternalFilesDir(null) != null
                  ? Objects.requireNonNull(mContext.getExternalFilesDir(null)).getAbsolutePath()
                  : mContext.getFilesDir().getAbsolutePath();
    }

    @Override
    public String getLuaExtDir(String dir) {
        return getLuaExtDir() + "/" + dir;
    }

    @Override
    public void setLuaExtDir(String dir) {
        mLuaExtDir = dir;
    }

    @Override
    public String getLuaExtPath(String path) {
        return getLuaExtDir() + "/" + path;
    }

    @Override
    public String getLuaExtPath(String dir, String name) {
        return getLuaExtDir(dir) + "/" + name;
    }

    @Override
    public String getRootDir() {
        return mPathResolver.getRootDir();
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    public LuaContext getLuaContext() {
        return mLuaContext;
    }

    @Override
    public Globals getLuaState() {
        return mGlobals;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public float getDensity() {
        return mDensity;
    }

    @Override
    public Object doFile(String path, Object... arg) {
        return mGlobals.loadfile(path).jcall(arg);
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return mGlobalData;
    }

    @Override
    public Map<String, ?> getSharedData() {
        return mSharedData;
    }

    @Override
    public Object getSharedData(String key) {
        return mSharedData.get(key);
    }

    @Override
    public Object getSharedData(String key, Object def) {
        Object v = mSharedData.get(key);
        return v != null ? v : def;
    }

    @Override
    public boolean setSharedData(String key, Object value) {
        mSharedData.put(key, value);
        return true;
    }

    @Override
    public void regGc(LuaGcable obj) {
        mGcList.add(obj);
    }

    // ==================== 工具方法 ====================

    public void setDebug(boolean debug) {
        LuaConfig.setDebug(debug);
    }

    public boolean isDebug() {
        return LuaConfig.isDebug();
    }

    public LuaPathResolver getPathResolver() {
        return mPathResolver;
    }
}