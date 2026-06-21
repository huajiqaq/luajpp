package com.androlua.adapter;

import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.BaseExpandableListAdapter;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaLayout;
import com.androlua.internal.LuaLog;

import org.luaj.Globals;
import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;
import org.luaj.lib.jse.CoerceLuaToJava;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class LuaExpandableListAdapter extends BaseExpandableListAdapter {

    private final LuaContext mContext;
    private final Globals mGlobals;

    private final LuaTable mGroupData;
    private final LuaTable mChildData;
    private final LuaTable mGroupLayout;
    private final LuaTable mChildLayout;
    private final LuaLayout mLayoutLoader;

    private final Map<View, Animation> mAnimCache = new HashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private LuaValue mAnimationUtil;
    private boolean mNotifyOnChange;
    private boolean mUpdating;

    public LuaExpandableListAdapter(LuaContext context, LuaTable groupLayout, LuaTable childLayout) throws LuaError {
        this(context, groupLayout, childLayout, null, null);
    }

    public LuaExpandableListAdapter(LuaContext context, LuaTable groupLayout, LuaTable childLayout,
                                    LuaTable groupData, LuaTable childData) throws LuaError {
        mContext = context;
        mGlobals = context.getLuaState();

        BitmapDrawable mDefaultDrawable = new BitmapDrawable(context.getContext().getResources(), getClass().getResourceAsStream("/res/drawable/icon.png"));
        mDefaultDrawable.setColorFilter(0x88ffffff, PorterDuff.Mode.SRC_ATOP);

        mGroupLayout = groupLayout;
        mChildLayout = childLayout;
        mGroupData = groupData != null ? groupData : new LuaTable(mGlobals);
        mChildData = childData != null ? childData : new LuaTable(mGlobals);

        var mLayoutParams = CoerceJavaToLua.coerce(AdapterView.LayoutParams.class);
        mLayoutLoader = new LuaLayout(context.getContext());
        mLayoutLoader.load(mGroupLayout, new LuaTable(), mLayoutParams);
        mLayoutLoader.load(mChildLayout, new LuaTable(), mLayoutParams);
    }

    public void setAnimationUtil(LuaValue animation) {
        mAnimCache.clear();
        mAnimationUtil = animation;
    }

    @Override
    public int getGroupCount() {
        return mGroupData.length();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        var group = mChildData.get(groupPosition + 1);
        return group.istable() ? group.length() : 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return CoerceLuaToJava.coerce(mGroupData.get(groupPosition + 1), Object.class);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        var group = mChildData.get(groupPosition + 1);
        return group.istable() ? CoerceLuaToJava.coerce(group.get(childPosition + 1), Object.class) : null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition + 1;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition + 1;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

    public LuaTable getGroupData() {
        return mGroupData;
    }

    public LuaTable getChildData() {
        return mChildData;
    }

    public GroupItem add(LuaTable groupItem) {
        return add(groupItem, new LuaTable(mGlobals));
    }

    public GroupItem add(LuaTable groupItem, LuaTable childItem) {
        mGroupData.set(mGroupData.length() + 1, groupItem);
        mChildData.set(mGroupData.length(), childItem);
        if (mNotifyOnChange) notifyDataSetChanged();
        return new GroupItem(childItem);
    }

    public GroupItem insert(int position, LuaTable groupItem, LuaTable childItem) {
        mGroupData.insert(position + 1, groupItem);
        mChildData.insert(position + 1, childItem);
        if (mNotifyOnChange) notifyDataSetChanged();
        return new GroupItem(childItem);
    }

    public void remove(int position) {
        mGroupData.remove(position + 1);
        mChildData.remove(position + 1);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        mGroupData.clear();
        mChildData.clear();
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        if (!mUpdating) {
            mUpdating = true;
            mHandler.postDelayed(() -> mUpdating = false, 500);
        }
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        return getView(groupPosition, -1, convertView, parent, true);
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        return getView(groupPosition, childPosition, convertView, parent, false);
    }

    private View getView(int groupPos, int childPos, View convertView, ViewGroup parent, boolean isGroup) {
        View view;
        LuaTable holder;
        var layout = isGroup ? mGroupLayout : mChildLayout;
        LuaTable data;

        if (isGroup) {
            data = mGroupData.get(groupPos + 1).checktable();
        } else {
            var group = mChildData.get(groupPos + 1);
            data = group.istable() ? group.get(childPos + 1).checktable() : new LuaTable(mGlobals);
        }

        if (convertView == null) {
            try {
                holder = new LuaTable(mGlobals);
                view = mLayoutLoader.load(layout, holder).touserdata(View.class);
                view.setTag(holder);
            } catch (LuaError e) {
                return new View(mContext.getContext());
            }
        } else {
            view = convertView;
            holder = (LuaTable) view.getTag();
        }

        for (LuaValue key : data.keys()) {
            try {
                var keyStr = key.tojstring();
                var value = data.jget(keyStr);
                var viewObj = holder.get(keyStr);
                if (viewObj.isuserdata())
                    AdapterHelper.setHelper(viewObj.touserdata(View.class), value);
            } catch (Exception e) {
                LuaConfig.logError("LuaExpandableListAdapter", e);
                LuaLog.getInstance().addError("setHelper", e);
            }
        }

        if (mUpdating) return view;

        if (mAnimationUtil != null && convertView != null) {
            Animation anim = mAnimCache.get(convertView);
            if (anim == null) {
                try {
                    anim = mAnimationUtil.call().touserdata(Animation.class);
                    mAnimCache.put(convertView, anim);
                } catch (Exception e) {
                    LuaConfig.logError("LuaExpandableListAdapter", e);
                    LuaLog.getInstance().addError("setAnimation error: ", e);
                }
            }
            if (anim != null) {
                view.clearAnimation();
                view.startAnimation(anim);
            }
        }
        return view;
    }

    public class GroupItem {
        private final LuaTable mData;

        public GroupItem(LuaTable data) {
            mData = data;
        }

        public LuaTable getData() {
            return mData;
        }

        public void add(LuaTable item) {
            mData.set(mData.length() + 1, item);
            if (mNotifyOnChange) notifyDataSetChanged();
        }

        public void insert(int position, LuaTable item) {
            mData.insert(position + 1, item);
            if (mNotifyOnChange) notifyDataSetChanged();
        }

        public void remove(int position) {
            mData.remove(position + 1);
            if (mNotifyOnChange) notifyDataSetChanged();
        }

        public void clear() {
            mData.clear();
            if (mNotifyOnChange) notifyDataSetChanged();
        }
    }
}
