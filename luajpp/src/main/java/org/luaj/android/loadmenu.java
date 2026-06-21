package org.luaj.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import org.luaj.LuaError;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.util.HashMap;
import java.util.Map;

public class loadmenu extends VarArgFunction {

    private final Context mContext;
    private final loadbitmap mLoadBitmap;

    public loadmenu(com.androlua.core.LuaContext context) {
        mContext = context.getContext();
        mLoadBitmap = new loadbitmap(context);
    }

    @Override
    public Varargs invoke(Varargs args) {
        Menu menu = (Menu) args.checkuserdata(1, Menu.class);
        LuaValue items = args.arg(2);
        if (!items.istable()) {
            throw new LuaError("menu config must be a table");
        }
        Map<String, MenuItem> idMap = new HashMap<>();
        loadMenu(menu, items, idMap);
        return CoerceJavaToLua.coerce(idMap);
    }

    private void loadMenu(Menu parent, LuaValue items, Map<String, MenuItem> idMap) {
        for (int i = 1; i <= items.length(); i++) {
            LuaValue cfg = items.get(i);
            if (!cfg.istable()) continue;

            LuaValue subItems = cfg.get("items");
            boolean hasSub = subItems.istable();

            int group = cfg.get("group").optint(Menu.NONE);
            int itemId = cfg.get("itemId").optint(Menu.NONE);
            int order = cfg.get("order").optint(Menu.NONE);
            String title = cfg.get("title").optjstring("");

            MenuItem menuItem;
            if (hasSub) {
                SubMenu subMenu = parent.addSubMenu(group, itemId, order, title);
                menuItem = subMenu.getItem();
                loadMenu(subMenu, subItems, idMap);
            } else {
                menuItem = parent.add(group, itemId, order, title);
            }

            // 图标
            LuaValue icon = cfg.get("icon");
            if (!icon.isnil()) setIcon(menuItem, icon);

            // 非子菜单属性
            if (!hasSub) {
                LuaValue asAction = cfg.get("asAction");
                if (!asAction.isnil()) {
                    menuItem.setShowAsAction(parseActionFlags(asAction));
                }
                LuaValue click = cfg.get("click");
                if (click.isfunction()) {
                    menuItem.setOnMenuItemClickListener(item -> {
                        click.call(CoerceJavaToLua.coerce(menuItem));
                        return true;
                    });
                }
                LuaValue id = cfg.get("id");
                if (id.isstring()) idMap.put(id.tojstring(), menuItem);
            }

            // 通用属性
            if (!cfg.get("enabled").optboolean(true)) menuItem.setEnabled(false);
            if (!cfg.get("visible").optboolean(true)) menuItem.setVisible(false);
            if (cfg.get("checkable").optboolean(false)) menuItem.setCheckable(true);
            if (cfg.get("checked").optboolean(false)) menuItem.setChecked(true);
        }
    }

    private void setIcon(MenuItem item, LuaValue icon) {
        Drawable drawable = null;
        if (icon.isstring()) {
            Varargs result = mLoadBitmap.invoke(LuaValue.valueOf(icon.tojstring()));
            Object obj = result.arg1().touserdata();
            if (obj instanceof Bitmap bmp) {
                drawable = new BitmapDrawable(mContext.getResources(), bmp);
            }
        } else if (icon.isuserdata()) {
            Object obj = icon.touserdata();
            if (obj instanceof Drawable d) drawable = d;
            else if (obj instanceof Bitmap bmp)
                drawable = new BitmapDrawable(mContext.getResources(), bmp);
        }
        if (drawable != null) item.setIcon(drawable);
    }

    private int parseActionFlags(LuaValue flags) {
        if (flags.isnumber()) return flags.toint();
        if (flags.isstring()) {
            int result = 0;
            for (String word : flags.tojstring().split("\\|")) {
                result |= switch (word.trim()) {
                    case "never" -> 0;
                    case "ifRoom" -> 1;
                    case "always" -> 2;
                    case "withText" -> 4;
                    case "collapseActionView" -> 8;
                    default -> throw new LuaError("unknown showAsAction flag: " + word);
                };
            }
            return result;
        }
        throw new LuaError("showAsAction must be number or string, got " + flags.typename());
    }
}