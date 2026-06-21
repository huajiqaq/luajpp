package org.luaj.android;

import android.os.Build;

import com.androlua.internal.LuaConfig;
import com.androlua.util.LuaUtil;

import org.luaj.Globals;
import org.luaj.LuaConstants;
import org.luaj.LuaString;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.OneArgFunction;
import org.luaj.lib.TwoArgFunction;
import org.luaj.lib.jse.LuaJavaLib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Lua file 模块：文件读写、列表、属性查询。
 */
public class file extends TwoArgFunction {

    private Globals mGlobals;

    public static String readAll(String path) {
        try {
            return new String(LuaUtil.readAll(path));
        } catch (IOException e) {
            LuaConfig.logError("file.readAll", e);
        }
        return "";
    }

    public static String[] list(String path) {
        File f = new File(path);
        if (f.isDirectory()) {
            String[] names = f.list();
            return names != null ? names : new String[0];
        }
        return new String[0];
    }

    public static boolean exists(String path) {
        return new File(path).exists();
    }

    public static boolean save(String path, LuaString text) {
        try (FileOutputStream out = new FileOutputStream(path)) {
            out.write(text.m_bytes, text.m_offset, text.m_length);
            return true;
        } catch (Exception e) {
            LuaConfig.logError("file.save", e);
        }
        return false;
    }


    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        mGlobals = env.checkglobals();
        LuaTable file = new LuaTable();
        file.set("readall", new readall());
        file.set("list", new list());
        file.set("exists", new exists());
        file.set("save", new save());
        file.set("type", new type());
        file.set("info", new info());
        file.set("mkdir", new mkdir());
        env.set("file", file);
        if (!env.get("package").isnil()) env.get("package").get("loaded").set("file", file);
        return LuaConstants.NIL;
    }

    private class readall extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            String content = readAll(mGlobals.finder.findFile(arg.tojstring()));
            return content.isEmpty() ? LuaConstants.NIL : LuaString.valueOf(content);
        }
    }

    private class list extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            return LuaJavaLib.asTable(list(mGlobals.finder.findFile(arg.tojstring())));
        }
    }

    private class exists extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            try {
                return LuaValue.valueOf(exists(mGlobals.finder.findFile(arg.tojstring())));
            } catch (Exception e) {
                return LuaConstants.NIL;
            }
        }
    }

    private class save extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue arg1, LuaValue arg2) {
            return LuaValue.valueOf(save(mGlobals.finder.findFile(arg1.tojstring()), arg2.checkstring()));
        }
    }

    private class type extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            File f = new File(mGlobals.finder.findFile(arg.tojstring()));
            if (!f.exists()) return LuaValue.valueOf("");
            return LuaValue.valueOf(f.isDirectory() ? "dir" : "file");
        }
    }

    private class mkdir extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            File dir = new File(mGlobals.finder.findFile(arg.tojstring()));
            if (dir.exists()) return LuaConstants.TRUE;
            return LuaValue.valueOf(dir.mkdirs());
        }
    }

    private class info extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            LuaTable ret = new LuaTable();
            String path = mGlobals.finder.findFile(arg.tojstring());
            File f = new File(path);

            String name = f.getName();
            ret.jset("name", name);
            int dot = name.lastIndexOf(".");
            if (dot > 0) ret.jset("ext", name.substring(dot + 1));
            ret.jset("parent", f.getParent());
            ret.jset("read", f.canRead());
            ret.jset("write", f.canWrite());

            if (!f.exists()) {
                ret.jset("type", "");
                return ret;
            }

            ret.jset("type", f.isDirectory() ? "dir" : "file");
            ret.jset("path", f.getAbsolutePath());
            ret.jset("size", f.length());
            ret.jset("execute", f.canExecute());
            ret.jset("last", f.lastModified());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Path p = Paths.get(f.getAbsolutePath());
                    BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                    ret.jset("create", attr.creationTime().toMillis());
                    ret.jset("access", attr.lastAccessTime().toMillis());
                } catch (Exception e) {
                    LuaConfig.logError("file.info", e);
                }
            }
            return ret;
        }
    }
}