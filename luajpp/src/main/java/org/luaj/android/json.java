package org.luaj.android;


import com.androlua.internal.LuaConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.LuaConstants;
import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.OneArgFunction;
import org.luaj.lib.TwoArgFunction;
import org.luaj.lib.jse.CoerceLuaToJava;
import org.luaj.lib.jse.LuaJavaLib;

public class json extends TwoArgFunction {

    public static String encode(LuaValue value) {
        return toJson(value).toString();
    }

    private static Object toJson(LuaValue value) {
        LuaTable t = value.checktable();
        if (t.length() == t.size()) {
            JSONArray arr = new JSONArray();
            for (int i = 1; i <= t.length(); i++) {
                arr.put(toJsonValue(t.get(i)));
            }
            return arr;
        }
        JSONObject obj = new JSONObject();
        Varargs pair = value.next(LuaConstants.NIL);
        while (!pair.isnil(1)) {
            try {
                obj.put(pair.arg1().tojstring(), toJsonValue(pair.arg(2)));
            } catch (JSONException e) {
                LuaConfig.logError("json", e);
            }
            pair = value.next(pair.arg1());
        }
        return obj;
    }

    private static Object toJsonValue(LuaValue v) {
        return v.istable() ? toJson(v) : CoerceLuaToJava.coerce(v, Object.class);
    }

    public static LuaValue decode(String text) {
        try {
            return LuaJavaLib.asTable(text.startsWith("[")
                    ? new JSONArray(text)
                    : new JSONObject(text));
        } catch (Exception e) {
            LuaConfig.logError("json", e);
            throw new LuaError(e.getMessage());
        }
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable json = new LuaTable();
        json.set("decode", new decode());
        json.set("encode", new encode());
        env.set("json", json);
        LuaValue pkg = env.get("package");
        if (!pkg.isnil()) pkg.get("loaded").set("json", json);
        return LuaConstants.NIL;
    }

    private static class decode extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            return decode(arg.tojstring());
        }
    }

    private static class encode extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            return LuaValue.valueOf(encode(arg));
        }
    }
}