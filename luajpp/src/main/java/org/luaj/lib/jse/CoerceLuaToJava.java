/*******************************************************************************
 * Copyright (c) 2009-2011 Luaj.org. All rights reserved.
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
package org.luaj.lib.jse;

import androidx.annotation.NonNull;

import com.androlua.internal.LuaLog;

import org.luaj.LuaConstants;
import org.luaj.LuaFunction;
import org.luaj.LuaString;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.Varargs;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to coerce values from lua to Java within the luajava library.
 * <p>
 * This class is primarily used by the {@link LuaJavaLib},
 * but can also be used directly when working with Java/lua bindings.
 * <p>
 * To coerce to specific Java values, generally the {@code toType()} methods
 * on {@link LuaValue} may be used:
 * <ul>
 * <li>{@link LuaValue#toboolean()}</li>
 * <li>{@link LuaValue#tobyte()}</li>
 * <li>{@link LuaValue#tochar()}</li>
 * <li>{@link LuaValue#toshort()}</li>
 * <li>{@link LuaValue#toint()}</li>
 * <li>{@link LuaValue#tofloat()}</li>
 * <li>{@link LuaValue#todouble()}</li>
 * <li>{@link LuaValue#tojstring()}</li>
 * <li>{@link LuaValue#touserdata()}</li>
 * <li>{@link LuaValue#touserdata(Class)}</li>
 * </ul>
 * <p>
 * For data in lua tables, the various methods on {@link LuaTable} can be used directly
 * to convert data to something more useful.
 *
 * @see LuaJavaLib
 * @see CoerceJavaToLua
 */
public class CoerceLuaToJava {

    static final Map<Object, Object> COERCIONS = Collections.synchronizedMap(new HashMap<>());
    static int SCORE_NULL_VALUE = 0x10;
    static int SCORE_NUM_WRONG_TYPE = 0x20;
    static int SCORE_INT_WRONG_TYPE = 0x80;
    static int SCORE_WRONG_TYPE = 0x100;
    static int SCORE_UNCOERCIBLE = 0x10000;

    static {
        Coercion boolCoercion = new BoolCoercion();
        Coercion byteCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_BYTE);
        Coercion charCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_CHAR);
        Coercion shortCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_SHORT);
        Coercion intCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_INT);
        Coercion longCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_LONG);
        Coercion floatCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_FLOAT);
        Coercion doubleCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_DOUBLE);
        Coercion stringCoercion = new StringCoercion(StringCoercion.TARGET_TYPE_STRING);
        //Coercion bytesCoercion = new StringCoercion(StringCoercion.TARGET_TYPE_BYTES);

        COERCIONS.put(Boolean.TYPE, boolCoercion);
        COERCIONS.put(Boolean.class, boolCoercion);
        COERCIONS.put(Byte.TYPE, byteCoercion);
        COERCIONS.put(Byte.class, byteCoercion);
        COERCIONS.put(Character.TYPE, charCoercion);
        COERCIONS.put(Character.class, charCoercion);
        COERCIONS.put(Short.TYPE, shortCoercion);
        COERCIONS.put(Short.class, shortCoercion);
        COERCIONS.put(Integer.TYPE, intCoercion);
        COERCIONS.put(Integer.class, intCoercion);
        COERCIONS.put(Long.TYPE, longCoercion);
        COERCIONS.put(Long.class, longCoercion);
        COERCIONS.put(Float.TYPE, floatCoercion);
        COERCIONS.put(Float.class, floatCoercion);
        COERCIONS.put(Double.TYPE, doubleCoercion);
        COERCIONS.put(Double.class, doubleCoercion);
        COERCIONS.put(String.class, stringCoercion);
        //COERCIONS.put(byte[].class, bytesCoercion);
    }

    /**
     * Coerce a LuaValue value to a specified java class
     *
     * @param value LuaValue to coerce
     * @param clazz Class to coerce into
     * @return Object of type clazz (or a subclass) with the corresponding value.
     */
    public static Object coerce(LuaValue value, Class<?> clazz) {
        return getCoercion(clazz).coerce(value);
    }

    public static Object arrayCoerce(LuaValue value, Class<?> clazz) {
        return new ArrayCoercion(clazz).coerce(value);
    }

    /**
     * Determine levels of inheritance between a base class and a subclass
     *
     * @param baseclass base class to look for
     * @param subclass  class from which to start looking
     * @return number of inheritance levels between subclass and baseclass,
     * or SCORE_UNCOERCIBLE if not a subclass
     */
    static int inheritanceLevels(Class<?> baseclass, Class<?> subclass) {
        if (subclass == null)
            return SCORE_UNCOERCIBLE;
        if (baseclass == subclass)
            return 0;
        int min = Math.min(SCORE_UNCOERCIBLE, inheritanceLevels(baseclass, subclass.getSuperclass()) + 1);
        Class<?>[] ifaces = subclass.getInterfaces();
        for (Class<?> iface : ifaces) min = Math.min(min, inheritanceLevels(baseclass, iface) + 1);
        return min;
    }

    static Coercion getCoercion(Class<?> c) {
        Coercion co = (Coercion) COERCIONS.get(c);
        if (co != null) {
            return co;
        }
        if (c.isArray()) {
            co = new ArrayCoercion(c.getComponentType());
        } else if (Map.class.isAssignableFrom(c)) {
            co = new MapCoercion(c);
        } else if (Collection.class.isAssignableFrom(c)) {
            co = new CollectionCoercion(c);
        }/* else	if ( c.isInterface() ) {
			co = new InterFaceCoercion(c);
		}*/ else {
            co = new ObjectCoercion(c);
        }
        COERCIONS.put(c, co);
        return co;
    }

    interface Coercion {
        int score(LuaValue value);

        Object coerce(LuaValue value);
    }

    static final class BoolCoercion implements Coercion {
        @NonNull
        public String toString() {
            return "BoolCoercion()";
        }

        public int score(LuaValue value) {
            return switch (value.type()) {
                case LuaValue.TNIL -> 32;
                case LuaValue.TBOOLEAN -> 0;
                default -> SCORE_UNCOERCIBLE;
            };
        }

        public Object coerce(LuaValue value) {
            return value.toboolean() ? Boolean.TRUE : Boolean.FALSE;
        }
    }

    record NumericCoercion(int targetType) implements Coercion {
            static final int TARGET_TYPE_BYTE = 0;
            static final int TARGET_TYPE_CHAR = 1;
            static final int TARGET_TYPE_SHORT = 2;
            static final int TARGET_TYPE_INT = 3;
            static final int TARGET_TYPE_LONG = 4;
            static final int TARGET_TYPE_FLOAT = 5;
            static final int TARGET_TYPE_DOUBLE = 6;
            static final String[] TYPE_NAMES = {"byte", "char", "short", "int", "long", "float", "double"};

        @NonNull
            public String toString() {
                return "NumericCoercion(" + TYPE_NAMES[targetType] + ")";
            }

            public int score(LuaValue value) {
                int fromStringPenalty = 0;
                if (value.type() == LuaValue.TSTRING) {
                    /*value = value.tonumber();
                    if (value.isnil()) {
                        return SCORE_UNCOERCIBLE;
                    }
                    fromStringPenalty = 4;*/
                    return SCORE_UNCOERCIBLE;
                }
                if (value.isint()) {
                    return switch (targetType) {
                        case TARGET_TYPE_BYTE -> {
                            int i = value.toint();
                            yield fromStringPenalty + ((i == (byte) i) ? 0 : SCORE_WRONG_TYPE);
                        }
                        case TARGET_TYPE_CHAR -> {
                            int i = value.toint();
                            yield fromStringPenalty + ((i == (byte) i) ? 1 : (i == (char) i) ? 0 : SCORE_WRONG_TYPE);
                        }
                        case TARGET_TYPE_SHORT -> {
                            int i = value.toint();
                            yield fromStringPenalty +
                                    ((i == (byte) i) ? 1 : (i == (short) i) ? 0 : SCORE_WRONG_TYPE);
                        }
                        case TARGET_TYPE_INT -> {
                            long i = value.tolong();
                            yield fromStringPenalty +
                                    ((i == (byte) i) ? 2 : ((i == (char) i) || (i == (short) i)) ? 1 : (i == (int) i) ? 0 : SCORE_WRONG_TYPE);
                        }
                        case TARGET_TYPE_LONG -> {
                            long i = value.tolong();
                            yield fromStringPenalty +
                                    ((i == (byte) i) ? 3 : ((i == (char) i) || (i == (short) i)) ? 2 : (i == (int) i) ? 1 : 0);
                        }
                        case TARGET_TYPE_FLOAT -> fromStringPenalty + SCORE_INT_WRONG_TYPE;
                        case TARGET_TYPE_DOUBLE -> fromStringPenalty + SCORE_INT_WRONG_TYPE;
                        default -> SCORE_WRONG_TYPE;
                    };
                } else if (value.isnumber()) {
                    return switch (targetType) {
                        case TARGET_TYPE_BYTE -> {
                            double d = value.todouble();
                            yield fromStringPenalty + ((d == (byte) d) ? 0 : SCORE_WRONG_TYPE) + SCORE_NUM_WRONG_TYPE;
                        }
                        case TARGET_TYPE_CHAR -> {
                            double d = value.todouble();
                            yield fromStringPenalty + ((d == (byte) d) ? 1 : (d == (char) d) ? 0 : SCORE_WRONG_TYPE) + SCORE_NUM_WRONG_TYPE;
                        }
                        case TARGET_TYPE_SHORT -> {
                            double d = value.todouble();
                            yield fromStringPenalty +
                                    ((d == (byte) d) ? 1 : (d == (short) d) ? 0 : SCORE_WRONG_TYPE) + SCORE_NUM_WRONG_TYPE;
                        }
                        case TARGET_TYPE_INT -> {
                            double d = value.todouble();
                            yield fromStringPenalty +
                                    ((d == (byte) d) ? 2 : ((d == (char) d) || (d == (short) d)) ? 1 : (d == (int) d) ? 0 : SCORE_WRONG_TYPE) + SCORE_NUM_WRONG_TYPE;
                        }
                        case TARGET_TYPE_LONG -> {
                            double d = value.todouble();
                            yield fromStringPenalty + ((d == (long) d) ? 0 : SCORE_WRONG_TYPE) + SCORE_NUM_WRONG_TYPE;
                        }
                        case TARGET_TYPE_FLOAT -> {
                            double d = value.todouble();
                            yield fromStringPenalty + ((d == (float) d) ? 0 : SCORE_WRONG_TYPE);
                        }
                        case TARGET_TYPE_DOUBLE -> {
                            double d = value.todouble();
                            yield fromStringPenalty + (((d == (long) d) || (d == (float) d)) ? 1 : 0);
                        }
                        default -> SCORE_WRONG_TYPE;
                    };
                } else if (value.isuserdata()) {
                    Class<?> cls = value.touserdata().getClass();
                    return switch (targetType) {
                        case TARGET_TYPE_BYTE ->
                                cls == Byte.class || cls == Byte.TYPE ? 0 : SCORE_UNCOERCIBLE;
                        case TARGET_TYPE_CHAR ->
                                cls == Character.class || cls == Character.TYPE ? 0 : SCORE_UNCOERCIBLE;
                        case TARGET_TYPE_SHORT ->
                                cls == Short.class || cls == Short.TYPE ? 0 : SCORE_UNCOERCIBLE;
                        case TARGET_TYPE_INT ->
                                cls == Integer.class || cls == Integer.TYPE ? 0 : SCORE_UNCOERCIBLE;
                        case TARGET_TYPE_LONG ->
                                cls == Long.class || cls == Long.TYPE ? 0 : SCORE_UNCOERCIBLE;
                        case TARGET_TYPE_FLOAT ->
                                cls == Float.class || cls == Float.TYPE ? 0 : SCORE_UNCOERCIBLE;
                        case TARGET_TYPE_DOUBLE ->
                                cls == Double.class || cls == Double.TYPE ? 0 : SCORE_UNCOERCIBLE;
                        default -> SCORE_UNCOERCIBLE;
                    };
                } else {
                    return SCORE_UNCOERCIBLE;
                }
            }

            public Object coerce(LuaValue value) {
                if (value.isuserdata()) {
                    Number n = value.touserdata(Number.class);
                    return switch (targetType) {
                        case TARGET_TYPE_BYTE -> n.byteValue();
                        case TARGET_TYPE_CHAR -> (char) n.intValue();
                        case TARGET_TYPE_SHORT -> n.shortValue();
                        case TARGET_TYPE_INT -> n.intValue();
                        case TARGET_TYPE_LONG -> n.longValue();
                        case TARGET_TYPE_FLOAT -> n.floatValue();
                        case TARGET_TYPE_DOUBLE -> n.doubleValue();
                        default -> null;
                    };
                }

                return switch (targetType) {
                    case TARGET_TYPE_BYTE -> (byte) value.toint();
                    case TARGET_TYPE_CHAR -> (char) value.toint();
                    case TARGET_TYPE_SHORT -> (short) value.toint();
                    case TARGET_TYPE_INT -> value.toint();
                    case TARGET_TYPE_LONG -> value.tolong();
                    case TARGET_TYPE_FLOAT -> (float) value.todouble();
                    case TARGET_TYPE_DOUBLE -> value.todouble();
                    default -> null;
                };
            }
        }

    static final class StringCoercion implements Coercion {
        public static final int TARGET_TYPE_STRING = 0;
        public static final int TARGET_TYPE_BYTES = 1;
        final int targetType;

        public StringCoercion(int targetType) {
            this.targetType = targetType;
        }

        @NonNull
        public String toString() {
            return "StringCoercion(" + (targetType == TARGET_TYPE_STRING ? "String" : "byte[]") + ")";
        }

        public int score(LuaValue value) {
            return switch (value.type()) {
                case LuaValue.TSTRING -> 0;
                case LuaValue.TNIL -> SCORE_NULL_VALUE;
                case LuaValue.TUSERDATA ->
                        value.touserdata() instanceof String ? 0 : SCORE_UNCOERCIBLE;
                default -> SCORE_UNCOERCIBLE;
            };
        }

        public Object coerce(LuaValue value) {
            if (value.isnil())
                return null;
            if (targetType == TARGET_TYPE_STRING)
                return value.tojstring();
            LuaString s = value.checkstring();
            byte[] b = new byte[s.m_length];
            s.copyInto(0, b, 0, b.length);
            return b;
        }
    }

    static final class ArrayCoercion implements Coercion {
        final Class<?> componentType;
        final Coercion componentCoercion;

        public ArrayCoercion(Class<?> componentType) {
            this.componentType = componentType;
            this.componentCoercion = getCoercion(componentType);
        }

        @NonNull
        public String toString() {
            return "ArrayCoercion(" + componentType.getName() + ")";
        }

        public int score(LuaValue value) {
            return switch (value.type()) {
                case LuaValue.TTABLE -> value.length() == 0 ? 0 : check(value);
                case LuaValue.TUSERDATA ->
                        inheritanceLevels(componentType, value.touserdata().getClass().getComponentType());
                case LuaValue.TNIL -> SCORE_NULL_VALUE;
                default -> SCORE_UNCOERCIBLE;
            };
        }

        private int check(LuaValue value) {
            int n = 0;
            int len = value.length();
            int s = 1;
            if (len > 10)
                s = len / 10;
            for (int i = 0; i < len; i += s) {
                int r = componentCoercion.score(value.get(i + 1));
                if (r > n)
                    n = r;
                if (r == SCORE_WRONG_TYPE)
                    break;
            }
            return n;
        }

        public Object coerce(LuaValue value) {
            switch (value.type()) {
                case LuaValue.TTABLE: {
                    int n = value.length();
                    Object a = Array.newInstance(componentType, n);
                    for (int i = 0; i < n; i++)
                        Array.set(a, i, componentCoercion.coerce(value.get(i + 1)));
                    return a;
                }
                case LuaValue.TUSERDATA:
                    return value.touserdata();
                case LuaValue.TNIL:
                    return null;
                default:
                    return null;
            }

        }
    }

    static final class CollectionCoercion implements Coercion {
        final Class<?> componentType;
        final Coercion componentCoercion;

        public CollectionCoercion(Class<?> componentType) {
            this.componentType = componentType;
            this.componentCoercion = new ObjectCoercion(componentType);
        }

        @NonNull
        public String toString() {
            return "CollectionCoercion(" + componentType.getName() + ")";
        }

        public int score(LuaValue value) {
            return switch (value.type()) {
                case LuaValue.TTABLE -> 10;
                case LuaValue.TUSERDATA ->
                        inheritanceLevels(componentType, value.touserdata().getClass());
                case LuaValue.TNIL -> SCORE_NULL_VALUE;
                default -> SCORE_UNCOERCIBLE;
            };
        }

        public Object coerce(LuaValue value) {
            switch (value.type()) {
                case LuaValue.TTABLE: {
                    try {
                        Collection<Object> list;
                        if (componentType.isInterface())
                            list = new ArrayList<>();
                        else
                            list = (Collection<Object>) componentType.getDeclaredConstructor().newInstance();

                        int n = value.length();
                        for (int i = 0; i < n; i++)
                            list.add(componentCoercion.coerce(value.get(i + 1)));
                        return list;
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
                        LuaLog.getInstance().addError("CoerceLuaToJava", e);
                    }

                }
                case LuaValue.TUSERDATA:
                    return value.touserdata();
                case LuaValue.TNIL:
                    return null;
                default:
                    return null;
            }
        }
    }

    static final class MapCoercion implements Coercion {
        final Class<?> componentType;
        final Coercion componentCoercion;

        public MapCoercion(Class<?> componentType) {
            this.componentType = componentType;
            this.componentCoercion = new ObjectCoercion(componentType);
        }

        @NonNull
        public String toString() {
            return "MapCoercion(" + componentType.getName() + ")";
        }

        public int score(LuaValue value) {
            return switch (value.type()) {
                case LuaValue.TTABLE -> 10;
                case LuaValue.TUSERDATA ->
                        inheritanceLevels(componentType, value.touserdata().getClass());
                case LuaValue.TNIL -> SCORE_NULL_VALUE;
                default -> SCORE_UNCOERCIBLE;
            };
        }

        public Object coerce(LuaValue value) {
            switch (value.type()) {
                case LuaValue.TTABLE: {
                    try {
                        Map<Object, Object> map;
                        if (componentType.equals(Map.class))
                            map = new HashMap<>();
                        else
                            map = (Map<Object, Object>) componentType.getDeclaredConstructor().newInstance();

                        Varargs ret = value.next(LuaConstants.NIL);
                        while (ret != LuaConstants.NIL) {
                            LuaValue k = ret.arg1();
                            map.put(componentCoercion.coerce(k), componentCoercion.coerce(ret.arg(2)));
                            ret = value.next(k);
                        }
                        return map;
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
                        LuaLog.getInstance().addError("CoerceLuaToJava", e);
                    }
                }
                case LuaValue.TUSERDATA:
                    return value.touserdata();
                case LuaValue.TNIL:
                    return null;
                default:
                    return null;
            }

        }
    }

    static final class InterFaceCoercion implements Coercion {
        final Class<?> componentType;
        final Coercion componentCoercion;

        public InterFaceCoercion(Class<?> componentType) {
            this.componentType = componentType;
            this.componentCoercion = new ObjectCoercion(componentType);
        }

        @NonNull
        public String toString() {
            return "InterFaceCoercion(" + componentType.getName() + ")";
        }

        public int score(LuaValue value) {
            return switch (value.type()) {
                case LuaValue.TTABLE, LuaValue.TFUNCTION -> SCORE_WRONG_TYPE;
                case LuaValue.TUSERDATA ->
                        inheritanceLevels(componentType, value.touserdata().getClass());
                case LuaValue.TNIL -> SCORE_NULL_VALUE;
                default -> SCORE_UNCOERCIBLE;
            };
        }

        public Object coerce(LuaValue value) {
            return switch (value.type()) {
                case LuaValue.TTABLE -> LuaJavaLib.createProxy(componentType, value);
                case LuaValue.TUSERDATA -> value.touserdata();
                case LuaValue.TNIL -> null;
                default -> null;
            };
        }
    }

    record ObjectCoercion(Class<?> targetType) implements Coercion {

        @NonNull
            public String toString() {
                return "ObjectCoercion(" + targetType.getName() + ")";
            }

            public int score(LuaValue value) {
                if (LuaValue.class.isAssignableFrom(targetType)) {
                    return inheritanceLevels(targetType, value.getClass());
                }
                return switch (value.type()) {
                    case LuaValue.TNUMBER ->
                            inheritanceLevels(targetType, value.isint() ? Integer.class : Double.class);
                    case LuaValue.TBOOLEAN -> inheritanceLevels(targetType, Boolean.class);
                    case LuaValue.TSTRING -> inheritanceLevels(targetType, String.class);
                    case LuaValue.TUSERDATA ->
                            inheritanceLevels(targetType, value.touserdata().getClass());
                    case LuaValue.TTABLE -> {
                        if (targetType.isInterface())
                            yield SCORE_WRONG_TYPE;
                        yield inheritanceLevels(targetType, LuaTable.class);
                    }
                    case LuaValue.TFUNCTION -> {
                        if (targetType.isInterface())
                            yield SCORE_WRONG_TYPE;
                        yield inheritanceLevels(targetType, LuaFunction.class);
                    }
                    case LuaValue.TNIL -> SCORE_NULL_VALUE;
                    default -> inheritanceLevels(targetType, value.getClass());
                };
            }

            public Object coerce(LuaValue value) {
                if (LuaValue.class.isAssignableFrom(targetType))
                    return value;
                return switch (value.type()) {
                    case LuaValue.TNUMBER ->
                            value.isint() ? (Object) value.toint() : (Object) value.todouble();
                    case LuaValue.TBOOLEAN -> value.toboolean() ? Boolean.TRUE : Boolean.FALSE;
                    case LuaValue.TSTRING -> value.tojstring();
                    case LuaValue.TUSERDATA -> value.optuserdata(targetType, null);
                    case LuaValue.TFUNCTION, LuaValue.TTABLE -> {
                        if (targetType.isInterface())
                            yield LuaJavaLib.createProxy(targetType, value).touserdata();
                        yield value;
                    }
                    case LuaValue.TNIL -> null;
                    default -> value;
                };
            }
        }
}
