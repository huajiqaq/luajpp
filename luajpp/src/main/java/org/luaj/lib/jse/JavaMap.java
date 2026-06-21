package org.luaj.lib.jse;

import org.luaj.LuaConstants;
import org.luaj.LuaTable;
import org.luaj.LuaUserdata;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.OneArgFunction;
import org.luaj.lib.VarArgFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LuaValue that represents a Java instance of {@link Map} type.
 * <p>
 * Can get elements by their key, as well as the length.
 * <p>
 * This class is not used directly.
 * It is returned by calls to {@link CoerceJavaToLua#coerce(Object)}
 * when a {@link Map} is supplied.
 *
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
class JavaMap extends LuaUserdata {

    static final LuaValue LENGTH = valueOf("length");
    static final LuaTable map_metatable;

    static {
        map_metatable = new LuaTable();
        map_metatable.rawset(LuaConstants.LEN, new LenFunction());
        map_metatable.rawset(LuaConstants.PAIRS, new PairsFunction());
    }

    JavaMap(Object instance) {
        super(instance);
        setmetatable(map_metatable);
    }

    @Override
    public Varargs next(LuaValue key) {
        // Map 没有顺序，不支持无状态的 next 迭代
        return LuaConstants.NIL;
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.equals(LENGTH))
            return valueOf(((Map<?, ?>) m_instance).size());
        Map<?, ?> map = (Map<?, ?>) m_instance;
        Object javaKey = CoerceLuaToJava.coerce(key, Object.class);
        if (map.containsKey(javaKey))
            return CoerceJavaToLua.coerce(map.get(javaKey));
        return super.get(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void set(LuaValue key, LuaValue value) {
        if (key.equals(LENGTH))
            error("cannot set length");
        else
            ((Map<Object, Object>) m_instance).put(
                    CoerceLuaToJava.coerce(key, Object.class),
                    CoerceLuaToJava.coerce(value, Object.class));
    }

    private static final class LenFunction extends OneArgFunction {
        public LuaValue call(LuaValue u) {
            return LuaValue.valueOf(((Map<?, ?>) ((LuaUserdata) u).m_instance).size());
        }
    }

    private static final class PairsFunction extends VarArgFunction {
        @Override
        public Varargs invoke(Varargs args) {
            final Map<?, ?> map = (Map<?, ?>) ((LuaUserdata) args.arg1()).m_instance;
            final List<Object> keys = new ArrayList<>(map.keySet());
            return varargsOf(new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    int index = args.arg(2).isnil() ? 0 : keys.indexOf(CoerceLuaToJava.coerce(args.arg(2), Object.class)) + 1;
                    if (index <= 0 || index >= keys.size()) return LuaConstants.NIL;
                    Object key = keys.get(index);
                    return varargsOf(CoerceJavaToLua.coerce(key), CoerceJavaToLua.coerce(map.get(key)));
                }
            }, args.arg1(), LuaConstants.NIL);
        }
    }
}