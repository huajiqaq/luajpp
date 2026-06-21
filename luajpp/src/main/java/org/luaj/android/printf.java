package org.luaj.android;

import com.androlua.core.LuaContext;

import org.luaj.Globals;
import org.luaj.LuaConstants;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;

public class printf extends VarArgFunction {
    private final LuaContext mContext;
    private final Globals mGlobals;

    public printf(LuaContext context) {
        mContext = context;
        mGlobals = context.getLuaState();
    }

    public printf(LuaContext context, Globals globals) {
        mContext = context;
        mGlobals = globals;
    }

    @Override
    public Varargs invoke(Varargs args) {
        String ss = mGlobals.stringlib.format.invoke(args).tojstring();
        mContext.sendMsg(ss);
        return LuaConstants.NONE;
    }
}