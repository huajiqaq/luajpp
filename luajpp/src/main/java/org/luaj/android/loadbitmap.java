package org.luaj.android;

import android.content.Context;
import android.graphics.Bitmap;

import com.androlua.core.LuaContext;
import com.androlua.image.LuaBitmap;
import com.androlua.internal.LuaConfig;

import org.luaj.LuaConstants;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;

public class loadbitmap extends VarArgFunction {

    private final Context mContext;
    private final String luaPath;

    public loadbitmap(LuaContext context) {
        mContext = context.getContext();
        luaPath = context.getLuaDir();
    }

    @Override
    public Varargs invoke(Varargs args) {
        String path = args.checkjstring(1);
        try {
            if (!(mContext instanceof Context ctx)) {
                return LuaConstants.NIL;
            }
            return LuaValue.userdataOf(loadBitmap(ctx, path));
        } catch (Exception e) {
            LuaConfig.logError("loadbitmap", e);
            return LuaConstants.NIL;
        }
    }

    private Bitmap loadBitmap(Context ctx, String path) throws Exception {
        // 网络路径直接用
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return LuaBitmap.getBitmapSync(ctx, path);
        }
        // 本地路径：无扩展名补 .png，相对路径拼 luaDir
        if (!path.contains(".")) {
            path += ".png";
        }
        if (!path.startsWith("/")) {
            path = luaPath + "/" + path;
        }
        return LuaBitmap.getBitmapSync(ctx, path);
    }
}