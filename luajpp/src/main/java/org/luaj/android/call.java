package org.luaj.android;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaConfig;

import org.luaj.Globals;
import org.luaj.LuaConstants;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;

/**
 * 在 UI 线程中调用 Lua 函数。
 * <p>
 * Activity 场景使用 {@code runOnUiThread}，Service 等场景回退到 {@code Handler(主线程)}。
 */
public class call extends VarArgFunction {

    private final LuaContext mContext;
    private final Globals mGlobals;
    private final Handler mMainHandler;

    public call(LuaContext context) {
        mContext = context;
        mGlobals = context.getLuaState();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public Varargs invoke(Varargs args) {
        final LuaValue func = args.arg1();
        final int n = args.narg() - 1;
        final LuaValue[] argv = new LuaValue[n];
        for (int i = 0; i < n; i++) {
            argv[i] = args.arg(i + 2);
        }

        Runnable action = () -> {
            try {
                LuaValue target = func.isfunction() ? func : mGlobals.get(func);
                if (target.isfunction()) {
                    target.invoke(argv);
                }
            } catch (Exception e) {
                LuaConfig.logError("call", e);
                mContext.sendError("call", e);
            }
        };

        Context ctx = mContext.getContext();
        if (ctx instanceof Activity activity) {
            activity.runOnUiThread(action);
        } else {
            mMainHandler.post(action);
        }

        return LuaConstants.NONE;
    }
}