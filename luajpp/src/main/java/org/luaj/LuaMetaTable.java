package org.luaj;

public interface LuaMetaTable {
    //public LuaValue __call(LuaValue args);
    void __newindex(LuaValue key, LuaValue value);

    LuaValue __index(LuaValue key);

}
