package org.luaj.android;

import android.content.Context;

import com.androlua.core.LuaContext;
import com.androlua.drawable.LuaBitmapDrawable;
import com.androlua.image.LuaBitmap;
import com.androlua.internal.LuaLayout;

import org.luaj.Globals;
import org.luaj.LuaConstants;
import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.TwoArgFunction;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.io.File;
import java.util.Locale;

public class res extends TwoArgFunction {

    private final LuaContext mContext;
    private String mLanguage;
    private Globals mGlobals;
    private LuaTable mStringTable;

    public res(LuaContext context) {
        mContext = context;
        mLanguage = Locale.getDefault().getLanguage();
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        mGlobals = env.checkglobals();
        LuaTable res = new LuaTable();
        res.set("string", new string());
        res.set("drawable", new drawable());
        res.set("bitmap", new bitmap());
        res.set("layout", new layout());
        res.set("view", new view());
        env.set("res", res);
        LuaValue pkg = env.get("package");
        if (!pkg.isnil()) pkg.get("loaded").set("res", res);
        return LuaConstants.NIL;
    }

    private void loadStringTable() {
        String language = Locale.getDefault().getLanguage();
        if (!language.equals(mLanguage)) {
            mLanguage = language;
            mStringTable = null;
        }
        if (mStringTable == null) {
            mStringTable = new LuaTable();
            String p = mContext.getLuaPath("res/string", "init.lua");
            if (new File(p).exists()) mGlobals.loadfile(p, mStringTable).call();
            p = mContext.getLuaPath("res/string", mLanguage + ".lua");
            if (new File(p).exists()) mGlobals.loadfile(p, mStringTable).call();
        }
    }

    private LuaTable listFiles(String dir) {
        LuaTable t = new LuaTable();
        String[] files = new File(mContext.getLuaPath(dir)).list();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                t.set(i + 1, files[i]);
            }
        }
        return t;
    }

    private String findFile(String base, String... exts) {
        for (String ext : exts) {
            String p = base + ext;
            if (new File(p).exists()) return p;
        }
        return null;
    }

    /**
     * 资源子表基类，消除 type/typename/get(LuaValue)/checktable 重复
     */
    private abstract class ResTable extends LuaValue {
        final String mDir;

        ResTable(String dir) {
            mDir = dir;
        }

        @Override
        public int type() {
            return TTABLE;
        }

        @Override
        public String typename() {
            return "table";
        }

        @Override
        public LuaValue get(LuaValue key) {
            return get(key.tojstring());
        }

        @Override
        public LuaTable checktable() {
            return listFiles(mDir);
        }
    }

    private class string extends ResTable {
        string() {
            super("res/string");
        }

        @Override
        public LuaTable checktable() {
            loadStringTable();
            return mStringTable;
        }

        @Override
        public LuaValue get(String key) {
            loadStringTable();
            return mStringTable.get(key);
        }
    }

    private class drawable extends ResTable {
        drawable() {
            super("res/drawable");
        }

        @Override
        public LuaValue get(String arg) {
            String base = mContext.getLuaPath("res/drawable", arg);
            String p = findFile(base, ".png", ".jpg", ".gif");
            if (p != null) return CoerceJavaToLua.coerce(new LuaBitmapDrawable(mContext, p));
            p = base + ".lua";
            if (new File(p).exists()) return mGlobals.loadfile(p, mGlobals).call();
            return LuaConstants.NIL;
        }
    }

    private class bitmap extends ResTable {
        bitmap() {
            super("res/drawable");
        }

        @Override
        public LuaValue get(String arg) {
            try {
                String base = mContext.getLuaPath("res/drawable", arg);
                String p = findFile(base, ".png", ".jpg", ".gif");
                Context context = mContext.getContext();
                if (p != null) return CoerceJavaToLua.coerce(LuaBitmap.getBitmapSync(context, p));
                p = base + ".lua";
                if (new File(p).exists()) return mGlobals.loadfile(p, mGlobals).call();
                return LuaConstants.NIL;
            } catch (Exception e) {
                throw new LuaError(e);
            }
        }
    }

    private class layout extends ResTable {
        layout() {
            super("res/layout");
        }

        @Override
        public LuaValue get(String arg) {
            return mGlobals.loadfile(mContext.getLuaPath("res/layout", arg + ".lua"), mGlobals).call();
        }
    }

    private class view extends ResTable {
        view() {
            super("res/layout");
        }

        @Override
        public LuaValue get(String arg) {
            String p = mContext.getLuaPath("res/layout", arg + ".lua");
            return new LuaLayout(mContext.getContext()).load(mGlobals.loadfile(p, mGlobals).call(), mGlobals);
        }
    }
}