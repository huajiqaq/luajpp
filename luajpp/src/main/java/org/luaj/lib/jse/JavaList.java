package org.luaj.lib.jse;

import org.luaj.LuaConstants;
import org.luaj.LuaTable;
import org.luaj.LuaUserdata;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.OneArgFunction;
import org.luaj.lib.VarArgFunction;

import java.util.List;

/**
 * LuaValue that represents a Java instance of {@link List} type.
 * <p>
 * Can get elements by their integer key index (1-based), as well as the length.
 * <p>
 * This class is not used directly.
 * It is returned by calls to {@link CoerceJavaToLua#coerce(Object)}
 * when a {@link List} is supplied.
 *
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
class JavaList extends LuaUserdata {

    static final LuaValue LENGTH = valueOf("length");
    static final LuaTable list_metatable;

    static {
        list_metatable = new LuaTable();
        list_metatable.rawset(LuaConstants.LEN, new LenFunction());
        list_metatable.rawset(LuaConstants.IPAIRS, new IPairsFunction());
        list_metatable.rawset(LuaConstants.PAIRS, new IPairsFunction());
    }

    JavaList(Object instance) {
        super(instance);
        setmetatable(list_metatable);
    }

    @Override
    public Varargs next(LuaValue index) {
        List<?> list = (List<?>) m_instance;
        int idx = index.isnil() ? 0 : index.toint() + 1;
        if (idx >= list.size())
            return LuaConstants.NIL;
        return LuaValue.varargsOf(new LuaValue[]{CoerceJavaToLua.coerce(idx + 1), CoerceJavaToLua.coerce(list.get(idx))});
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.equals(LENGTH))
            return valueOf(((List<?>) m_instance).size());
        if (key.isint()) {
            int i = key.toint() - 1;
            List<?> list = (List<?>) m_instance;
            return i >= 0 && i < list.size() ?
                    CoerceJavaToLua.coerce(list.get(i)) :
                    LuaConstants.NIL;
        }
        return super.get(key);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void set(LuaValue key, LuaValue value) {
        if (key.isint()) {
            int i = key.toint() - 1;
            List<Object> list = (List<Object>) m_instance;
            if (i >= 0 && i < list.size())
                list.set(i, CoerceLuaToJava.coerce(value, Object.class));
            else if (m_metatable == null || metatag(LuaConstants.NEWINDEX).isnil() && !settable(this, key, value))
                error("list index out of bounds");
        } else
            super.set(key, value);
    }

    private static final class LenFunction extends OneArgFunction {
        public LuaValue call(LuaValue u) {
            return LuaValue.valueOf(((List<?>) ((LuaUserdata) u).m_instance).size());
        }
    }

    private static final class IPairsFunction extends VarArgFunction {
        @Override
        public Varargs invoke(Varargs args) {
            return varargsOf(new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    List<?> list = (List<?>) ((LuaUserdata) args.arg1()).m_instance;
                    int index = args.arg(2).toint();
                    if (index >= list.size()) return LuaConstants.NIL;
                    return varargsOf(LuaValue.valueOf(index + 1), CoerceJavaToLua.coerce(list.get(index)));
                }
            }, args.arg1(), LuaValue.valueOf(0));
        }
    }
}