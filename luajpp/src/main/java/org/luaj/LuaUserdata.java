/*******************************************************************************
 * Copyright (c) 2009 Luaj.org. All rights reserved.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ******************************************************************************/
package org.luaj;


public class LuaUserdata extends LuaValue {

    public Object m_instance;
    public LuaValue m_metatable;

    public LuaUserdata(Object obj) {
        m_instance = obj;
    }

    public LuaUserdata(Object obj, LuaValue metatable) {
        m_instance = obj;
        m_metatable = metatable;
    }

    @Override
    public LuaValue tostring() {
        return LuaValue.valueOf(tojstring());
    }

    @Override
    public String tojstring() {
        return String.valueOf(m_instance);
    }

    @Override
    public int type() {
        return LuaValue.TUSERDATA;
    }

    @Override
    public String typename() {
        return "userdata";
    }

    @Override
    public int hashCode() {
        return m_instance.hashCode();
    }

    public Object userdata() {
        return m_instance;
    }

    @Override
    public boolean isuserdata() {
        return true;
    }

    @Override
    public boolean isuserdata(Class<?> c) {
        return c.isAssignableFrom(m_instance.getClass());
    }

    @Override
    public Object touserdata() {
        return m_instance;
    }

    @Override
    public <T> T touserdata(Class<T> c) {
        return c.isInstance(m_instance) ? c.cast(m_instance) : null;
    }

    @Override
    public Object optuserdata(Object defval) {
        return m_instance;
    }

    @Override
    public Object optuserdata(Class<?> c, Object defval) {
        if (!c.isAssignableFrom(m_instance.getClass()))
            typerror(c.getName());
        return m_instance;
    }

    @Override
    public LuaValue getmetatable() {
        return m_metatable;
    }

    @Override
    public LuaValue setmetatable(LuaValue metatable) {
        this.m_metatable = metatable;
        return this;
    }

    @Override
    public Object checkuserdata() {
        return m_instance;
    }

    @Override
    public Object checkuserdata(Class<?> c) {
        if (c.isAssignableFrom(m_instance.getClass()))
            return m_instance;
        return typerror(c.getName());
    }

    @Override
    public LuaValue get(LuaValue key) {
        return m_metatable != null ? gettable(this, key) : LuaConstants.NIL;
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        if (m_metatable == null || !settable(this, key, value))
            error("cannot set " + key + " for userdata");
    }

    @Override
    public boolean equals(Object val) {
        if (this == val)
            return true;
        if (!(val instanceof LuaUserdata u))
            return false;
        return m_instance.equals(u.m_instance);
    }

    // equality w/ metatable processing
    @Override
    public LuaValue eq(LuaValue val) {
        return eq_b(val) ? LuaConstants.TRUE : LuaConstants.FALSE;
    }

    @Override
    public boolean eq_b(LuaValue val) {
        return val.raweq(this) || LuaValue.eqmtcall(this, val);
		/*if ( val.raweq(this) ) return true;
		if ( m_metatable == null || !val.isuserdata() ) return false;
		LuaValue valmt = val.getmetatable();
		return valmt!=null && LuaValue.eqmtcall(this, m_metatable, val, valmt);*/
    }

    // equality w/o metatable processing
    @Override
    public boolean raweq(LuaValue val) {
        return val.raweq(this);
    }

    @Override
    public boolean raweq(LuaUserdata val) {
        return this == val || (m_instance.equals(val.m_instance));
    }

    // __eq metatag processing
    public boolean eqmt(LuaValue val) {
        return LuaValue.eqmtcall(this, val);
        //return m_metatable!=null && val.isuserdata()? LuaValue.eqmtcall(this, m_metatable, val, val.getmetatable()): false;
    }
}
