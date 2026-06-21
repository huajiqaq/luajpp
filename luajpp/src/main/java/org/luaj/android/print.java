package org.luaj.android;

import com.androlua.core.LuaContext;

import org.luaj.Globals;
import org.luaj.LuaConstants;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;

public class print extends VarArgFunction {

    final LuaContext mContext;
    final Globals mGlobals;

    public print(LuaContext context) {
        mContext = context;
        mGlobals = context.getLuaState();
    }

    public print(LuaContext context, Globals globals) {
        mContext = context;
        mGlobals = globals;
    }

    @Override
    public Varargs invoke(Varargs args) {
        LuaValue tostring = mGlobals.baselib.tostring;
        StringBuilder buf = new StringBuilder();
        for (int i = 1, n = args.narg(); i <= n; i++) {
            if (i > 1) buf.append("    ");
            buf.append(tostring.call(args.arg(i)).tojstring());
        }
        mContext.sendMsg(buf.toString());
        return LuaConstants.NONE;
    }
}