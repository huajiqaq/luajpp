package org.luaj.android;

import android.os.Handler;
import android.os.Looper;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaScheduler;

import org.luaj.LuaConstants;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.util.concurrent.Future;

public class task extends VarArgFunction implements LuaGcable {

    private final LuaContext mContext;
    private Future<?> mFuture;

    public task(LuaContext context) {
        mContext = context;
        context.regGc(this);
    }

    @Override
    public Varargs invoke(Varargs args) {
        LuaValue func = args.arg1();
        int n = args.narg();
        LuaValue callback = n > 1 ? args.arg(n) : LuaConstants.NIL;
        int argCount = func.isnumber() ? n - 1 : n - (callback.isfunction() ? 2 : 1);
        LuaValue[] as = new LuaValue[Math.max(argCount, 0)];
        for (int i = 0; i < as.length; i++) {
            as[i] = args.arg(i + (func.isnumber() ? 2 : 1));
        }

        mFuture = LuaScheduler.getInstance().runOnIo(() -> {
            Varargs result;
            if (func.isnumber()) {
                try {
                    Thread.sleep(func.tolong());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                result = LuaValue.varargsOf(as);
            } else {
                try {
                    result = func.invoke(as);
                } catch (Exception e) {
                    LuaConfig.logError("task", e);
                    mContext.sendError("task", e);
                    result = LuaValue.varargsOf(LuaConstants.NIL, LuaValue.valueOf(e.toString()));
                }
            }
            if (callback.isfunction()) {
                Varargs finalResult = result;
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        callback.invoke(finalResult);
                    } catch (Exception e) {
                        LuaConfig.logError("task", e);
                        mContext.sendError("task", e);
                    }
                });
            }
        });

        return CoerceJavaToLua.coerce(mFuture);
    }

    @Override
    public void gc() {
        if (mFuture != null) mFuture.cancel(true);
    }

    @Override
    public boolean isGc() {
        return false;
    }
}