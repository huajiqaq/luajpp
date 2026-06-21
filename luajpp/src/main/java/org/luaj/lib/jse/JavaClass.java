/* ******************************************************************************
 * Copyright (c) 2011 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ***************************************************************************** */
package org.luaj.lib.jse;


import com.androlua.internal.LuaLog;

import org.luaj.LuaError;
import org.luaj.LuaString;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.OneArgFunction;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * LuaValue that represents a Java class.
 * <p>
 * Will respond to get() and set() by returning field values, or java methods.
 * <p>
 * This class is not used directly.
 * It is returned by calls to {@link CoerceJavaToLua#coerce(Object)}
 * when a Class is supplied.
 *
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
public class JavaClass extends JavaInstance implements CoerceJavaToLua.Coercion {
    static final HashMap<LuaValue, LuaValue> classMethods = new HashMap<>();
    static final Map<Class<?>, JavaClass> classes = Collections.synchronizedMap(new HashMap<>());
    static final Map<String, JavaClass> classes2 = Collections.synchronizedMap(new HashMap<>());
    static final LuaValue NEW = valueOf("new");

    static {
        Method[] ms = Class.class.getMethods();
        for (Method m : ms) {
            classMethods.put(LuaValue.valueOf(m.getName()), JavaMethod.forMethod(m));
        }
    }

    final HashMap<LuaValue, Integer> typeCache = new HashMap<>();
    final HashMap<LuaValue, Integer> typeCache2 = new HashMap<>();
    final HashMap<LuaValue, Integer> setTypeCache = new HashMap<>();
    final HashMap<LuaValue, LuaValue> staticFinalValueCache = new HashMap<>();
    final HashMap<LuaValue, LuaValue> getterCache = new HashMap<>();
    final HashMap<LuaValue, LuaValue> setterCache = new HashMap<>();
    Map<LuaValue, Field> fields;
    Map<LuaValue, LuaValue> methods;
    Map<LuaValue, JavaClass> innerclasses;

    JavaClass(Class<?> c) {
        super(c);
        this.jclass = this;
    }

    static JavaClass forClass(Class<?> c) {
        JavaClass j = classes.get(c);
        if (j == null)
            classes.put(c, j = new JavaClass(c));
        return j;
    }

    static JavaClass forName(String c) throws ClassNotFoundException {
        JavaClass j = classes2.get(c);
        if (j == null)
            classes2.put(c, j = forClass(Class.forName(c)));
        return j;
    }

    static JavaClass forName(String c, ClassLoader loader) throws ClassNotFoundException {
        JavaClass j = classes2.get(c);
        if (j == null)
            classes2.put(c, j = forClass(Class.forName(c, true, loader)));
        return j;
    }

    @Override
    public LuaValue call() {
        try {
            return new JavaInstance(((Class<?>) m_instance).getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            LuaLog.getInstance().addError("JavaClass", e);
        }
        LuaValue m = getMethod(NEW);
        return m.call();
    }

    @Override
    public LuaValue call(LuaValue arg) {
        Class<?> obj = (Class<?>) touserdata();

        if (arg.istable()) {
            if (obj.isPrimitive()) {
                return CoerceJavaToLua.coerce(new CoerceLuaToJava.ArrayCoercion(obj).coerce(arg));
                //return new JavaInstance(CoerceLuaToJava.coerce(arg,obj));
            }
            if (obj.isInterface())
                return LuaJavaLib.createProxy(obj, arg);
            if ((obj.getModifiers() & Modifier.ABSTRACT) != 0) {
                try {
                    return LuaJavaLib.override(obj, arg);
                } catch (Exception e) {
                    throw new LuaError(e);
                }
            }
            if (Map.class.isAssignableFrom(obj))
                return CoerceJavaToLua.coerce(new CoerceLuaToJava.MapCoercion(obj).coerce(arg));

            if (List.class.isAssignableFrom(obj))
                return CoerceJavaToLua.coerce(new CoerceLuaToJava.CollectionCoercion(obj).coerce(arg));
            try {
                LuaValue m = get(NEW);
                return m.call(arg);
            } catch (Exception e) {
                return CoerceJavaToLua.coerce(new CoerceLuaToJava.ArrayCoercion(obj).coerce(arg));
            }
        }
        if (obj.isPrimitive()) {
            return new JavaInstance(CoerceLuaToJava.coerce(arg, obj));
        }
        LuaValue m = getMethod(NEW);
        return m.call(arg);
    }

    @Override
    public LuaValue call(LuaValue arg1, LuaValue arg2) {
        LuaValue m = getMethod(NEW);
        return m.call(arg1, arg2);
    }

    @Override
    public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        LuaValue m = getMethod(NEW);
        return m.call(arg1, arg2, arg3);
    }

    @Override
    public Varargs invoke(Varargs args) {
        if (args.narg() == 1) {
            Class<?> obj = (Class<?>) touserdata();
            LuaValue arg = args.arg1();

            if (arg.istable()) {
                if (obj.isPrimitive()) {
                    return CoerceJavaToLua.coerce(new CoerceLuaToJava.ArrayCoercion(obj).coerce(arg));
                    //return new JavaInstance(CoerceLuaToJava.coerce(arg,obj));
                }
                if (obj.isInterface())
                    return LuaJavaLib.createProxy(obj, arg);
                if ((obj.getModifiers() & Modifier.ABSTRACT) != 0) {
                    try {
                        return LuaJavaLib.override(obj, arg);
                    } catch (Exception e) {
                        LuaLog.getInstance().addError("JavaClass", e);
                        throw new LuaError(e);
                    }
                }
                if (Map.class.isAssignableFrom(obj))
                    return CoerceJavaToLua.coerce(new CoerceLuaToJava.MapCoercion(obj).coerce(arg));

                if (List.class.isAssignableFrom(obj))
                    return CoerceJavaToLua.coerce(new CoerceLuaToJava.CollectionCoercion(obj).coerce(arg));
                try {
                    LuaValue m = get(NEW);
                    return m.invoke(args);
                } catch (Exception e) {
                    return CoerceJavaToLua.coerce(new CoerceLuaToJava.ArrayCoercion(obj).coerce(arg));
                }
            }
            if (obj.isPrimitive()) {
                return new JavaInstance(CoerceLuaToJava.coerce(arg, obj));
            }
        }

        LuaValue m = get(NEW);
        return m.invoke(args);
    }

    @Override
    public LuaValue coerce(Object javaValue) {
        return this;
    }

    Field getField(LuaValue key) {
        if (fields == null) {
            Map<LuaValue, Field> m = new HashMap<>();
            Field[] f = ((Class<?>) m_instance).getFields();
            for (int i = f.length - 1; i >= 0; i--) {
                Field fi = f[i];
                if (Modifier.isPublic(fi.getModifiers())) {
                    m.put(LuaValue.valueOf(fi.getName()), fi);
                    try {
                        if (!fi.isAccessible())
                            fi.setAccessible(true);
                    } catch (SecurityException s) {
                        //nothing
                    }
                }
            }
            fields = m;
        }
        return fields.get(key);
    }

    public LuaValue getMethod(LuaValue key) {
        if (methods == null) {
            Map<String, List<LuaValue>> namedlists = new HashMap<>();
            Method[] m = ((Class<?>) m_instance).getMethods();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < m.length; i++) {
                Method mi = m[i];
                if (Modifier.isPublic(mi.getModifiers())) {
                    String name = mi.getName();
                    List<LuaValue> list = namedlists.get(name);
                    if (list == null)
                        namedlists.put(name, list = new ArrayList<>());
                    list.add(JavaMethod.forMethod(mi));
                }
            }
            Map<LuaValue, LuaValue> map = new HashMap<>();
            Constructor<?>[] c = ((Class<?>) m_instance).getConstructors();
            if (c.length == 0)
                c = ((Class<?>) m_instance).getDeclaredConstructors();
            List<LuaValue> list = new ArrayList<>();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < c.length; i++) {
                if (Modifier.isPublic(c[i].getModifiers())) {
                    c[i].setAccessible(true);
                    list.add(JavaConstructor.forConstructor(c[i]));
                }
            }
            switch (list.size()) {
                case 0:
                    break;
                case 1:
                    map.put(NEW, list.get(0));
                    break;
                default:
                    //noinspection SuspiciousToArrayCall,ToArrayCallWithZeroLengthArrayArgument
                    map.put(NEW, JavaConstructor.forConstructors(list.toArray(new JavaConstructor[list.size()])));
                    break;
            }
            map.putAll(classMethods);
            for (Entry<String, List<LuaValue>> e : namedlists.entrySet()) {
                String name = e.getKey();
                List<LuaValue> methods = e.getValue();
                //noinspection ToArrayCallWithZeroLengthArrayArgument,SuspiciousToArrayCall
                map.put(LuaValue.valueOf(name),
                        methods.size() == 1 ?
                                methods.get(0) :
                                JavaMethod.forMethods(methods.toArray(new JavaMethod[methods.size()])));
            }
            methods = map;
        }
        return methods.get(key);
    }

    JavaClass getInnerClass(LuaValue key) {
        if (innerclasses == null) {
            Map<LuaValue, JavaClass> m = new HashMap<>();
            for (Class<?> c = (Class<?>) m_instance; c != null; c = c.getSuperclass()) {
                for (Class<?> member : c.getDeclaredClasses()) {
                    if (Modifier.isPublic(member.getModifiers())) {
                        String name = member.getName();
                        String stub = name.substring(Math.max(name.lastIndexOf('$'), name.lastIndexOf('.')) + 1);
                        LuaString k = LuaValue.valueOf(stub);
                        if (!m.containsKey(k))
                            m.put(k, forClass(member));
                    }
                }
            }

            /*
             * 遗留代码，疑似存在数组越界风险：
             * 循环从 c.length 开始，首次迭代访问 c[c.length]，有效索引范围为 0 ~ c.length-1，
             * 若数组非空将抛出 ArrayIndexOutOfBoundsException。
             * 此外 getDeclaredClasses() 仅返回当前类的内部类，需配合 getClasses() 才能覆盖父类，代码冗余。
             */
            /*
            Class[] c = ((Class) m_instance).getClasses();
            for (int i = c.length; i > 0; i--) {
                Class ci = c[i];
                String name = ci.getName();
                String stub = name.substring(Math.max(name.lastIndexOf('$'), name.lastIndexOf('.')) + 1);
                m.put(LuaValue.valueOf(stub), ci);
            }
            c = ((Class) m_instance).getDeclaredClasses();
            for (int i = c.length; i > 0; i--) {
                Class ci = c[i];
                if(!Modifier.isPublic(ci.getModifiers()))
                    continue;
                String name = ci.getName();
                String stub = name.substring(Math.max(name.lastIndexOf('$'), name.lastIndexOf('.')) + 1);
                m.put(LuaValue.valueOf(stub), ci);
            }
            */
            innerclasses = m;
        }
        return innerclasses.get(key);
    }

    public LuaValue getConstructor() {
        return getMethod(NEW);
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.isnumber())
            return CoerceJavaToLua.arrayCoercion.coerce(Array.newInstance((Class<?>) touserdata(), key.toint()));
        return switch (key.tojstring()) {
            case "override" -> new LuaJavaLib.override(this);
            case "new" -> getMethod(key);
            case "array" -> new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue arg) {
                    return CoerceJavaToLua.coerce(new CoerceLuaToJava.ArrayCoercion((Class<?>) m_instance).coerce(arg));
                }
            };
            case "class" -> this;
            default -> super.get(key);
        };
    }

}