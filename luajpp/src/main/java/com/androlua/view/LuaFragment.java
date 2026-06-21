package com.androlua.view;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.androlua.internal.LuaLayout;

import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaValue;

@SuppressLint("ValidFragment")
public class LuaFragment extends Fragment {

    private LuaTable mLayout;
    private LuaTable mEnv;

    public LuaFragment(LuaTable layout) {
        mLayout = layout;
    }

    public void setLayout(LuaTable layout) {
        mLayout = layout;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            mEnv = new LuaTable();
            return new LuaLayout(requireActivity()).load(mLayout, mEnv).touserdata(View.class);
        } catch (LuaError e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public LuaValue getView(String id) {
        if (mEnv == null)
            return null;
        return mEnv.get(id);
    }
}
