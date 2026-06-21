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

import androidx.annotation.NonNull;

import org.luaj.compiler.DumpState;
import org.luaj.lib.StringLib;
import org.luaj.lib.jse.CoerceLuaToJava;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

/**
 * Subclass of {@link LuaValue} for representing lua tables.
 * <p>
 * Almost all API's implemented in {@link LuaTable} are defined and documented in {@link LuaValue}.
 * <p>
 * If a table is needed, the one of the type-checking functions can be used such as
 * {@link #istable()},
 * {@link #checktable()}, or
 * {@link #opttable(LuaTable)}
 * <p>
 * The main table operations are defined on {@link LuaValue}
 * for getting and setting values with and without metatag processing:
 * <ul>
 * <li>{@link #get(LuaValue)}</li>
 * <li>{@link #set(LuaValue, LuaValue)}</li>
 * <li>{@link #rawget(LuaValue)}</li>
 * <li>{@link #rawset(LuaValue, LuaValue)}</li>
 * <li>plus overloads such as {@link #get(String)}, {@link #get(int)}, and so on</li>
 * </ul>
 * <p>
 * To iterate over key-value pairs from Java, use
 * <pre> {@code
 * LuaValue k = LuaConstants.NIL;
 * while ( true ) {
 *    Varargs n = table.next(k);
 *    if ( (k = n.arg1()).isnil() )
 *       break;
 *    LuaValue v = n.arg(2)
 *    process( k, v )
 * }}</pre>
 * <p>
 * <p>
 * As with other types, {@link LuaTable} instances should be constructed via one of the table constructor
 * methods on {@link LuaValue}:
 * <ul>
 * <li>{@link LuaValue#tableOf()} empty table</li>
 * <li>{@link LuaValue#tableOf(int, int)} table with capacity</li>
 * <li>{@link LuaValue#listOf(LuaValue[])} initialize array part</li>
 * <li>{@link LuaValue#listOf(LuaValue[], Varargs)} initialize array part</li>
 * <li>{@link LuaValue#tableOf(LuaValue[])} initialize named hash part</li>
 * <li>{@link LuaValue#tableOf(Varargs, int)} initialize named hash part</li>
 * <li>{@link LuaValue#tableOf(LuaValue[], LuaValue[])} initialize array and named parts</li>
 * <li>{@link LuaValue#tableOf(LuaValue[], LuaValue[], Varargs)} initialize array and named parts</li>
 * </ul>
 *
 * @see LuaValue
 */
public class LuaTable extends LuaValue implements Metatable {
    private static final int MIN_HASH_CAPACITY = 2;
    private static final LuaString N = valueOf("n");
    private static final Slot[] NOBUCKETS = {};
    private final Globals globals;
    /**
     * the array values
     */
    protected LuaValue[] array;
    /**
     * the hash part
     */
    protected Slot[] hash;
    /**
     * the number of hash entries
     */
    protected int hashEntries;
    /**
     * metatable for this table, or null
     */
    protected Metatable m_metatable;
    protected boolean mConst;

    /**
     * Construct empty table
     */
    public LuaTable() {
        this(null);
    }

    public LuaTable(Globals globals) {
        array = LuaConstants.NOVALS;
        hash = NOBUCKETS;
        this.globals = globals;
    }

    public LuaTable(LuaTable t) {
        this(null, t);
    }

    public LuaTable(Globals globals, LuaTable t) {
        this(globals);
        LuaValue key = LuaConstants.NIL;
        Varargs next;
        while (!(next = t.next(key)).isnil(1)) {
            key = next.arg1();
            LuaValue value = next.arg(2);
            if (value.istable())
                set(key, new LuaTable(value.checktable()));
            else
                set(key, value);
        }
    }

    /**
     * Construct table with preset capacity.
     *
     * @param narray capacity of array part
     * @param nhash  capacity of hash part
     */
    public LuaTable(int narray, int nhash) {
        this(null, narray, nhash);
    }

    public LuaTable(Globals globals, int narray, int nhash) {
        this(globals);
        presize(narray, nhash);
    }

    /**
     * Construct table with named and unnamed parts.
     *
     * @param named   Named elements in order {@code key-a, value-a, key-b, value-b, ... }
     * @param unnamed Unnamed elements in order {@code value-1, value-2, ... }
     * @param lastarg Additional unnamed values beyond {@code unnamed.length}
     */
    public LuaTable(LuaValue[] named, LuaValue[] unnamed, Varargs lastarg) {
        this(null, named, unnamed, lastarg);
    }

    public LuaTable(Globals globals, LuaValue[] named, LuaValue[] unnamed, Varargs lastarg) {
        this(globals);
        int nn = (named != null ? named.length : 0);
        int nu = (unnamed != null ? unnamed.length : 0);
        int nl = (lastarg != null ? lastarg.narg() : 0);
        presize(nu + nl, nn >> 1);
        for (int i = 0; i < nu; i++)
            rawset(i + 1, unnamed[i]);
        if (lastarg != null)
            for (int i = 1, n = lastarg.narg(); i <= n; ++i)
                rawset(nu + i, lastarg.arg(i));
        for (int i = 0; i < nn; i += 2)
            if (!named[i + 1].isnil())
                rawset(named[i], named[i + 1]);
    }

    /**
     * Construct table of unnamed elements.
     *
     * @param varargs Unnamed elements in order {@code value-1, value-2, ... }
     */
    public LuaTable(Varargs varargs) {
        this(null, varargs);
    }

    public LuaTable(Globals globals, Varargs varargs) {
        this(globals, varargs, 1);
    }

    /**
     * Construct table of unnamed elements.
     *
     * @param varargs  Unnamed elements in order {@code value-1, value-2, ... }
     * @param firstarg the index in varargs of the first argument to include in the table
     */
    public LuaTable(Varargs varargs, int firstarg) {
        this(null, varargs, firstarg);
    }

    public LuaTable(Globals globals, Varargs varargs, int firstarg) {
        int nskip = firstarg - 1;
        int n = Math.max(varargs.narg() - nskip, 0);
        presize(n, 1);
        set(N, valueOf(n));
        for (int i = 1; i <= n; i++)
            set(i, varargs.arg(i + nskip));
        this.globals = globals;
    }

    /**
     * Resize the table
     */
    private static LuaValue[] resize(LuaValue[] old, int n) {
        LuaValue[] v = new LuaValue[n];
        System.arraycopy(old, 0, v, 0, old.length);
        return v;
    }

    public static int hashpow2(int hashCode, int mask) {
        return hashCode & mask;
    }

    public static int hashmod(int hashCode, int mask) {
        return (hashCode & 0x7FFFFFFF) % mask;
    }

    /**
     * Find the hashtable slot index to use.
     *
     * @param key      the key to look for
     * @param hashMask N-1 where N is the number of hash slots (must be power of 2)
     * @return the slot index
     */
    public static int hashSlot(LuaValue key, int hashMask) {
        return switch (key.type()) {
            case TNUMBER, TTABLE, TTHREAD, TLIGHTUSERDATA, TUSERDATA ->
                    hashmod(key.hashCode(), hashMask);
            default -> hashpow2(key.hashCode(), hashMask);
        };
    }

    // Compute ceil(log2(x))
    static int log2(int x) {
        int lg = 0;
        x -= 1;
        if (x < 0)
            // 2^(-(2^31)) is approximately 0
            return Integer.MIN_VALUE;
        if ((x & 0xFFFF0000) != 0) {
            lg = 16;
            x >>>= 16;
        }
        if ((x & 0xFF00) != 0) {
            lg += 8;
            x >>>= 8;
        }
        if ((x & 0xF0) != 0) {
            lg += 4;
            x >>>= 4;
        }
        switch (x) {
            case 0x0:
                return 0;
            case 0x1:
                lg += 1;
                break;
            case 0x2:
                lg += 2;
                break;
            case 0x3:
                lg += 2;
                break;
            case 0x4:
                lg += 3;
                break;
            case 0x5:
                lg += 3;
                break;
            case 0x6:
                lg += 3;
                break;
            case 0x7:
                lg += 3;
                break;
            case 0x8:
                lg += 4;
                break;
            case 0x9:
                lg += 4;
                break;
            case 0xA:
                lg += 4;
                break;
            case 0xB:
                lg += 4;
                break;
            case 0xC:
                lg += 4;
                break;
            case 0xD:
                lg += 4;
                break;
            case 0xE:
                lg += 4;
                break;
            case 0xF:
                lg += 4;
                break;
        }
        return lg;
    }

    protected static boolean isLargeKey(LuaValue key) {
        return switch (key.type()) {
            case TSTRING -> key.rawlen() > LuaString.RECENT_STRINGS_MAX_LENGTH;
            case TNUMBER, TBOOLEAN -> false;
            default -> true;
        };
    }

    protected static Entry defaultEntry(LuaValue key, LuaValue value) {
        return key.isinttype() ? new IntKeyEntry(key.toint(), value) : new NormalEntry(key, value);
        /*if (key.isinttype()) {
            return new IntKeyEntry(key.toint(), value);
        } else if (value.type() == TNUMBER) {
            return new NumberValueEntry(key, value.todouble());
        } else {
            return new NormalEntry(key, value);
        }*/
    }

    static void addquoted(StringBuilder buf, LuaString s) {
        Buffer result = new Buffer();
        StringLib.addquoted(result, s);
        buf.append(result.tojstring());

    }

    @Override
    public Globals getGlobals() {
        return globals;
    }

    @NonNull
    @Override
    public LuaTable clone() {
        return new LuaTable(this);
    }

    @Override
    public LuaValue add(LuaValue v) {
        int len = length() + 1;
        for (int i = 1; i < len; i++) {
            if (get(i).eq_b(v))
                return LuaValue.valueOf(i);
        }
        insert(len, v);
        return LuaValue.valueOf(len);
    }

    public LuaTable sub(LuaValue s, LuaValue e) {
        return sub(s.toint(), e.toint());
    }

    public LuaTable sub(int s, int e) {
        LuaTable ret = new LuaTable(globals, e - s, 0);
        return copy(s, e, ret);
    }

    public LuaTable copy(int s, int e, LuaTable dest) {
        return copy(s, e, dest, 1);
    }

    public LuaTable copy(int s, int e, LuaTable dest, int p) {
        int len = rawlen();
        if (s < 0)
            s = len + s + 1;
        if (e < 0)
            e = len + e + 1;
        if (s < 1)
            s = 1;
        if (e > len)
            e = len;
        if (e < s)
            return dest;
        if (dest.length() < e - s + p)
            dest.presize(e - s + p);
        if (e < array.length) {
            System.arraycopy(array, s - 1, dest.array, p - 1, e - s + 1);
        } else {
            for (int i = 0; i <= e - s; i++) {
                dest.rawset(p + i, rawget(s + i));
            }
        }
        return dest;
    }

    public void clear() {
        array = LuaConstants.NOVALS;
        hash = NOBUCKETS;
        hashEntries = 0;
    }

    public void _const() {
        mConst = true;
    }

    public LuaValue find(LuaValue v, LuaValue k) {
        Varargs a;
        while (!(a = next(k)).isnil(1)) {
            if (a.arg(2).eq_b(v))
                return a.arg1();
            k = a.arg1();
        }
        return LuaConstants.NONE;
    }

    public Varargs foreach(LuaValue key, LuaValue func) {
        int i = 0;
        do {
            // find current key index
            if (!key.isnil()) {
                if (key.isinttype()) {
                    i = key.toint();
                    if (i > 0 && i <= array.length) {
                        break;
                    }
                }
                if (hash.length == 0)
                    error("invalid key to 'next' 1: " + key);
                i = hashSlot(key);
                boolean found = false;
                for (Slot slot = hash[i]; slot != null; slot = slot.rest()) {
                    if (found) {
                        StrongSlot nextEntry = slot.first();
                        if (nextEntry != null) {
                            func.invoke(nextEntry.toVarargs());
                        }
                    } else if (slot.keyeq(key)) {
                        found = true;
                    }
                }
                if (!found) {
                    error("invalid key to 'next' 2: " + key);
                }
                i += 1 + array.length;
            }
        } while (false);

        // check array part
        for (; i < array.length; ++i) {
            if (array[i] != null) {
                LuaValue value = m_metatable == null ? array[i] : m_metatable.arrayget(array, i);
                if (value != null) {
                    func.invoke(LuaInteger.valueOf(i + 1), value);
                }
            }
        }

        // check hash part
        for (i -= array.length; i < hash.length; ++i) {
            Slot slot = hash[i];
            while (slot != null) {
                StrongSlot first = slot.first();
                if (first != null)
                    func.invoke(first.toVarargs());
                slot = slot.rest();
            }
        }
        return LuaConstants.NONE;
    }

    public Varargs foreachi(LuaValue key, LuaValue func) {
        int i = 0;
        // check array part
        for (; i < array.length; ++i) {
            if (array[i] != null) {
                LuaValue value = m_metatable == null ? array[i] : m_metatable.arrayget(array, i);
                if (value != null) {
                    func.invoke(LuaInteger.valueOf(i + 1), value);
                }
            }
        }
        return LuaConstants.NONE;
    }

    @Override
    public int type() {
        return LuaValue.TTABLE;
    }

    @Override
    public String typename() {
        return "table";
    }

    @Override
    public boolean istable() {
        return true;
    }

    @Override
    public LuaTable checktable() {
        return this;
    }

    @Override
    public LuaTable opttable(LuaTable defval) {
        return this;
    }

    @Override
    public void presize(int narray) {
        if (narray > array.length)
            array = resize(array, 1 << log2(narray));
    }

    public void presize(int narray, int nhash) {
        if (nhash > 0 && nhash < MIN_HASH_CAPACITY)
            nhash = MIN_HASH_CAPACITY;
        // Size of both parts must be a power of two.
        array = (narray > 0 ? new LuaValue[1 << log2(narray)] : LuaConstants.NOVALS);
        hash = (nhash > 0 ? new Slot[1 << log2(nhash)] : NOBUCKETS);
        hashEntries = 0;
    }

    /**
     * Get the length of the array part of the table.
     *
     * @return length of the array part, does not relate to count of objects in the table.
     */
    protected int getArrayLength() {
        return array.length;
    }

    /**
     * Get the length of the hash part of the table.
     *
     * @return length of the hash part, does not relate to count of objects in the table.
     */
    protected int getHashLength() {
        return hash.length;
    }

    @Override
    public LuaValue getmetatable() {
        return (m_metatable != null) ? m_metatable.toLuaValue() : null;
    }

    @Override
    public LuaValue setmetatable(LuaValue metatable) {
        boolean hadWeakKeys = m_metatable != null && m_metatable.useWeakKeys();
        boolean hadWeakValues = m_metatable != null && m_metatable.useWeakValues();
        m_metatable = metatableOf(metatable);
        if ((hadWeakKeys != (m_metatable != null && m_metatable.useWeakKeys())) ||
                (hadWeakValues != (m_metatable != null && m_metatable.useWeakValues()))) {
            // force a rehash
            rehash(0);
        }
        return this;
    }

    @Override
    public LuaValue get(int key) {
        LuaValue v = rawget(key);
        return v.isnil() && m_metatable != null ? gettable(this, valueOf(key)) : v;
    }

    @Override
    public LuaValue get(LuaValue key) {
        LuaValue v = rawget(key);
        return v.isnil() && m_metatable != null ? gettable(this, key) : v;
    }

    @Override
    public LuaValue rawget(int key) {
        if (key > 0 && key <= array.length) {
            LuaValue v = m_metatable == null ? array[key - 1] : m_metatable.arrayget(array, key - 1);
            return v != null ? v : LuaConstants.NIL;
        }
        return hashget(LuaInteger.valueOf(key));
    }

    @Override
    public LuaValue rawget(LuaValue key) {
        if (key.isinttype()) {
            int ikey = key.toint();
            if (ikey > 0 && ikey <= array.length) {
                LuaValue v = m_metatable == null
                        ? array[ikey - 1] : m_metatable.arrayget(array, ikey - 1);
                return v != null ? v : LuaConstants.NIL;
            }
        }
        return hashget(key);
    }

    protected LuaValue hashget(LuaValue key) {
        if (hashEntries > 0) {
            for (Slot slot = hash[hashSlot(key)]; slot != null; slot = slot.rest()) {
                StrongSlot foundSlot;
                if ((foundSlot = slot.find(key)) != null) {
                    return foundSlot.value();
                }
            }
        }
        return LuaConstants.NIL;
    }

    @Override
    public void set(int key, LuaValue value) {
        if (mConst)
            throw new LuaError("can not be set a const table");
        if (m_metatable == null || !rawget(key).isnil() || !settable(this, LuaInteger.valueOf(key), value))
            rawset(key, value);
    }

    /**
     * caller must ensure key is not nil
     */
    @Override
    public void set(LuaValue key, LuaValue value) {
        if (mConst)
            throw new LuaError("can not be set a const table");
        if (key == null || !key.isvalidkey() && !metatag(LuaConstants.NEWINDEX).isfunction())
            throw new LuaError("value ('" + key + "') can not be used as a table index");
        if (m_metatable == null || !rawget(key).isnil() || !settable(this, key, value))
            rawset(key, value);
    }

    @Override
    public void rawset(int key, LuaValue value) {
        if (mConst)
            throw new LuaError("can not be set a const table");
        if (!arrayset(key, value))
            hashset(LuaInteger.valueOf(key), value);
    }

    /**
     * caller must ensure key is not nil
     */
    @Override
    public void rawset(LuaValue key, LuaValue value) {
        if (mConst)
            throw new LuaError("can not be set a const table");
        if (!key.isinttype() || !arrayset(key.toint(), value))
            hashset(key, value);
    }

    /**
     * Set an array element
     */
    private boolean arrayset(int key, LuaValue value) {
        if (key > 0 && key <= array.length) {
            array[key - 1] = value.isnil() ? null :
                    (m_metatable != null ? m_metatable.wrap(value) : value);
            return true;
        }
        return false;
    }

    /**
     * Remove the element at a position in a list-table
     *
     * @param pos the position to remove
     * @return The removed item, or {@link LuaConstants#NONE} if not removed
     */
    public LuaValue remove(int pos) {
        int n = length();
        if (pos == 0)
            pos = n;
        else if (pos > n)
            return LuaConstants.NONE;
        LuaValue v = get(pos);
        for (LuaValue r = v; !r.isnil(); ) {
            r = get(pos + 1);
            set(pos++, r);
        }
        return v.isnil() ? LuaConstants.NONE : v;
    }

    /**
     * Insert an element at a position in a list-table
     *
     * @param pos   the position to remove
     * @param value The value to insert
     */
    public void insert(int pos, LuaValue value) {
        if (pos == 0)
            pos = length() + 1;
        while (!value.isnil()) {
            LuaValue v = get(pos);
            set(pos++, value);
            value = v;
        }
    }

    /**
     * Concatenate the contents of a table efficiently, using {@link Buffer}
     *
     * @param sep {@link LuaString} separater to apply between elements
     * @param i   the first element index
     * @param j   the last element index, inclusive
     * @return {@link LuaString} value of the concatenation
     */
    public LuaValue concat(LuaString sep, int i, int j) {
        Buffer sb = new Buffer();
        if (i <= j) {
            sb.append(get(i).checkstring());
            while (++i <= j) {
                sb.append(sep);
                sb.append(get(i).checkstring());
            }
        }
        return sb.tostring();
    }

    @Override
    public int length() {
        if (m_metatable != null) {
            LuaValue len = len();
            if (!len.isint()) throw new LuaError("table length is not an integer: " + len);
            return len.toint();
        }
        return rawlen();
    }

    @Override
    public LuaValue len() {
        final LuaValue h = metatag(LuaConstants.LEN);
        if (h.toboolean())
            return h.call(this);
        return LuaInteger.valueOf(rawlen());
    }

    @Override
    public int rawlen() {
        int a = getArrayLength();
        int n = a + 1, m = 0;
        /*while (!rawget(n).isnil()) {
            m = n;
            n += a + getHashLength() + 1;
        }*/
        if (!rawget(n).isnil()) {
            //m = n;
            n += getHashLength() + 1;
        }

        while (n > m + 1) {
            int k = (n + m) / 2;
            if (!rawget(k).isnil())
                m = k;
            else
                n = k;
        }
        return m;
    }

    /**
     * Get the next element after a particular key in the table
     *
     * @return key, value or nil
     */
    @Override
    public Varargs next(LuaValue key) {
        int i = 0;
        do {
            // find current key index
            if (!key.isnil()) {
                if (key.isinttype()) {
                    i = key.toint();
                    if (i > 0 && i <= array.length) {
                        break;
                    }
                }
                if (hash.length == 0)
                    error("invalid key to 'next' 1: " + key);
                i = hashSlot(key);
                boolean found = false;
                for (Slot slot = hash[i]; slot != null; slot = slot.rest()) {
                    if (found) {
                        StrongSlot nextEntry = slot.first();
                        if (nextEntry != null) {
                            return nextEntry.toVarargs();
                        }
                    } else if (slot.keyeq(key)) {
                        found = true;
                    }
                }
                if (!found) {
                    error("invalid key to 'next' 2: " + key);
                }
                i += 1 + array.length;
            }
        } while (false);

        // check array part
        for (; i < array.length; ++i) {
            if (array[i] != null) {
                LuaValue value = m_metatable == null ? array[i] : m_metatable.arrayget(array, i);
                if (value != null) {
                    return varargsOf(LuaInteger.valueOf(i + 1), value);
                }
            }
        }

        // check hash part
        for (i -= array.length; i < hash.length; ++i) {
            Slot slot = hash[i];
            while (slot != null) {
                StrongSlot first = slot.first();
                if (first != null)
                    return first.toVarargs();
                slot = slot.rest();
            }
        }

        // nothing found, push nil, return nil.
        return LuaConstants.NIL;
    }

    /**
     * Get the next element after a particular key in the
     * contiguous array part of a table
     *
     * @return key, value or none
     */
    @Override
    public Varargs inext(LuaValue key) {
        int k = key.checkint() + 1;
        LuaValue v = rawget(k);
        return v.isnil() ? LuaConstants.NONE : varargsOf(LuaInteger.valueOf(k), v);
    }

    /**
     * Set a hashtable value
     *
     * @param key   key to set
     * @param value value to set
     */
    public void hashset(LuaValue key, LuaValue value) {
        if (value.isnil())
            hashRemove(key);
        else {
            int index = 0;
            if (hash.length > 0) {
                index = hashSlot(key);
                for (Slot slot = hash[index]; slot != null; slot = slot.rest()) {
                    StrongSlot foundSlot;
                    if ((foundSlot = slot.find(key)) != null) {
                        hash[index] = hash[index].set(foundSlot, value);
                        return;
                    }
                }
            }
            if (checkLoadFactor()) {
                if ((m_metatable == null || !(m_metatable.useWeakValues())) && key.isinttype() && key.toint() > 0) {
                    // a rehash might make room in the array portion for this key.
                    rehash(key.toint());
                    if (arrayset(key.toint(), value))
                        return;
                } else {
                    rehash(-1);
                }
                index = hashSlot(key);
            }
            Slot entry = (m_metatable != null)
                    ? m_metatable.entry(key, value)
                    : defaultEntry(key, value);
            hash[index] = (hash[index] != null) ? hash[index].add(entry) : entry;
            ++hashEntries;
        }
    }

    /**
     * Find the hashtable slot to use
     *
     * @param key key to look for
     * @return slot to use
     */
    private int hashSlot(LuaValue key) {
        return hashSlot(key, hash.length - 1);
    }

    private void hashRemove(LuaValue key) {
        if (hash.length > 0) {
            int index = hashSlot(key);
            for (Slot slot = hash[index]; slot != null; slot = slot.rest()) {
                StrongSlot foundSlot;
                if ((foundSlot = slot.find(key)) != null) {
                    hash[index] = hash[index].remove(foundSlot);
                    --hashEntries;
                    return;
                }
            }
        }
    }

    private boolean checkLoadFactor() {
        return hashEntries >= hash.length;
    }

    private int countHashKeys() {
        int keys = 0;
        for (Slot value : hash) {
            for (Slot slot = value; slot != null; slot = slot.rest()) {
                if (slot.first() != null)
                    keys++;
            }
        }
        return keys;
    }

    private void dropWeakArrayValues() {
        for (int i = 0; i < array.length; ++i) {
            m_metatable.arrayget(array, i);
        }
    }

    private int countIntKeys(int[] nums) {
        int total = 0;
        int i = 1;

        // Count integer keys in array part
        for (int bit = 0; bit < 31; ++bit) {
            if (i > array.length)
                break;
            int j = Math.min(array.length, 1 << bit);
            int c = 0;
            while (i <= j) {
                if (array[i++ - 1] != null)
                    c++;
            }
            nums[bit] = c;
            total += c;
        }

        // Count integer keys in hash part
        for (i = 0; i < hash.length; ++i) {
            for (Slot s = hash[i]; s != null; s = s.rest()) {
                int k;
                if ((k = s.arraykey(Integer.MAX_VALUE)) > 0) {
                    nums[log2(k)]++;
                    total++;
                }
            }
        }

        return total;
    }

    // ----------------- sort support -----------------------------
    //
    // implemented heap sort from wikipedia
    //
    // Only sorts the contiguous array part.
    //

    /*
     * newKey > 0 is next key to insert
     * newKey == 0 means number of keys not changing (__mode changed)
     * newKey < 0 next key will go in hash part
     */
    private void rehash(int newKey) {
        if (m_metatable != null && (m_metatable.useWeakKeys() || m_metatable.useWeakValues())) {
            // If this table has weak entries, hashEntries is just an upper bound.
            hashEntries = countHashKeys();
            if (m_metatable.useWeakValues()) {
                dropWeakArrayValues();
            }
        }
        int[] nums = new int[32];
        int total = countIntKeys(nums);
        if (newKey > 0) {
            total++;
            nums[log2(newKey)]++;
        }

        // Choose N such that N <= sum(nums[0..log(N)]) < 2N
        int keys = nums[0];
        int newArraySize = 0;
        for (int log = 1; log < 32; ++log) {
            keys += nums[log];
            if (total * 2 < 1 << log) {
                // Not enough integer keys.
                break;
            } else if (keys >= (1 << (log - 1))) {
                newArraySize = 1 << log;
            }
        }

        final LuaValue[] oldArray = array;
        final Slot[] oldHash = hash;
        final LuaValue[] newArray;
        final Slot[] newHash;

        // Copy existing array entries and compute number of moving entries.
        int movingToArray = 0;
        if (newKey > 0 && newKey <= newArraySize) {
            movingToArray--;
        }
        if (newArraySize != oldArray.length) {
            newArray = new LuaValue[newArraySize];
            if (newArraySize > oldArray.length) {
                for (int i = log2(oldArray.length + 1), j = log2(newArraySize) + 1; i < j; ++i) {
                    movingToArray += nums[i];
                }
            } else if (oldArray.length > newArraySize) {
                for (int i = log2(newArraySize + 1), j = log2(oldArray.length) + 1; i < j; ++i) {
                    movingToArray -= nums[i];
                }
            }
            System.arraycopy(oldArray, 0, newArray, 0, Math.min(oldArray.length, newArraySize));
        } else {
            newArray = array;
        }

        final int newHashSize = hashEntries - movingToArray
                + ((newKey < 0 || newKey > newArraySize) ? 1 : 0); // Make room for the new entry
        final int oldCapacity = oldHash.length;
        final int newCapacity;
        final int newHashMask;

        if (newHashSize > 0) {
            // round up to next power of 2.
            newCapacity = (newHashSize < MIN_HASH_CAPACITY)
                    ? MIN_HASH_CAPACITY
                    : 1 << log2(newHashSize);
            newHashMask = newCapacity - 1;
            newHash = new Slot[newCapacity];
        } else {
            newCapacity = 0;
            newHashMask = 0;
            newHash = NOBUCKETS;
        }

        // Move hash buckets
        for (Slot value : oldHash) {
            for (Slot slot = value; slot != null; slot = slot.rest()) {
                int k;
                if ((k = slot.arraykey(newArraySize)) > 0) {
                    StrongSlot entry = slot.first();
                    if (entry != null)
                        newArray[k - 1] = entry.value();
                } else if (!(slot instanceof DeadSlot)) {
                    int j = slot.keyindex(newHashMask);
                    newHash[j] = slot.relink(newHash[j]);
                }
            }
        }

        // Move array values into hash portion
        for (int i = newArraySize; i < oldArray.length; ) {
            LuaValue v;
            if ((v = oldArray[i++]) != null) {
                int slot = hashmod(LuaInteger.hashCode(i), newHashMask);
                Slot newEntry;
                if (m_metatable != null) {
                    newEntry = m_metatable.entry(valueOf(i), v);
                    if (newEntry == null)
                        continue;
                } else {
                    newEntry = defaultEntry(valueOf(i), v);
                }
                newHash[slot] = (newHash[slot] != null)
                        ? newHash[slot].add(newEntry) : newEntry;
            }
        }

        hash = newHash;
        array = newArray;
        hashEntries -= movingToArray;
    }

    @Override
    public Slot entry(LuaValue key, LuaValue value) {
        return defaultEntry(key, value);
    }

    /**
     * Sort the table using a comparator.
     *
     * @param comparator {@link LuaValue} to be called to compare elements.
     */
    public void sort(LuaValue comparator) {
        if (len().tolong() >= (long) Integer.MAX_VALUE)
            throw new LuaError("array too big: " + len().tolong());
        if (m_metatable != null && m_metatable.useWeakValues()) {
            dropWeakArrayValues();
        }
        int n = length();
        if (n > 1)
            heapSort(n, comparator.isnil() ? null : comparator);
    }

    private void heapSort(int count, LuaValue cmpfunc) {
        heapify(count, cmpfunc);
        for (int end = count; end > 1; ) {
            LuaValue a = get(end); // swap(end, 1)
            set(end, get(1));
            set(1, a);
            siftDown(1, --end, cmpfunc);
        }
    }

    private void heapify(int count, LuaValue cmpfunc) {
        for (int start = count / 2; start > 0; --start)
            siftDown(start, count, cmpfunc);
    }

    private void siftDown(int start, int end, LuaValue cmpfunc) {
        for (int root = start; root * 2 <= end; ) {
            int child = root * 2;
            if (child < end && compare(child, child + 1, cmpfunc))
                ++child;
            if (compare(root, child, cmpfunc)) {
                LuaValue a = get(root); // swap(root, child)
                set(root, get(child));
                set(child, a);
                root = child;
            } else
                return;
        }
    }

    private boolean compare(int i, int j, LuaValue cmpfunc) {
        LuaValue a = get(i), b = get(j);
        if (a == null || b == null)
            return false;
        if (cmpfunc != null) {
            return cmpfunc.call(a, b).toboolean();
        } else {
            return a.lt_b(b);
        }
    }

    /**
     * This may be deprecated in a future release.
     * It is recommended to count via iteration over next() instead
     *
     * @return count of keys in the table
     */
    public int keyCount() {
        LuaValue k = LuaConstants.NIL;
        for (int i = 0; true; i++) {
            Varargs n = next(k);
            if ((k = n.arg1()).isnil())
                return i;
        }
    }

    /**
     * This may be deprecated in a future release.
     * It is recommended to use next() instead
     *
     * @return array of keys in the table
     */
    public LuaValue[] keys() {
        Vector<LuaValue> l = new Vector<>();
        LuaValue k = LuaConstants.NIL;
        while (true) {
            Varargs n = next(k);
            if ((k = n.arg1()).isnil())
                break;
            l.addElement(k);
        }
        LuaValue[] a = new LuaValue[l.size()];
        l.copyInto(a);
        return a;
    }

    // equality w/ metatable processing
    @Override
    public LuaValue eq(LuaValue val) {
        return eq_b(val) ? LuaConstants.TRUE : LuaConstants.FALSE;
    }

    @Override
    public boolean eq_b(LuaValue val) {
        return this == val || LuaValue.eqmtcall(this, val);
        /*if (this == val) return true;
        if (m_metatable == null || !val.istable()) return false;
        LuaValue valmt = val.getmetatable();
        return valmt != null && LuaValue.eqmtcall(this, m_metatable.toLuaValue(), val, valmt);*/
    }

    /**
     * Unpack all the elements of this table
     */
    public Varargs unpack() {
        return unpack(1, this.rawlen());
    }

    /**
     * Unpack all the elements of this table from element i
     */
    public Varargs unpack(int i) {
        return unpack(i, this.rawlen());
    }

    /**
     * Unpack the elements from i to j inclusive
     */
    public Varargs unpack(int i, int j) {
        if (j < i) return LuaConstants.NONE;
        int count = j - i;
        if (count < 0)
            throw new LuaError("too many results to unpack: greater " + Integer.MAX_VALUE); // integer overflow
        int max = 0x00ffffff;
        if (count >= max)
            throw new LuaError("too many results to unpack: " + count + " (max is " + max + ')');
        int n = j + 1 - i;
        switch (n) {
            case 0:
                return LuaConstants.NONE;
            case 1:
                return get(i);
            case 2:
                return varargsOf(get(i), get(i + 1));
            default:
                if (n < 0)
                    return LuaConstants.NONE;
                try {
                    LuaValue[] v = new LuaValue[n];
                    while (--n >= 0)
                        v[n] = get(i + n);
                    return varargsOf(v);
                } catch (OutOfMemoryError e) {
                    throw new LuaError("too many results to unpack [out of memory]: " + n);
                }
        }
    }

    public int size() {
        int len = 0;
        // check array part
        int i = 0;
        for (; i < array.length; ++i) {
            if (array[i] != null) {
                len++;
            }
        }

        // check hash part
        for (i -= array.length; i < hash.length; ++i) {
            Slot slot = hash[i];
            while (slot != null) {
                StrongSlot first = slot.first();
                if (first != null) {
                    len++;
                }
                slot = slot.rest();
            }
        }
        return len;
    }

    public LuaString dump() {
        StringBuilder buf = new StringBuilder();
        HashMap<LuaValue, String> cache = new HashMap<>();
        dump(buf, this, 0, cache);
        return LuaValue.valueOf(buf.toString());
    }

    private void dump(StringBuilder buf, LuaValue obj, int idx, HashMap<LuaValue, String> cache) {
        switch (obj.type()) {
            case LuaValue.TNUMBER:
                buf.append(obj.tonumber());
                return;
            case LuaValue.TSTRING:
                addquoted(buf, obj.checkstring());
                return;
            case LuaValue.TTABLE:
                obj.checktable().dump(buf, idx, cache);
                return;
            case LuaValue.TFUNCTION:
                if (obj.isclosure()) {
                    buf.append("--function\n");
                    buf.append("loadstring ");
                    LuaValue f = obj.checkfunction();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        DumpState.dump(((LuaClosure) f).p, baos, true);
                        addquoted(buf, LuaValue.valueOf(baos.toByteArray()));
                    } catch (IOException e) {
                        buf.append(e.getMessage());
                    }
                } else {
                    buf.append("\"");
                    buf.append(obj.tojstring());
                    buf.append("\"");
                }

                return;
            default:
                buf.append(obj.tojstring());
        }
    }

    void dump(StringBuilder buf, int idx, HashMap<LuaValue, String> cache) {
        for (int i1 = 0; i1 < idx; i1++) {
            buf.append(" ");
        }
        idx = idx + 2;
        buf.append("{\n");

        // check array part
        int i = 0;
        for (; i < array.length; ++i) {
            if (array[i] != null) {
                LuaValue value = m_metatable == null ? array[i] : m_metatable.arrayget(array, i);
                if (value != null) {
                    for (int i1 = 0; i1 < idx; i1++) {
                        buf.append(" ");
                    }
                    buf.append("[").append(i + 1).append("]").append(" = ");
                    if (value.istable()) {
                        if (cache.containsKey(value)) {
                            buf.append(cache.get(value));
                        } else {
                            cache.put(value, "[" + (i + 1) + "]");
                            dump(buf, value, idx, cache);
                        }
                    } else {
                        dump(buf, value, idx, cache);
                    }

                    buf.append(";\n");
                }
            }
        }

        // check hash part
        for (i -= array.length; i < hash.length; ++i) {
            Slot slot = hash[i];
            while (slot != null) {
                StrongSlot first = slot.first();
                if (first != null) {
                    LuaValue k = first.key();
                    LuaValue v = first.value();
                    for (int i1 = 0; i1 < idx; i1++) {
                        buf.append(" ");
                    }
                    if (k.isstring())
                        buf.append(k.tojstring());
                    else if (k.isnumber())
                        buf.append("[").append(k.tonumber()).append("]");
                    else
                        buf.append("[").append(k.tojstring()).append("]");
                    buf.append(" = ");
                    if (v.istable()) {
                        if (cache.containsKey(v)) {
                            buf.append(cache.get(v));
                        } else {
                            cache.put(v, "[" + (k) + "]");
                            dump(buf, v, idx, cache);
                        }
                    } else {
                        dump(buf, v, idx, cache);
                    }
                    buf.append(";\n");
                }
                slot = slot.rest();
            }
        }

        idx = idx - 2;
        for (int i1 = 0; i1 < idx; i1++) {
            buf.append(" ");
        }
        buf.append("}");
        for (int i1 = 0; i1 < idx; i1++) {
            buf.append(" ");
        }
    }

    public List<?> values() {
        ArrayList<Object> l = new ArrayList<>();
        LuaValue k = LuaConstants.NIL;
        while (true) {
            Varargs n = next(k);
            if ((k = n.arg1()).isnil())
                break;
            l.add(CoerceLuaToJava.coerce(n.arg(2), Object.class));
        }
        return l;
    }

    public List<String> stringValues() {
        Vector<String> l = new Vector<>();
        LuaValue k = LuaConstants.NIL;
        while (true) {
            Varargs n = next(k);
            if ((k = n.arg1()).isnil())
                break;
            l.addElement(n.arg(2).optjstring(""));
        }
        return l;
    }

    public void setmetamethod(String s, LuaValue value) {
        LuaValue mt = getmetatable();
        if (mt == null) {
            mt = new LuaTable();
            setmetatable(mt);
        }
        mt.set(s, value);
    }

    @Override
    public boolean useWeakKeys() {
        return false;
    }

    @Override
    public boolean useWeakValues() {
        return false;
    }

    @Override
    public LuaValue toLuaValue() {
        return this;
    }

    @Override
    public LuaValue wrap(LuaValue value) {
        return value;
    }

    @Override
    public LuaValue arrayget(LuaValue[] array, int index) {
        return array[index];
    }

    @Override
    public LuaValue call(LuaValue arg) {
        if (arg instanceof LuaList && metatag(LuaConstants.CALL).isnil()) {
            LuaValue from = arg.get(1);
            LuaValue to = arg.get(2);
            if (to.isnil())
                to = len();
            return sub(from, to);
        }
        return super.call(arg);
    }

    @Override
    public Varargs invoke(Varargs args) {
        if (args.narg() == 1) {
            LuaValue arg = args.arg1();
            if (arg instanceof LuaList && metatag(LuaConstants.CALL).isnil()) {
                LuaValue from = arg.get(1);
                LuaValue to = arg.get(2);
                if (to.isnil())
                    to = len();
                return sub(from, to);
            }
        }
        return super.invoke(args);
    }

    /**
     * Represents a slot in the hash table.
     */
    public interface Slot {

        /**
         * Return hash{pow2,mod}( first().key().hashCode(), sizeMask )
         */
        int keyindex(int hashMask);

        /**
         * Return first Entry, if still present, or null.
         */
        StrongSlot first();

        /**
         * Compare given key with first()'s key; return first() if equal.
         */
        StrongSlot find(LuaValue key);

        /**
         * Compare given key with first()'s key; return true if equal. May
         * return true for keys no longer present in the table.
         */
        boolean keyeq(LuaValue key);

        /**
         * Return rest of elements
         */
        Slot rest();

        /**
         * Return first entry's key, iff it is an integer between 1 and max,
         * inclusive, or zero otherwise.
         */
        int arraykey(int max);

        /**
         * Set the value of this Slot's first Entry, if possible, or return a
         * new Slot whose first entry has the given value.
         */
        Slot set(StrongSlot target, LuaValue value);

        /**
         * Link the given new entry to this slot.
         */
        Slot add(Slot newEntry);

        /**
         * Return a Slot with the given value set to nil; must not return null
         * for next() to behave correctly.
         */
        Slot remove(StrongSlot target);

        /**
         * Return a Slot with the same first key and value (if still present)
         * and rest() equal to rest.
         */
        Slot relink(Slot rest);
    }

    // Metatable operations

    /**
     * Subclass of Slot guaranteed to have a strongly-referenced key and value,
     * to support weak tables.
     */
    public interface StrongSlot extends Slot {
        /**
         * Return first entry's key
         */
        LuaValue key();

        /**
         * Return first entry's value
         */
        LuaValue value();

        /**
         * Return varargsOf(key(), value()) or equivalent
         */
        Varargs toVarargs();
    }

    private static class LinkSlot implements StrongSlot {
        private Entry entry;
        private Slot next;

        LinkSlot(Entry entry, Slot next) {
            this.entry = entry;
            this.next = next;
        }

        @Override
        public LuaValue key() {
            return entry.key();
        }

        @Override
        public int keyindex(int hashMask) {
            return entry.keyindex(hashMask);
        }

        @Override
        public LuaValue value() {
            return entry.value();
        }

        @Override
        public Varargs toVarargs() {
            return entry.toVarargs();
        }

        @Override
        public StrongSlot first() {
            return entry;
        }

        @Override
        public StrongSlot find(LuaValue key) {
            return entry.keyeq(key) ? this : null;
        }

        @Override
        public boolean keyeq(LuaValue key) {
            return entry.keyeq(key);
        }

        @Override
        public Slot rest() {
            return next;
        }

        @Override
        public int arraykey(int max) {
            return entry.arraykey(max);
        }

        @Override
        public Slot set(StrongSlot target, LuaValue value) {
            if (target == this) {
                entry = entry.set(value);
                return this;
            } else {
                return setnext(next.set(target, value));
            }
        }

        @Override
        public Slot add(Slot entry) {
            return setnext(next.add(entry));
        }

        @Override
        public Slot remove(StrongSlot target) {
            if (this == target) {
                return new DeadSlot(key(), next);
            } else {
                this.next = next.remove(target);
            }
            return this;
        }

        @Override
        public Slot relink(Slot rest) {
            // This method is (only) called during rehash, so it must not change this.next.
            return (rest != null) ? new LinkSlot(entry, rest) : entry;
        }

        // this method ensures that this.next is never set to null.
        private Slot setnext(Slot next) {
            if (next != null) {
                this.next = next;
                return this;
            } else {
                return entry;
            }
        }

        @NonNull
        @Override
        public String toString() {
            return entry + "; " + next;
        }
    }

    /**
     * Base class for regular entries.
     * <p>
     * <p>
     * If the key may be an integer, the {@link #arraykey(int)} method must be
     * overridden to handle that case.
     */
    public static abstract class Entry extends Varargs implements StrongSlot {

        @Override
        public abstract LuaValue key();

        @Override
        public abstract LuaValue value();

        abstract Entry set(LuaValue value);

        @Override
        public abstract boolean keyeq(LuaValue key);

        @Override
        public abstract int keyindex(int hashMask);

        @Override
        public int arraykey(int max) {
            return 0;
        }

        @Override
        public LuaValue arg(int i) {
            return switch (i) {
                case 1 -> key();
                case 2 -> value();
                default -> LuaConstants.NIL;
            };
        }

        @Override
        public int narg() {
            return 2;
        }

        /**
         * Subclasses should redefine as "return this;" whenever possible.
         */
        @Override
        public Varargs toVarargs() {
            return varargsOf(key(), value());
        }

        @Override
        public LuaValue arg1() {
            return key();
        }

        @Override
        public Varargs subargs(int start) {
            return switch (start) {
                case 1 -> this;
                case 2 -> value();
                default -> LuaConstants.NONE;
            };
        }

        @Override
        public StrongSlot first() {
            return this;
        }

        @Override
        public Slot rest() {
            return null;
        }

        @Override
        public StrongSlot find(LuaValue key) {
            return keyeq(key) ? this : null;
        }

        @Override
        public Slot set(StrongSlot target, LuaValue value) {
            return set(value);
        }

        @Override
        public Slot add(Slot entry) {
            return new LinkSlot(this, entry);
        }

        @Override
        public Slot remove(StrongSlot target) {
            return new DeadSlot(key(), null);
        }

        @Override
        public Slot relink(Slot rest) {
            return (rest != null) ? new LinkSlot(this, rest) : this;
        }
    }

    static class NormalEntry extends Entry {
        private final LuaValue key;
        private LuaValue value;

        NormalEntry(LuaValue key, LuaValue value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public LuaValue key() {
            return key;
        }

        @Override
        public LuaValue value() {
            return value;
        }

        @Override
        public Entry set(LuaValue value) {
            this.value = value;
            return this;
        }

        @Override
        public Varargs toVarargs() {
            return this;
        }

        @Override
        public int keyindex(int hashMask) {
            return hashSlot(key, hashMask);
        }

        @Override
        public boolean keyeq(LuaValue key) {
            return key.raweq(this.key);
        }
    }

    private static class IntKeyEntry extends Entry {
        private final int key;
        private LuaValue value;

        IntKeyEntry(int key, LuaValue value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public LuaValue key() {
            return valueOf(key);
        }

        @Override
        public int arraykey(int max) {
            return (key >= 1 && key <= max) ? key : 0;
        }

        @Override
        public LuaValue value() {
            return value;
        }

        @Override
        public Entry set(LuaValue value) {
            this.value = value;
            return this;
        }

        @Override
        public int keyindex(int mask) {
            return hashmod(LuaInteger.hashCode(key), mask);
        }

        @Override
        public boolean keyeq(LuaValue key) {
            return key.raweq(this.key);
        }
    }

    /**
     * Entry class used with numeric values, but only when the key is not an integer.
     */
    private static class NumberValueEntry extends Entry {
        private final LuaValue key;
        private double value;

        NumberValueEntry(LuaValue key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public LuaValue key() {
            return key;
        }

        @Override
        public LuaValue value() {
            return valueOf(value);
        }

        @Override
        public Entry set(LuaValue value) {
            if (value.type() == TNUMBER) {
                LuaValue n = value.tonumber();
                if (!n.isnil()) {
                    this.value = n.todouble();
                    return this;
                }
            }
            return new NormalEntry(this.key, value);
        }

        @Override
        public int keyindex(int mask) {
            return hashSlot(key, mask);
        }

        @Override
        public boolean keyeq(LuaValue key) {
            return key.raweq(this.key);
        }
    }

    /**
     * A Slot whose value has been set to nil. The key is kept in a weak reference so that
     * it can be found by next().
     */
    private static class DeadSlot implements Slot {

        private final Object key;
        private Slot next;

        private DeadSlot(LuaValue key, Slot next) {
            this.key = isLargeKey(key) ? new WeakReference<>(key) : key;
            this.next = next;
        }

        private LuaValue key() {
            return (LuaValue) (key instanceof WeakReference ? ((WeakReference<?>) key).get() : key);
        }

        @Override
        public int keyindex(int hashMask) {
            // Not needed: this entry will be dropped during rehash.
            return 0;
        }

        @Override
        public StrongSlot first() {
            return null;
        }

        @Override
        public StrongSlot find(LuaValue key) {
            return null;
        }

        @Override
        public boolean keyeq(LuaValue key) {
            LuaValue k = key();
            return k != null && key.raweq(k);
        }

        @Override
        public Slot rest() {
            return next;
        }

        @Override
        public int arraykey(int max) {
            return -1;
        }

        @Override
        public Slot set(StrongSlot target, LuaValue value) {
            Slot next = (this.next != null) ? this.next.set(target, value) : null;
            if (key() != null) {
                // if key hasn't been garbage collected, it is still potentially a valid argument
                // to next(), so we can't drop this entry yet.
                this.next = next;
                return this;
            } else {
                return next;
            }
        }

        @Override
        public Slot add(Slot newEntry) {
            return (next != null) ? next.add(newEntry) : newEntry;
        }

        @Override
        public Slot remove(StrongSlot target) {
            if (key() != null) {
                next = next.remove(target);
                return this;
            } else {
                return next;
            }
        }

        @Override
        public Slot relink(Slot rest) {
            return rest;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("<dead");
            LuaValue k = key();
            if (k != null) {
                buf.append(": ");
                buf.append(k);
            }
            buf.append('>');
            if (next != null) {
                buf.append("; ");
                buf.append(next.toString());
            }
            return buf.toString();
        }
    }

}
