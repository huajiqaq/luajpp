package com.androlua.proxy;

import org.luaj.LuaConstants;
import org.luaj.LuaError;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;
import org.luaj.lib.jse.CoerceLuaToJava;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * 纯接口代理（JDK Proxy）。
 * 不支持 super 调用，接口方法直接分发给 Lua handler。
 */
public class LuaInterfaceProxy {

    /**
     * 创建多个接口的代理实例。
     */
    public static Object create(Class<?>[] ifaces, LuaValue lobj) {
        for (Class<?> iface : ifaces) {
            if (!iface.isInterface()) {
                throw new LuaError("not an interface: " + iface.getName());
            }
        }
        InvocationHandler handler = new Handler(lobj);
        return Proxy.newProxyInstance(ifaces[0].getClassLoader(), ifaces, handler);
    }

    /**
     * 创建单个接口的代理实例。
     */
    public static Object create(Class<?> iface, LuaValue lobj) {
        return create(new Class[]{iface}, lobj);
    }

    // ============================================================
    // InvocationHandler
    // ============================================================

    private record Handler(LuaValue mLuaObject) implements InvocationHandler {
        private static final int VARARGS = Modifier.TRANSIENT; // 0x80

        /**
         * 判断是否为 Object 的 equals/hashCode/toString。
         */
        private static boolean isObjectMethod(Method method) {
            if (method.getDeclaringClass() != Object.class) return false;
            String name = method.getName();
            return name.equals("equals") || name.equals("hashCode") || name.equals("toString");
        }

        /**
         * Object 方法的默认实现。
         * 只能用 switch 手动实现，反射和 MethodHandle 都会死循环。
         */
        private static Object invokeObjectMethodDefault(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> proxy.getClass().getName() + "@"
                        + Integer.toHexString(System.identityHashCode(proxy));
                default -> throw new LuaError("unexpected Object method: " + method.getName());
            };
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();

            // Object 方法：Lua 没定义就走默认实现
            if (isObjectMethod(method)) {
                if (mLuaObject.isfunction() || mLuaObject.get(name).isnil()) {
                    return invokeObjectMethodDefault(proxy, method, args);
                }
            }

            LuaValue func = mLuaObject.isfunction() ? mLuaObject : mLuaObject.get(name);
            if (func.isnil()) {
                return CoerceLuaToJava.coerce(LuaConstants.NIL, method.getReturnType());
            }

            boolean isVarArgs = (method.getModifiers() & VARARGS) != 0;
            int n = args != null ? args.length : 0;
            LuaValue[] v;

            if (isVarArgs && n > 0) {
                Object varArg = args[n - 1];
                n--;
                int m = varArg != null ? Array.getLength(varArg) : 0;
                v = new LuaValue[n + m];
                for (int i = 0; i < n; i++) {
                    v[i] = CoerceJavaToLua.coerce(args[i]);
                }
                for (int i = 0; i < m; i++) {
                    v[i + n] = CoerceJavaToLua.coerce(Array.get(varArg, i));
                }
            } else {
                v = new LuaValue[n];
                for (int i = 0; i < n; i++) {
                    v[i] = CoerceJavaToLua.coerce(args[i]);
                }
            }

            try {
                LuaValue result = func.invoke(v).arg1();
                return CoerceLuaToJava.coerce(result, method.getReturnType());
            } catch (Exception e) {
                throw new LuaError(e);
            }
        }
    }
}