package org.luaj.android;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaScheduler;

import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.util.concurrent.Future;

public class thread extends VarArgFunction {

    private final LuaContext mContext;

    public thread(LuaContext context) {
        mContext = context;
    }

    @Override
    public Varargs invoke(Varargs args) {
        LuaValue f = args.arg1();
        LuaValue[] as = new LuaValue[args.narg() - 1];
        for (int i = 0; i < as.length; i++) {
            as[i] = args.arg(i + 2);
        }

        Future<?> future = LuaScheduler.getInstance().runOnIo(() -> {
            try {
                f.invoke(as);
            } catch (Exception e) {
                LuaConfig.logError("thread", e);
                mContext.sendError("thread", e);
            }
        });

        return CoerceJavaToLua.coerce(future);
    }
}