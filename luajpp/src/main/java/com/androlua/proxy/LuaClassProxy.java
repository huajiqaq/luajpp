package com.androlua.proxy;

import android.content.Context;

import com.android.dx.stock.ProxyBuilder;

import com.androlua.LuaApplication;
import com.androlua.core.LuaContext;
import com.androlua.internal.LuaConfig;
import com.androlua.util.LuaUtil;

import org.luaj.LuaConstants;
import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;
import org.luaj.lib.jse.CoerceJavaToLua;
import org.luaj.lib.jse.CoerceLuaToJava;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 ProxyBuilder 的类方法重写。
 */
public class LuaClassProxy {

    private static final File DEX_CACHE;

    static {
        Context ctx = LuaApplication.getInstance();
        DEX_CACHE = new File(ctx.getCodeCacheDir(), "lua_proxy");
    }

    private final Class<?> type;
    private final List<Class<?>> ifaces = new ArrayList<>();

    public LuaClassProxy(Class<?> type) {
        this.type = type;
    }

    public LuaClassProxy(String name) throws ClassNotFoundException {
        this(Class.forName(name));
    }

    public static boolean canOverride(Method m) {
        int mod = m.getModifiers();
        if (Modifier.isFinal(mod) || Modifier.isStatic(mod) || Modifier.isPrivate(mod))
            return false;
        return !(m.getName().equals("finalize") && m.getParameterTypes().length == 0);
    }

    public static Object callSuper(Object obj, Method m, Set<String> proxyNames, Object... args) throws Throwable {
        if (proxyNames != null && proxyNames.contains(m.getName())) {
            try {
                return ProxyBuilder.callSuper(obj, m, args);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return m.invoke(obj, args);
    }

    public static boolean isProxy(Class<?> c) {
        return ProxyBuilder.isProxyClass(c);
    }

    private static Method match(List<Method> ms, int n, Varargs a) {
        List<Method> cs = new ArrayList<>();
        for (Method m : ms) if (m.getParameterTypes().length == n) cs.add(m);
        if (cs.size() == 1) return cs.get(0);
        for (Method m : cs) if (typesMatch(m.getParameterTypes(), a)) return m;
        return cs.isEmpty() ? null : cs.get(0);
    }

    private static boolean typesMatch(Class<?>[] pts, Varargs a) {
        for (int i = 0; i < pts.length; i++) {
            Object arg = CoerceLuaToJava.coerce(a.arg(i + 2), Object.class);
            if (arg != null && !pts[i].isInstance(arg)) return false;
        }
        return true;
    }

    private static Varargs invoke(Object target, Method m, Set<String> proxyNames, Varargs a, int off, int n) {
        Object[] ja = new Object[n];
        Class<?>[] pts = m.getParameterTypes();
        for (int i = 0; i < n; i++)
            ja[i] = CoerceLuaToJava.coerce(a.arg(i + off), i < pts.length ? pts[i] : Object.class);
        try {
            return CoerceJavaToLua.coerce(LuaClassProxy.callSuper(target, m, proxyNames, ja));
        } catch (Throwable e) {
            throw new LuaError(e);
        }
    }

    public Object create(LuaContext luaContext, Varargs args) {
        DEX_CACHE.mkdirs();
        LuaValue handlers = args.arg1();
        int n = args.narg() - 1;
        Object[] ctorArgs = new Object[n];
        for (int i = 0; i < n; i++)
            ctorArgs[i] = CoerceLuaToJava.coerce(args.arg(i + 2), Object.class);

        Set<String> names = keys(handlers);
        for (Class<?> i : matchingInterfaces(names)) if (!ifaces.contains(i)) ifaces.add(i);

        try {
            ProxyBuilder<?> b = ProxyBuilder.forClass(type)
                    .dexCache(DEX_CACHE)
                    .handler(new LuaHandler(handlers, luaContext))
                    .onlyMethods(methods(names));

            if (!ifaces.isEmpty()) b.implementing(ifaces.toArray(new Class[0]));

            if (ctorArgs.length > 0) {
                b.constructorArgTypes(types(ctorArgs)).constructorArgValues(ctorArgs);
            } else {
                if (hasConstructor(Context.class)) {
                    b.constructorArgTypes(Context.class).constructorArgValues(luaContext.getContext());
                } else if (!hasConstructor()) {
                    throw new LuaError("no suitable constructor for: " + type.getName());
                }
            }

            return b.build();
        } catch (IOException e) {
            throw new LuaError(e);
        } finally {
            try {
                LuaUtil.rmDir(DEX_CACHE);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean hasConstructor(Class<?>... paramTypes) {
        try {
            type.getConstructor(paramTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Class<?>[] types(Object... args) {
        Class<?>[] ts = new Class[args.length];
        for (int i = 0; i < args.length; i++)
            ts[i] = args[i] != null ? args[i].getClass() : Object.class;
        return ts;
    }

    private Method[] methods(Set<String> names) {
        List<Method> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
            collect(c.getDeclaredMethods(), names, list, seen);
        for (Class<?> i : ifaces) collectInterface(i, names, list, seen);
        return list.toArray(new Method[0]);
    }

    private void collectInterface(Class<?> i, Set<String> names, List<Method> list, Set<String> seen) {
        if (!i.isInterface()) return;
        collect(i.getDeclaredMethods(), names, list, seen);
        for (Class<?> p : i.getInterfaces()) collectInterface(p, names, list, seen);
    }

    private void collect(Method[] methods, Set<String> names, List<Method> list, Set<String> seen) {
        for (Method m : methods) {
            if (!seen.add(m.getName() + Arrays.toString(m.getParameterTypes()))) continue;
            if (names != null && !names.contains(m.getName())) continue;
            if (!canOverride(m)) throw new LuaError("cannot override: " + m);
            list.add(m);
        }
    }

    private Set<String> keys(LuaValue t) {
        Set<String> s = new HashSet<>();
        if (!t.istable()) return s;
        LuaValue k = LuaConstants.NIL;
        while (true) {
            Varargs n = t.next(k);
            if (n.isnil(1)) break;
            s.add(n.arg1().tojstring());
            k = n.arg1();
        }
        return s;
    }

    // ---- handler ----

    private List<Class<?>> matchingInterfaces(Set<String> names) {
        List<Class<?>> r = new ArrayList<>();
        for (Class<?> i : allInterfaces()) {
            Method[] ms = i.getDeclaredMethods();
            if (ms.length == 0) continue;
            boolean ok = true;
            for (Method m : ms)
                if (!names.contains(m.getName())) {
                    ok = false;
                    break;
                }
            if (ok) r.add(i);
        }
        return r;
    }

    // ---- super metatable ----

    private Set<Class<?>> allInterfaces() {
        Set<Class<?>> s = new HashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
            for (Class<?> i : c.getInterfaces()) addAll(s, i);
        return s;
    }

    private void addAll(Set<Class<?>> s, Class<?> i) {
        if (!s.add(i)) return;
        for (Class<?> p : i.getInterfaces()) addAll(s, p);
    }

    public static class LuaHandler implements InvocationHandler {
        private final LuaValue obj;
        private final Map<Integer, LuaValue> cache = new HashMap<>();
        private final Set<String> proxyNames;
        private LuaContext ctx;

        public LuaHandler(LuaValue obj) {
            this.obj = obj;
            this.proxyNames = keys(obj);
        }

        public LuaHandler(LuaValue obj, LuaContext ctx) {
            this.obj = obj;
            this.ctx = ctx;
            this.proxyNames = keys(obj);
        }

        private static Set<String> keys(LuaValue t) {
            Set<String> s = new HashSet<>();
            if (!t.istable()) return s;
            LuaValue k = LuaConstants.NIL;
            while (true) {
                Varargs n = t.next(k);
                if (n.isnil(1)) break;
                s.add(n.arg1().tojstring());
                k = n.arg1();
            }
            return s;
        }

        private static Object def(Class<?> t) {
            if (t == boolean.class) return false;
            return t.isPrimitive() ? 0 : null;
        }

        public void setContext(LuaContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public Object invoke(Object target, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            Class<?> ret = method.getReturnType();
            LuaValue fn = obj.isfunction() ? obj : obj.get(name);
            if (fn.isnil()) return def(ret);

            Object[] callArgs;
            if (Modifier.isAbstract(method.getModifiers())) {
                callArgs = args != null ? args : new Object[0];
            } else {
                callArgs = new Object[(args != null ? args.length : 0) + 1];
                callArgs[0] = superTable(target, method.getDeclaringClass());
                if (args != null) System.arraycopy(args, 0, callArgs, 1, args.length);
            }

            try {
                if (ret == void.class) {
                    fn.jcall(callArgs);
                    return null;
                }
                Object r = fn.jcall(callArgs);
                if (r == null) return def(ret);
                if (!ret.isPrimitive() && r.getClass() != ret)
                    return CoerceLuaToJava.coerce(LuaValue.userdataOf(r), ret);
                return r;
            } catch (LuaError e) {
                if (ctx != null) ctx.sendError(name, e);
                else LuaConfig.logError("LuaClassProxy", e);
                return def(ret);
            }
        }

        private LuaValue superTable(Object target, Class<?> sc) {
            int k = System.identityHashCode(target);
            LuaValue v = cache.get(k);
            if (v != null) return v;
            v = buildSuper(target, sc);
            cache.put(k, v);
            return v;
        }

        private LuaValue buildSuper(Object target, Class<?> sc) {
            Map<String, List<Method>> g = new HashMap<>();
            for (Method m : sc.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || Modifier.isPrivate(m.getModifiers()))
                    continue;
                List<Method> list = g.get(m.getName());
                if (list == null) {
                    list = new ArrayList<>();
                    g.put(m.getName(), list);
                }
                list.add(m);
            }
            LuaValue t = new LuaTable();
            LuaValue mt = new LuaTable();
            mt.set("__index", new Index(target, g, proxyNames));
            mt.set("__call", new Dispatch(target, g, proxyNames));
            t.setmetatable(mt);
            return t;
        }
    }

    private static class Index extends VarArgFunction {
        private final Object target;
        private final Map<String, List<Method>> g;
        private final Set<String> proxyNames;

        Index(Object t, Map<String, List<Method>> g, Set<String> p) {
            target = t;
            this.g = g;
            proxyNames = p;
        }

        @Override
        public Varargs invoke(Varargs a) {
            List<Method> ms = g.get(a.arg(2).tojstring());
            return ms != null && !ms.isEmpty() ? new Fn(target, ms, proxyNames) : LuaConstants.NIL;
        }
    }

    private static class Dispatch extends VarArgFunction {
        private final Object target;
        private final Map<String, List<Method>> g;
        private final Set<String> proxyNames;

        Dispatch(Object t, Map<String, List<Method>> g, Set<String> p) {
            target = t;
            this.g = g;
            proxyNames = p;
        }

        @Override
        public Varargs invoke(Varargs a) {
            int n = a.narg();
            if (n < 1) throw new LuaError("super() needs method name");
            List<Method> ms = g.get(a.arg1().tojstring());
            if (ms == null) throw new LuaError("no super method");
            Method m = match(ms, n - 1, a);
            if (m == null) throw new LuaError("no overload");
            return LuaClassProxy.invoke(target, m, proxyNames, a, 2, n - 1);
        }
    }

    private static class Fn extends VarArgFunction {
        private final Object target;
        private final List<Method> ms;
        private final Set<String> proxyNames;

        Fn(Object t, List<Method> m, Set<String> p) {
            target = t;
            ms = m;
            proxyNames = p;
        }

        @Override
        public Varargs invoke(Varargs a) {
            if (ms.size() == 1)
                return LuaClassProxy.invoke(target, ms.get(0), proxyNames, a, 1, a.narg());
            Method m = match(ms, a.narg(), a);
            return m != null ? LuaClassProxy.invoke(target, m, proxyNames, a, 1, a.narg()) : LuaConstants.NIL;
        }
    }
}