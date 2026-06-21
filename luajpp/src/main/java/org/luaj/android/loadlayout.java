package org.luaj.android;

import android.content.Context;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaLayout;

import org.luaj.Globals;
import org.luaj.LuaConstants;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;

public class loadlayout extends VarArgFunction {

    private final Context mContext;
    private final Globals mGlobals;

    public loadlayout(LuaContext context) {
        mContext = context.getContext();
        mGlobals = context.getLuaState();
    }

    public loadlayout(Context context, Globals globals) {
        mContext = context;
        mGlobals = globals;
    }

    @Override
    public Varargs invoke(Varargs args) {
        int n = args.narg();
        if (n < 1 || n > 3) {
            LuaConfig.logError("loadlayout", new IllegalArgumentException(
                    "invalid arguments, expected 1-3, got " + n));
            return LuaConstants.NIL;
        }
        var loader = new LuaLayout(mContext);
        return switch (n) {
            case 1 -> loader.load(args.arg1(), mGlobals);
            case 2 -> loader.load(args.arg1(), args.arg(2).checktable());
            case 3 -> loader.load(args.arg1(), args.arg(2).checktable(), args.arg(3));
            default -> LuaConstants.NIL;
        };
    }
}