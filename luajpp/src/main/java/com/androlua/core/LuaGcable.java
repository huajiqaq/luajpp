package com.androlua.core;

/**
 * 可 GC 管理的对象接口。
 * <p>
 * 实现 此接口的对象会被 LuaEngine 的 GC 列表追踪，
 * 在引擎销毁时统一调用 {@link #gc()} 释放资源。
 */
public interface LuaGcable {
    void gc();

    boolean isGc();
}
