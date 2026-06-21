package org.luaj.android;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaTimer;

import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;
import org.luaj.lib.jse.CoerceJavaToLua;

public class timer extends VarArgFunction {

    private final LuaContext mContext;

    public timer(LuaContext context) {
        mContext = context;
    }

    @Override
    public Varargs invoke(Varargs args) {
        LuaValue fn = args.arg1();
        int delay = args.arg(2).toint();
        int period = args.arg(3).toint();
        LuaValue[] as = new LuaValue[args.narg() - 3];
        for (int i = 0; i < as.length; i++) {
            as[i] = args.arg(i + 4);
        }
        LuaTimer t = new LuaTimer(mContext, fn, as);
        t.start(delay, period);
        return CoerceJavaToLua.coerce(t);
    }
}