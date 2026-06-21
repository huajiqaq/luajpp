package com.androlua.view;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaLayout;

import org.luaj.LuaValue;

/**
 * Lua 视图容器。
 * <p>
 * 公共 API — 将 Lua 表定义的布局渲染为 Android View。
 * <p>
 * 这是 view 包对外暴露的核心类之一，主模块可直接使用：
 * <pre>
 *   LuaView view = new LuaView(context, luaTable);
 *   parentLayout.addView(view);
 * </pre>
 */
@SuppressWarnings("unused")
public class LuaView extends FrameLayout {

    public LuaView(Context context) {
        super(context);
    }

    /**
     * 从 Lua 表创建视图。
     *
     * @param context Android 上下文
     * @param value   Lua 表定义的布局
     */
    public LuaView(Context context, LuaValue value) {
        super(context);
        addView(new LuaLayout(context).load(value).touserdata(View.class));
    }

    /**
     * 从 Lua 表创建视图（带 LuaContext）。
     * <p>
     * 推荐使用此构造器，能正确解析 Lua 上下文中的资源引用。
     *
     * @param context Android 上下文
     * @param luaCtx  Lua 上下文
     * @param value   Lua 表定义的布局
     */
    public LuaView(Context context, LuaContext luaCtx, LuaValue value) {
        super(context);
        addView(new LuaLayout(context).load(value, luaCtx.getLuaState()).touserdata(View.class));
    }
}
