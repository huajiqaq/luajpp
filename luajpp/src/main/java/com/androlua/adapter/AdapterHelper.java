package com.androlua.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.androlua.internal.LuaConfig;
import com.bumptech.glide.Glide;

import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;

public final class AdapterHelper {

    private AdapterHelper() {
    }

    public static void setFields(View view, LuaTable fields) {
        for (LuaValue key : fields.keys()) {
            var keyStr = key.tojstring();
            var value = fields.jget(keyStr);
            if ("src".equalsIgnoreCase(keyStr)) {
                setHelper(view, value);
            } else {
                javaSetter(view, keyStr, value);
            }
        }
    }

    public static void setHelper(View view, Object value) {
        try {
            if (value instanceof LuaTable table) {
                setFields(view, table);
            } else if (view instanceof TextView tv) {
                tv.setText(value instanceof CharSequence ? (CharSequence) value : String.valueOf(value));
            } else if (view instanceof ImageView iv) {
                setImage(iv, value);
            }
        } catch (Exception e) {
            LuaConfig.logError("AdapterHelper", e);
        }
    }

    public static void setImage(ImageView imageView, Object value) {
        try {
            if (value instanceof Bitmap) {
                imageView.setImageBitmap((Bitmap) value);
            } else if (value instanceof String path) {
                Glide.with(imageView).load(path).into(imageView);
            } else if (value instanceof Drawable) {
                imageView.setImageDrawable((Drawable) value);
            } else if (value instanceof Number) {
                imageView.setImageResource(((Number) value).intValue());
            }
        } catch (Exception e) {
            LuaConfig.logError("AdapterHelper", e);
        }
    }

    private static void javaSetter(Object obj, String methodName, Object value) {
        CoerceJavaToLua.coerce(obj).jset(methodName, value);
    }
}
