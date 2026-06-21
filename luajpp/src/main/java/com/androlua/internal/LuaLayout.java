package com.androlua.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.androlua.adapter.LuaAdapter;
import com.androlua.adapter.LuaPagerAdapter;
import com.androlua.core.LuaContext;
import com.androlua.drawable.LuaBitmapDrawable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior;
import com.google.android.material.behavior.HideViewOnScrollBehavior;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.search.SearchBar;
import com.google.android.material.sidesheet.SideSheetBehavior;
import com.google.android.material.transformation.FabTransformationScrimBehavior;
import com.google.android.material.transformation.FabTransformationSheetBehavior;

import org.luaj.LuaConstants;
import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.VarArgFunction;
import org.luaj.lib.jse.CoerceJavaToLua;
import org.luaj.lib.jse.CoerceLuaToJava;
import org.luaj.lib.jse.JavaInstance;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 运行时 Lua 布局加载器，将 Lua 表解析为 Android View 层次。
 */
public final class LuaLayout {

    // ==================== 内部数据结构 ====================

    private record ResourceRef(int id, Kind kind) {
        enum Kind {ATTR, STYLE}

        boolean valid() {
            return id != 0;
        }
    }

    private record StyleConfig(int theme, int attr, int res, int legacy) {
        static final StyleConfig NONE = new StyleConfig(0, 0, 0, 0);

        boolean hasAny() {
            return (theme | attr | res | legacy) != 0;
        }
    }

    // ==================== 常量映射（不可变） ====================

    private static final Map<String, Integer> VALUE_MAP;
    private static final Map<String, Integer> SCALE_TYPE_MAP;
    private static final Map<String, Integer> RULE_MAP;
    private static final Map<String, Integer> UNIT_MAP;
    private static final Map<String, CoordinatorLayout.Behavior<?>> BEHAVIOR_MAP;

    /**
     * 全局 View ID 映射（跨实例共享）
     */
    private static final Map<String, Integer> ID_MAP = new ConcurrentHashMap<>();

    private static final String[] PADDING_KEYS = {"paddingLeft", "paddingTop", "paddingRight", "paddingBottom"};
    private static final String[] MARGIN_KEYS = {"layout_marginLeft", "layout_marginTop", "layout_marginRight", "layout_marginBottom"};

    private static final LuaValue WRAP_CONTENT = CoerceJavaToLua.coerce(ViewGroup.LayoutParams.WRAP_CONTENT);
    private static final int ID_START = 0x7f000000;

    /**
     * 预计算 ScaleType 数组，避免每次调用 values() 分配新数组
     */
    private static final ImageView.ScaleType[] SCALE_TYPES = ImageView.ScaleType.values();

    /**
     * 预编译管道分隔正则，避免每次 str.split() 重新编译
     */
    private static final Pattern PIPE_PATTERN = Pattern.compile("\\|");

    static {
        VALUE_MAP = initValueMap();
        SCALE_TYPE_MAP = Map.ofEntries(
                entry("matrix", 0), entry("fitCenter", 1), entry("fitEnd", 2), entry("fitStart", 3),
                entry("fitXY", 4), entry("center", 5), entry("centerCrop", 6), entry("centerInside", 7)
        );
        RULE_MAP = Map.ofEntries(
                entry("layout_above", 2), entry("layout_alignBaseline", 4), entry("layout_alignBottom", 8),
                entry("layout_alignEnd", 19), entry("layout_alignLeft", 5), entry("layout_alignParentBottom", 12),
                entry("layout_alignParentEnd", 21), entry("layout_alignParentLeft", 9), entry("layout_alignParentRight", 11),
                entry("layout_alignParentStart", 20), entry("layout_alignParentTop", 10), entry("layout_alignRight", 7),
                entry("layout_alignStart", 18), entry("layout_alignTop", 6), entry("layout_alignWithParentIfMissing", 0),
                entry("layout_below", 3), entry("layout_centerHorizontal", 14), entry("layout_centerInParent", 13),
                entry("layout_centerVertical", 15), entry("layout_toEndOf", 17), entry("layout_toLeftOf", 0),
                entry("layout_toRightOf", 1), entry("layout_toStartOf", 16)
        );
        UNIT_MAP = Map.of(
                "px", TypedValue.COMPLEX_UNIT_PX, "dp", TypedValue.COMPLEX_UNIT_DIP,
                "sp", TypedValue.COMPLEX_UNIT_SP, "pt", TypedValue.COMPLEX_UNIT_PT,
                "in", TypedValue.COMPLEX_UNIT_IN, "mm", TypedValue.COMPLEX_UNIT_MM
        );
        BEHAVIOR_MAP = new HashMap<>();
        BEHAVIOR_MAP.put("@string/appbar_scrolling_view_behavior", new AppBarLayout.ScrollingViewBehavior());
        BEHAVIOR_MAP.put("@string/bottom_sheet_behavior", new BottomSheetBehavior<>());
        BEHAVIOR_MAP.put("@string/side_sheet_behavior", new SideSheetBehavior<>());
        BEHAVIOR_MAP.put("@string/hide_bottom_view_on_scroll_behavior", new HideBottomViewOnScrollBehavior<>());
        BEHAVIOR_MAP.put("@string/hide_view_on_scroll_behavior", new HideViewOnScrollBehavior<>());
        BEHAVIOR_MAP.put("@string/searchbar_scrolling_view_behavior", new SearchBar.ScrollingViewBehavior());
        BEHAVIOR_MAP.put("@string/fab_transformation_scrim_behavior", new FabTransformationScrimBehavior());
        BEHAVIOR_MAP.put("@string/fab_transformation_sheet_behavior", new FabTransformationSheetBehavior());
    }

    private static Map.Entry<String, Integer> entry(String key, int value) {
        return Map.entry(key, value);
    }

    private static Map<String, Integer> initValueMap() {
        var m = new HashMap<String, Integer>(128);
        m.put("auto", 0);
        m.put("low", 1);
        m.put("high", 2);
        m.put("yes", 1);
        m.put("no", 2);
        m.put("none", 0);
        m.put("software", 1);
        m.put("hardware", 2);
        m.put("ltr", 0);
        m.put("rtl", 1);
        m.put("inherit", 2);
        m.put("locale", 3);
        m.put("insideOverlay", 0x0);
        m.put("insideInset", 0x01000000);
        m.put("outsideOverlay", 0x02000000);
        m.put("outsideInset", 0x03000000);
        m.put("visible", 0);
        m.put("invisible", 4);
        m.put("gone", 8);
        m.put("wrap_content", -2);
        m.put("fill_parent", -1);
        m.put("match_parent", -1);
        m.put("wrap", -2);
        m.put("fill", -1);
        m.put("match", -1);
        m.put("web", 0x01);
        m.put("email", 0x02);
        m.put("phon", 0x04);
        m.put("map", 0x08);
        m.put("all", 0x0f);
        m.put("vertical", 1);
        m.put("horizontal", 0);
        m.put("axis_clip", 8);
        m.put("axis_pull_after", 4);
        m.put("axis_pull_before", 2);
        m.put("axis_specified", 1);
        m.put("axis_x_shift", 0);
        m.put("axis_y_shift", 4);
        m.put("bottom", 80);
        m.put("center", 17);
        m.put("center_horizontal", 1);
        m.put("center_vertical", 16);
        m.put("clip_horizontal", 8);
        m.put("clip_vertical", 128);
        m.put("display_clip_horizontal", 16777216);
        m.put("display_clip_vertical", 268435456);
        m.put("fill_horizontal", 7);
        m.put("fill_vertical", 112);
        m.put("horizontal_gravity_mask", 7);
        m.put("left", 3);
        m.put("no_gravity", 0);
        m.put("relative_horizontal_gravity_mask", 8388615);
        m.put("relative_layout_direction", 8388608);
        m.put("right", 5);
        m.put("start", 8388611);
        m.put("top", 48);
        m.put("vertical_gravity_mask", 112);
        m.put("end", 8388613);
        m.put("gravity", 1);
        m.put("textStart", 2);
        m.put("textEnd", 3);
        m.put("textCenter", 4);
        m.put("viewStart", 5);
        m.put("viewEnd", 6);
        m.put("text", 0x00000001);
        m.put("textCapCharacters", 0x00001001);
        m.put("textCapWords", 0x00002001);
        m.put("textCapSentences", 0x00004001);
        m.put("textAutoCorrect", 0x00008001);
        m.put("textAutoComplete", 0x00010001);
        m.put("textMultiLine", 0x00020001);
        m.put("textImeMultiLine", 0x00040001);
        m.put("textNoSuggestions", 0x00080001);
        m.put("textUri", 0x00000011);
        m.put("textEmailAddress", 0x00000021);
        m.put("textEmailSubject", 0x00000031);
        m.put("textShortMessage", 0x00000041);
        m.put("textLongMessage", 0x00000051);
        m.put("textPersonName", 0x00000061);
        m.put("textPostalAddress", 0x00000071);
        m.put("textPassword", 0x00000081);
        m.put("textVisiblePassword", 0x00000091);
        m.put("textWebEditText", 0x000000a1);
        m.put("textFilter", 0x000000b1);
        m.put("textPhonetic", 0x000000c1);
        m.put("textWebEmailAddress", 0x000000d1);
        m.put("textWebPassword", 0x000000e1);
        m.put("number", 0x00000002);
        m.put("numberSigned", 0x00001002);
        m.put("numberDecimal", 0x00002002);
        m.put("numberPassword", 0x00000012);
        m.put("phone", 0x00000003);
        m.put("datetime", 0x00000004);
        m.put("date", 0x00000014);
        m.put("time", 0x00000024);
        m.put("normal", 0x00000000);
        m.put("actionUnspecified", 0x00000000);
        m.put("actionNone", 0x00000001);
        m.put("actionGo", 0x00000002);
        m.put("actionSearch", 0x00000003);
        m.put("actionSend", 0x00000004);
        m.put("actionNext", 0x00000005);
        m.put("actionDone", 0x00000006);
        m.put("actionPrevious", 0x00000007);
        m.put("flagNoFullscreen", 0x2000000);
        m.put("flagNavigatePrevious", 0x4000000);
        m.put("flagNavigateNext", 0x8000000);
        m.put("flagNoExtractUi", 0x10000000);
        m.put("flagNoAccessoryAction", 0x20000000);
        m.put("flagNoEnterAction", 0x40000000);
        m.put("flagForceAscii", -0x80000000);
        m.put("noScroll", 0);
        m.put("scroll", 1);
        m.put("exitUntilCollapsed", 2);
        m.put("enterAlways", 4);
        m.put("enterAlwaysCollapsed", 8);
        m.put("snap", 16);
        m.put("snapMargins", 32);
        m.put("pin", 1);
        m.put("parallax", 2);
        return Map.copyOf(m);
    }

    // ==================== 构造器成功缓存（跨实例共享） ====================

    /**
     * 记录 View 类的最佳构造器签名，避免每次重试
     */
    private static final Map<Class<?>, Integer> CTOR_HITS = new ConcurrentHashMap<>();
    private static final int CTOR_4ARG = 4;    // (Context, AttributeSet, int, int)
    private static final int CTOR_3ARG = 3;    // (Context, AttributeSet, int)
    private static final int CTOR_1ARG = 1;    // (Context)

    // ==================== 实例字段 ====================

    private final Context mContext;
    private final DisplayMetrics mDisplayMetrics;
    private final Map<String, LuaValue> mViewMap = new HashMap<>();
    private final LuaValue mLuaContext;
    private final LuaContext mLuaCtx;
    private int mNextId = ID_START;
    private final Map<String, ResourceRef> mResourceCache = new HashMap<>();
    private final HashMap<Class<?>, Constructor<?>> mConstructorCache = new HashMap<>();

    /**
     * 解析值缓存（LRU），避免相同字符串重复 parseValue
     */
    private final Map<String, Object> mParseCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > 128;
        }
    };

    /**
     * 主题属性值缓存
     */
    private final Map<String, Object> mThemeCache = new HashMap<>();

    public LuaLayout(Context context) {
        mContext = context;
        mDisplayMetrics = context.getResources().getDisplayMetrics();
        mLuaContext = CoerceJavaToLua.coerce(context);
        mLuaCtx = mLuaContext.touserdata(LuaContext.class);
    }

    public Map<String, Integer> getIdMap() {
        return ID_MAP;
    }

    public Map<String, LuaValue> getViewMap() {
        return mViewMap;
    }

    @Nullable
    public LuaValue getView(String id) {
        return mViewMap.get(id);
    }

    public int type() {
        return LuaValue.TUSERDATA;
    }

    public String typename() {
        return "LuaLayout";
    }

    public LuaValue get(LuaValue key) {
        return get(key.tojstring());
    }

    public LuaValue get(String key) {
        return mViewMap.get(key);
    }

    private int obtainViewId(String idString) {
        Integer id = ID_MAP.get(idString);
        if (id == null) {
            id = mNextId++;
            ID_MAP.put(idString, id);
        }
        return id;
    }

    // ==================== 资源解析 ====================

    private ResourceRef resolveResource(LuaValue value, String fieldName) {
        var kind = "styleAttr".equals(fieldName) || (value.isstring() && value.tojstring().charAt(0) == '?')
                ? ResourceRef.Kind.ATTR : ResourceRef.Kind.STYLE;

        if (value.isnil()) return new ResourceRef(0, kind);
        if (value.isnumber()) return new ResourceRef(value.toint(), kind);
        if (!value.isstring()) {
            mLuaCtx.sendMsg("loadlayout: " + fieldName + " requires resource id or string, got " + value.typename());
            return new ResourceRef(0, kind);
        }

        var ref = value.tojstring().trim();
        if (ref.isEmpty() || "nil".equals(ref)) return new ResourceRef(0, kind);

        // 尝试直接解析为数字
        int parsedInt = tryParseInt(ref, Integer.MIN_VALUE);
        if (parsedInt != Integer.MIN_VALUE) return new ResourceRef(parsedInt, kind);

        var cacheKey = fieldName + "|" + kind.name() + "|" + ref;
        var cached = mResourceCache.get(cacheKey);
        if (cached != null) return cached;

        int resolved = resolveResourceId(ref, kind);
        if (resolved == 0)
            mLuaCtx.sendMsg("loadlayout: cannot resolve " + fieldName + " resource '" + ref + "'");

        var result = new ResourceRef(resolved, kind);
        mResourceCache.put(cacheKey, result);
        return result;
    }

    @SuppressLint("DiscouragedApi")
    private int resolveResourceId(String ref, ResourceRef.Kind kind) {
        var normalized = ref;
        if (normalized.startsWith("?attr/")) normalized = normalized.substring(6);
        else if (normalized.startsWith("?android:attr/"))
            normalized = "android:" + normalized.substring(14);
        else if (normalized.charAt(0) == '?') normalized = normalized.substring(1);
        else if (normalized.charAt(0) == '@') normalized = normalized.substring(1);

        int slashIndex = normalized.indexOf('/');
        String typeName, entryName;
        if (slashIndex >= 0) {
            typeName = normalized.substring(0, slashIndex);
            entryName = normalized.substring(slashIndex + 1);
        } else {
            typeName = kind == ResourceRef.Kind.ATTR ? "attr" : "style";
            entryName = normalized;
        }

        boolean isAndroid = typeName.startsWith("android:");
        var cleanType = typeName.replace("android:", "");
        var cleanName = entryName.replace("android:", "");

        return mContext.getResources().getIdentifier(cleanName, cleanType, isAndroid ? "android" : mContext.getPackageName());
    }

    @Nullable
    private Object resolveThemeAttribute(String ref) {
        if (ref.charAt(0) != '?') return null;

        // 检查主题属性缓存
        var cached = mThemeCache.get(ref);
        if (cached != null) return cached;

        var attrName = ref.substring(1);
        if (attrName.startsWith("attr/")) attrName = attrName.substring(5);
        else if (attrName.startsWith("android:attr/")) attrName = attrName.substring(13);
        if (attrName.isEmpty()) return null;

        boolean isAndroid = ref.contains("android:");
        @SuppressLint("DiscouragedApi") int attrId = mContext.getResources().getIdentifier(attrName, "attr", isAndroid ? "android" : mContext.getPackageName());
        if (attrId == 0) return null;

        var outValue = new TypedValue();
        if (!mContext.getTheme().resolveAttribute(attrId, outValue, true)) return null;

        Object result = switch (outValue.type) {
            case TypedValue.TYPE_DIMENSION -> outValue.getDimension(mDisplayMetrics);
            case TypedValue.TYPE_FLOAT -> outValue.getFloat();
            case TypedValue.TYPE_STRING ->
                    outValue.string != null ? parseValue(outValue.string.toString()) : null;
            default ->
                    (outValue.type >= TypedValue.TYPE_FIRST_INT && outValue.type <= TypedValue.TYPE_LAST_INT)
                            ? outValue.data : (outValue.resourceId != 0 ? outValue.resourceId : outValue.data);
        };

        if (result != null) mThemeCache.put(ref, result);
        return result;
    }

    // ==================== 值解析（热路径 — 重点优化） ====================

    @Nullable
    private Object parseValue(String str) {
        if (str == null || str.isEmpty()) return 0;
        if ("nil".equals(str)) return 0;

        // 快速路径：查缓存（仅缓存非字符串结果）
        var cached = mParseCache.get(str);
        if (cached != null) return cached;

        Object result = parseValueCore(str);

        // 仅缓存数值/常量结果，不缓存任意字符串（太泛，缓存命中率低）
        if (result != null && !(result instanceof String)) {
            mParseCache.put(str, result);
        }
        return result;
    }

    @Nullable
    private Object parseValueCore(String str) {
        // 1. 管道分隔的位掩码（如 "bold|italic"）
        if (str.indexOf('|') >= 0) {
            return parseBitmask(str);
        }

        // 2. 常量映射（最高命中率）
        var constant = VALUE_MAP.get(str);
        if (constant != null) return constant;

        int len = str.length();

        // 3. 颜色
        if (len > 0 && str.charAt(0) == '#') return parseColor(str);

        int w = mLuaCtx.getWidth(), h = mLuaCtx.getHeight();

        // 4. 百分比（如 "50%"、"50%w"、"50%h"）
        if (len > 1 && str.charAt(len - 1) == '%') {
            if (len >= 3 && str.charAt(len - 2) == '%') {
                // 不可能：两个连续 %，跳过
            } else {
                float pct = tryParseFloat(str, 0, len - 1, Float.NaN);
                if (!Float.isNaN(pct)) return (int) (pct * w / 100);
            }
        }
        if (len >= 3 && str.charAt(len - 2) == '%') {
            float v = tryParseFloat(str, 0, len - 2, Float.NaN);
            if (!Float.isNaN(v)) {
                char axis = str.charAt(len - 1);
                if (axis == 'w') return (int) (v * w / 100);
                if (axis == 'h') return (int) (v * h / 100);
            }
        }

        // 5. 单位后缀（如 "16dp"、"12sp"）
        if (len >= 3) {
            var unitStr = str.substring(len - 2);
            var unitType = UNIT_MAP.get(unitStr);
            if (unitType != null) {
                float v = tryParseFloat(str, 0, len - 2, Float.NaN);
                if (!Float.isNaN(v))
                    return (int) TypedValue.applyDimension(unitType, v, mDisplayMetrics);
            }
        }

        // 6. 主题属性（如 "?attr/colorPrimary"）
        var trimmed = str.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '?') {
            var themeValue = resolveThemeAttribute(trimmed);
            if (themeValue != null) return themeValue;
        }

        // 7. 纯数字（无异常解析，避免 NumberFormatException 开销）
        long longVal = tryParseLong(str, Long.MIN_VALUE);
        if (longVal != Long.MIN_VALUE) return longVal;

        double doubleVal = tryParseDouble(str, Double.NaN);
        if (!Double.isNaN(doubleVal)) return doubleVal;

        // 8. 无法解析，返回原始字符串
        return str;
    }

    /**
     * 解析管道分隔的位掩码值
     */
    private int parseBitmask(String str) {
        int result = 0;
        var parts = PIPE_PATTERN.split(str);
        for (var part : parts) {
            var val = VALUE_MAP.get(part);
            if (val != null) result |= val;
        }
        return result;
    }

    public static int parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty() || colorStr.charAt(0) != '#') return 0;
        long color = parseHexLong(colorStr, 1, colorStr.length());
        if (colorStr.length() <= 7) color |= 0xFF000000L;
        return (int) color;
    }

    /**
     * 将 LuaValue 转换为像素值，用于 margin/padding 等成对属性
     */
    private int toPixelValue(LuaValue value) {
        if (value.isnumber()) return value.toint();
        Object parsed = parseValue(value.tojstring());
        if (parsed instanceof Number num) return num.intValue();
        return 0;
    }

    // ==================== 无异常数字解析（避免 NumberFormatException 堆栈开销） ====================

    private static int tryParseInt(String s, int defaultValue) {
        if (s.isEmpty()) return defaultValue;
        int start = 0;
        boolean negative = false;
        if (s.charAt(0) == '-') {
            negative = true;
            start = 1;
            if (s.length() == 1) return defaultValue;
        }
        int result = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return defaultValue;
            result = result * 10 + (c - '0');
        }
        return negative ? -result : result;
    }

    private static long tryParseLong(String s, long defaultValue) {
        if (s.isEmpty()) return defaultValue;
        int start = 0;
        boolean negative = false;
        if (s.charAt(0) == '-') {
            negative = true;
            start = 1;
            if (s.length() == 1) return defaultValue;
        }
        long result = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return defaultValue;
            result = result * 10 + (c - '0');
        }
        return negative ? -result : result;
    }

    private static float tryParseFloat(String s, int start, int end, float defaultValue) {
        if (start >= end) return defaultValue;
        boolean negative = false;
        if (s.charAt(start) == '-') {
            negative = true;
            start++;
            if (start >= end) return defaultValue;
        }
        long integerPart = 0;
        int i = start;
        boolean hasDigits = false;
        while (i < end) {
            char c = s.charAt(i);
            if (c == '.' || c == 'e' || c == 'E') break;
            if (c < '0' || c > '9') return defaultValue;
            integerPart = integerPart * 10 + (c - '0');
            hasDigits = true;
            i++;
        }
        if (!hasDigits) return defaultValue;

        float fraction = 0;
        if (i < end && s.charAt(i) == '.') {
            i++;
            float divisor = 10;
            while (i < end) {
                char c = s.charAt(i);
                if (c == 'e' || c == 'E') break;
                if (c < '0' || c > '9') return defaultValue;
                fraction += (c - '0') / divisor;
                divisor *= 10;
                i++;
            }
        }

        float result = integerPart + fraction;

        // 科学计数法
        if (i < end && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            return defaultValue; // 简化：回退到 Double.parseDouble
        }

        return negative ? -result : result;
    }

    private static double tryParseDouble(String s, double defaultValue) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseHexLong(String s, int start, int end) {
        long result = 0;
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            int digit;
            if (c >= '0' && c <= '9') digit = c - '0';
            else if (c >= 'a' && c <= 'f') digit = c - 'a' + 10;
            else if (c >= 'A' && c <= 'F') digit = c - 'A' + 10;
            else break;
            result = result * 16 + digit;
        }
        return result;
    }

    // ==================== 样式 & View 创建 ====================

    private StyleConfig parseViewStyle(LuaValue layout) {
        var theme = resolveResource(layout.get("theme"), "theme");
        var styleAttr = resolveResource(layout.get("styleAttr"), "styleAttr");
        var styleRes = resolveResource(layout.get("styleRes"), "styleRes");
        var legacy = resolveResource(layout.get("style"), "style");

        return new StyleConfig(
                theme.id(),
                styleAttr.valid() ? styleAttr.id() : (legacy.kind() == ResourceRef.Kind.ATTR ? legacy.id() : 0),
                styleRes.valid() ? styleRes.id() : 0,
                legacy.kind() == ResourceRef.Kind.STYLE ? legacy.id() : 0
        );
    }

    private LuaValue createViewWithStyle(LuaValue viewClass, LuaValue layout) {
        var style = parseViewStyle(layout);
        if (!style.hasAny()) return viewClass.call(mLuaContext);

        var themedContext = createThemedContext(style);
        var themedLuaContext = CoerceJavaToLua.coerce(themedContext);
        var nil = LuaConstants.NIL;

        // 检查构造器命中缓存，优先使用上次成功的构造器
        if (viewClass.isuserdata(Class.class)) {
            var clazz = (Class<?>) viewClass.touserdata(Class.class);
            var hit = CTOR_HITS.get(clazz);
            if (hit != null) {
                try {
                    return switch (hit) {
                        case CTOR_4ARG ->
                                instantiateView(viewClass, themedContext, null, style.attr(), style.res());
                        case CTOR_3ARG ->
                                viewClass.call(themedLuaContext, nil, CoerceJavaToLua.coerce(style.attr() != 0 ? style.attr() : style.res()));
                        case CTOR_1ARG -> viewClass.call(themedLuaContext);
                        default -> viewClass.call(themedLuaContext);
                    };
                } catch (Exception ignored) {
                    CTOR_HITS.remove(clazz); // 缓存失效，走全路径
                }
            }
        }

        var attempts = new ArrayList<String>();
        var failures = new ArrayList<String>();

        if (style.attr() != 0 && style.res() != 0 && viewClass.isuserdata(Class.class)) {
            attempts.add("(Context, AttributeSet?, defStyleAttr, defStyleRes)");
            try {
                var result = instantiateView(viewClass, themedContext, null, style.attr(), style.res());
                CTOR_HITS.put((Class<?>) viewClass.touserdata(Class.class), CTOR_4ARG);
                return result;
            } catch (Exception e) {
                failures.add("(Context, AttributeSet?, defStyleAttr, defStyleRes) -> " + e.getMessage());
            }
        }

        if (style.attr() != 0) {
            attempts.add("(Context, AttributeSet?, defStyleAttr)");
            try {
                var result = viewClass.call(themedLuaContext, nil, CoerceJavaToLua.coerce(style.attr()));
                if (viewClass.isuserdata(Class.class))
                    CTOR_HITS.put((Class<?>) viewClass.touserdata(Class.class), CTOR_3ARG);
                return result;
            } catch (Exception e) {
                failures.add("(Context, AttributeSet?, defStyleAttr) -> " + e.getMessage());
            }
        }

        if (style.res() != 0) {
            attempts.add("(Context, AttributeSet?, styleRes)");
            try {
                var result = viewClass.call(themedLuaContext, nil, CoerceJavaToLua.coerce(style.res()));
                if (viewClass.isuserdata(Class.class))
                    CTOR_HITS.put((Class<?>) viewClass.touserdata(Class.class), CTOR_3ARG);
                return result;
            } catch (Exception e) {
                failures.add("(Context, AttributeSet?, styleRes) -> " + e.getMessage());
            }
        }

        if (style.legacy() != 0) {
            attempts.add("legacy (ContextThemeWrapper, AttributeSet?, style)");
            try {
                return viewClass.call(themedLuaContext, nil, CoerceJavaToLua.coerce(style.legacy()));
            } catch (Exception e) {
                failures.add("legacy -> " + e.getMessage());
            }
        }

        attempts.add("(Context)");
        try {
            var result = viewClass.call(themedLuaContext);
            if (viewClass.isuserdata(Class.class))
                CTOR_HITS.put((Class<?>) viewClass.touserdata(Class.class), CTOR_1ARG);
            return result;
        } catch (Exception e) {
            failures.add("(Context) -> " + e.getMessage());
        }

        var viewId = layout.get("id").isstring() ? "[" + layout.get("id").tojstring() + "] " : "";
        throw new LuaError(
                "loadlayout create View failed " + viewId + viewClass + "\n" +
                        "theme=" + layout.get("theme") + ", style=" + layout.get("style") +
                        ", styleAttr=" + layout.get("styleAttr") + ", styleRes=" + layout.get("styleRes") + "\n" +
                        "Tried: " + String.join(", ", attempts) + "\n" +
                        "Failures: " + String.join("; ", failures)
        );
    }

    private Context createThemedContext(StyleConfig style) {
        if (style.theme() != 0) return new ContextThemeWrapper(mContext, style.theme());
        if (style.legacy() != 0 && style.attr() == 0)
            return new ContextThemeWrapper(mContext, style.legacy());
        if (style.res() != 0 && style.attr() == 0 && style.legacy() == 0)
            return new ContextThemeWrapper(mContext, style.res());
        return mContext;
    }

    private LuaValue instantiateView(LuaValue viewClass, Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) throws Exception {
        var clazz = (Class<?>) viewClass.touserdata(Class.class);
        Constructor<?> ctor = mConstructorCache.get(clazz);
        if (ctor == null) {
            try {
                ctor = clazz.getConstructor(Context.class, AttributeSet.class, Integer.TYPE, Integer.TYPE);
                mConstructorCache.put(clazz, ctor);
            } catch (NoSuchMethodException e) {
                mConstructorCache.put(clazz, null);
                return null;
            }
        }
        return CoerceJavaToLua.coerce(ctor.newInstance(context, attrs, defStyleAttr, defStyleRes));
    }

    // ==================== 布局加载（主入口） ====================

    public LuaValue load(LuaValue layout) {
        return load(layout, new LuaTable(), CoerceJavaToLua.coerce(ViewGroup.LayoutParams.class));
    }

    public LuaValue load(LuaValue layout, LuaTable env) {
        return load(layout, env, CoerceJavaToLua.coerce(ViewGroup.LayoutParams.class));
    }

    public LuaValue load(LuaValue layout, LuaTable env, LuaValue params) {
        var viewClass = layout.get(1);
        if (viewClass.isnil()) {
            var idVal = layout.get("id");
            var idHint = idVal.isstring() ? " (id=\"" + idVal.tojstring() + "\")" : "";
            throw new LuaError("loadlayout error: First value Must be a Class" + idHint + "\nLayout: " + layout.checktable().dump());
        }

        JavaInstance view = (JavaInstance) createViewWithStyle(viewClass, layout);
        params = params.call(WRAP_CONTENT, WRAP_CONTENT);

        boolean isAdapterView = viewClass.isuserdata() && AdapterView.class.isAssignableFrom((Class<?>) viewClass.touserdata(Class.class));

        // 预取 id 字符串（多处使用）
        var idVal = layout.get("id");
        String idStr = idVal.isstring() ? idVal.tojstring() : null;

        LuaValue key = LuaConstants.NIL;
        Varargs next;
        while (!(next = layout.next(key)).isnil(1)) {
            key = next.arg1();
            var value = next.arg(2);
            try {
                if (key.isint()) {
                    handleChildElement(key.toint(), value, view, env, viewClass, isAdapterView);
                } else if (key.isstring()) {
                    handleAttribute(key.tojstring(), value, view, env, params, idStr);
                }
            } catch (Exception e) {
                mLuaCtx.sendError("loadlayout " + (idStr != null ? "[" + idStr + "]" : "") + ": " + key + "=" + value, e);
            }
        }

        applyMargins(layout, params, idStr);
        view.set("LayoutParams", params);
        applyPadding(layout, view, idStr);

        return view;
    }

    // ==================== 子元素处理 ====================

    private void handleChildElement(int index, LuaValue value, JavaInstance view, LuaTable env, LuaValue viewClass, boolean isAdapterView) {
        if (index <= 1) return;
        if (value.isstring()) value = mLuaCtx.getLuaState().package_.require.call(value);
        if (isAdapterView) {
            view.jset("adapter", new LuaAdapter(mLuaCtx, value.checktable()));
        } else {
            var child = load(value, env, viewClass.get("LayoutParams"));
            view.getJavaMethod("addView").call(child);
        }
    }

    // ==================== 属性分发 ====================

    private void handleAttribute(String key, LuaValue value, JavaInstance view, LuaTable env, LuaValue params, String idStr) {
        switch (key) {
            case "style", "styleAttr", "styleRes", "theme", "padding" -> {
            }
            case "id" -> {
                String id = value.tojstring();
                view.set("id", obtainViewId(id));
                mViewMap.put(id, view);
                env.set(value, view);
            }
            case "text" -> view.set("text", value.tostring());
            case "hint" -> view.set("hint", value.tostring());
            case "textSize" -> setTextSize(view, value);
            case "textStyle" -> setTextStyle(view, value);
            case "scaleType" -> setScaleType(view, value);
            case "ellipsize" -> setEllipsize(view, value);
            case "items" -> setItems(view, value);
            case "minWidth" -> view.jset("MinimumWidth", value.tojstring());
            case "minHeight" -> view.jset("MinimumHeight", value.tojstring());
            case "pages" -> setPages(view, value.checktable(), env);
            case "pagesWithTitle" -> setPagesWithTitle(view, value.checktable(), env);
            case "src" -> handleSrc(view, value);
            case "background" -> handleBackground(view, value);
            default -> handleDefault(key, value, view, env, params);
        }
    }

    // ==================== 属性设置方法 ====================

    private void setTextSize(JavaInstance view, LuaValue value) {
        if (value.isnumber()) {
            view.getJavaMethod("setTextSize").call(value.tonumber());
        } else {
            var parsed = parseValue(value.tojstring());
            if (parsed instanceof Number num) {
                view.getJavaMethod("setTextSize").jcall(TypedValue.COMPLEX_UNIT_PX, num.floatValue());
            }
        }
    }

    private void setTextStyle(JavaInstance view, LuaValue value) {
        int style = switch (value.tojstring()) {
            case "bold" -> Typeface.BOLD;
            case "italic" -> Typeface.ITALIC;
            case "bold|italic", "italic|bold" -> Typeface.BOLD_ITALIC;
            default -> Typeface.NORMAL;
        };
        view.getJavaMethod("setTypeface").jcall(Typeface.defaultFromStyle(style));
    }

    private void setScaleType(JavaInstance view, LuaValue value) {
        var index = SCALE_TYPE_MAP.get(value.tojstring());
        if (index != null) view.getJavaMethod("setScaleType").jcall(SCALE_TYPES[index]);
    }

    private void setEllipsize(JavaInstance view, LuaValue value) {
        try {
            view.getJavaMethod("setEllipsize").jcall(TextUtils.TruncateAt.valueOf(value.tojstring().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            mLuaCtx.sendMsg("loadlayout: unsupported ellipsize value: " + value.tojstring());
        }
    }

    private void setItems(JavaInstance view, LuaValue value) {
        var adapter = view.get("adapter");
        if (!adapter.isnil()) {
            adapter.get("addAll").call(value);
        } else {
            var items = (String[]) CoerceLuaToJava.arrayCoerce(value, String.class);
            view.getJavaMethod("setAdapter").jcall(
                    new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1, items)
            );
        }
    }

    private void setPages(JavaInstance view, LuaTable viewsTable, LuaTable env) {
        var views = processLuaPages(viewsTable, env);
        var pagerAdapter = new LuaPagerAdapter();
        for (View v : views) pagerAdapter.add(v);
        view.getJavaMethod("setAdapter").jcall(pagerAdapter);
    }

    private void setPagesWithTitle(JavaInstance view, LuaTable table, LuaTable env) {
        var views = processLuaPages(table.get(1).checktable(), env);
        var titleTable = table.get(2).checktable();
        var pagerAdapter = new LuaPagerAdapter();
        for (int i = 0; i < views.size(); i++) {
            pagerAdapter.add(views.get(i), titleTable.get(i + 1).tojstring());
        }
        view.getJavaMethod("setAdapter").jcall(pagerAdapter);
    }

    private List<View> processLuaPages(LuaTable viewsTable, LuaTable env) {
        var views = new ArrayList<View>();
        for (int i = 1; i <= viewsTable.length(); i++) {
            var v = viewsTable.get(i);
            View view;
            if (v.isuserdata()) {
                view = v.touserdata(View.class);
            } else if (v.istable()) {
                view = load(v.checktable(), env).touserdata(View.class);
            } else if (v.isstring()) {
                view = load(mLuaCtx.getLuaState().package_.require.call(v), env).touserdata(View.class);
            } else {
                throw new LuaError("Unsupported type for Lua pages: " + v.typename());
            }
            views.add(view);
        }
        return views;
    }

    private void handleSrc(JavaInstance view, LuaValue value) {
        try {
            if (value.isuserdata(Drawable.class)) {
                view.jset("ImageDrawable", value.touserdata(Drawable.class));
            } else if (value.isuserdata()) {
                // 其他 userdata 类型（如 Bitmap）也直接设置
                view.jset("ImageBitmap", value.touserdata());
            } else {
                String src = value.tojstring();
                Glide.with(mContext).load(src).into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        view.jset("ImageDrawable", resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
            }
        } catch (Exception e) {
            LuaConfig.logError("LuaLayout", e);
        }
    }

    private void handleBackground(JavaInstance view, LuaValue value) {
        if (value.isuserdata()) {
            view.jset("background", value.touserdata(Drawable.class));
        } else if (value.isnumber()) {
            view.jset("backgroundColor", value.toint());
        } else if (value.isstring()) {
            var s = value.tojstring();
            if (s.charAt(0) == '#') view.jset("backgroundColor", parseColor(s));
            else view.jset("background", new LuaBitmapDrawable(mLuaCtx, s));
        }
    }

    // ==================== Behavior（缓存实例） ====================

    @Nullable
    private CoordinatorLayout.Behavior<?> createBehaviorFromString(String behaviorString) {
        if (behaviorString == null || behaviorString.isEmpty()) return null;
        return BEHAVIOR_MAP.get(behaviorString);
    }

    // ==================== 默认属性处理 ====================

    private void handleDefault(String key, LuaValue value, JavaInstance view, LuaTable env, LuaValue params) {
        if (key.length() >= 2 && key.charAt(0) == 'o' && key.charAt(1) == 'n') {
            if (value.isstring()) {
                var finalVal = value;
                value = new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        return env.get(finalVal).invoke(args);
                    }
                };
            }
            view.set(key, value);
            return;
        }

        if (key.startsWith("layout")) {
            handleLayoutParam(key, value, params);
            return;
        }

        if (value.type() == LuaValue.TSTRING)
            value = CoerceJavaToLua.coerce(parseValue(value.tojstring()));
        view.set(key, value);
    }

    private void handleLayoutParam(String key, LuaValue value, LuaValue params) {
        Integer rule = RULE_MAP.get(key);
        if (rule != null) {
            if ((value.isboolean() && value.toboolean()) || "true".equals(value.tojstring())) {
                params.get("addRule").jcall(rule);
            } else {
                var targetId = ID_MAP.get(value.tojstring());
                if (targetId != null) params.get("addRule").jcall(rule, targetId);
                else
                    mLuaCtx.sendMsg("loadlayout: " + key + " references undefined id '" + value.tojstring() + "'");
            }
            return;
        }

        switch (key) {
            case "layout_behavior" -> {
                var behavior = createBehaviorFromString(value.tojstring());
                params.get("setBehavior").jcall(Objects.requireNonNullElse(behavior, value));
            }
            case "layout_anchor" -> {
                var anchorId = ID_MAP.get(value.tojstring());
                if (anchorId != null) params.get("setAnchorId").jcall(anchorId);
                else
                    mLuaCtx.sendMsg("loadlayout: layout_anchor references undefined id '" + value.tojstring() + "'");
            }
            case "layout_collapseParallaxMultiplier" ->
                    params.get("setParallaxMultiplier").jcall(coerceNumeric(value));
            case "layout_marginEnd" -> params.get("setMarginEnd").jcall(coerceNumeric(value));
            case "layout_marginStart" -> params.get("setMarginStart").jcall(coerceNumeric(value));
            case "layout_collapseMode" -> params.get("setCollapseMode").jcall(coerceNumeric(value));
            case "layout_scrollFlags" -> params.get("setScrollFlags").jcall(coerceNumeric(value));
            default -> params.set(key.substring(7), coerceNumeric(value));
        }
    }

    /**
     * 将 LuaValue 转换为数值 LuaValue，字符串会经过 parseValue 解析
     */
    private LuaValue coerceNumeric(LuaValue value) {
        return value.isnumber() ? value : CoerceJavaToLua.coerce(parseValue(value.tojstring()));
    }

    // ==================== Margin / Padding（成对处理） ====================

    private void applyMargins(LuaValue layout, LuaValue params, String idStr) {
        try {
            boolean hasMargin = false;
            int l = 0, t = 0, r = 0, b = 0;
            for (int i = 0; i < MARGIN_KEYS.length; i++) {
                var margin = layout.get(MARGIN_KEYS[i]);
                if (margin.isnil()) margin = layout.get("layout_margin");
                if (!margin.isnil()) {
                    hasMargin = true;
                    int px = toPixelValue(margin);
                    switch (i) {
                        case 0 -> l = px;
                        case 1 -> t = px;
                        case 2 -> r = px;
                        case 3 -> b = px;
                    }
                }
            }
            if (hasMargin) {
                params.get("setMargins").jcall(l, t, r, b);
            }
        } catch (Exception e) {
            mLuaCtx.sendError("loadlayout margin error " + (idStr != null ? "[" + idStr + "]" : ""), e);
        }
    }

    private void applyPadding(LuaValue layout, JavaInstance view, String idStr) {
        try {
            boolean hasPadding = false;
            int l = 0, t = 0, r = 0, b = 0;
            for (int i = 0; i < PADDING_KEYS.length; i++) {
                var padding = layout.get(PADDING_KEYS[i]);
                if (padding.isnil()) padding = layout.get("padding");
                if (!padding.isnil()) {
                    hasPadding = true;
                    int px = toPixelValue(padding);
                    switch (i) {
                        case 0 -> l = px;
                        case 1 -> t = px;
                        case 2 -> r = px;
                        case 3 -> b = px;
                    }
                }
            }
            if (hasPadding) {
                view.getJavaMethod("setPadding").jcall(l, t, r, b);
            }
        } catch (Exception e) {
            mLuaCtx.sendError("loadlayout padding error " + (idStr != null ? "[" + idStr + "]" : ""), e);
        }
    }
}
