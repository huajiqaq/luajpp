package com.androlua.view;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.androlua.internal.LuaLog;

import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;

/**
 * Lua 偏好设置 Fragment
 */
@SuppressLint("ValidFragment")
@SuppressWarnings("unused")
public class LuaPreferenceFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    private LuaTable mPreferences;
    private Preference.OnPreferenceChangeListener mOnPreferenceChangeListener;
    private Preference.OnPreferenceClickListener mOnPreferenceClickListener;

    public LuaPreferenceFragment(LuaTable preferences) {
        mPreferences = preferences;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(0, rootKey);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());

        try {
            initPreferences(screen, mPreferences);
        } catch (Exception e) {
            LuaLog.getInstance().addError("LuaPreferenceFragment", e);
        }

        setPreferenceScreen(screen);
    }

    public void setPreferences(LuaTable preferences) {
        mPreferences = preferences;
    }

    private void initPreferences(PreferenceScreen screen, LuaTable preferences) {
        int count = preferences.length();
        for (int i = 1; i <= count; i++) {
            LuaTable prefConfig = preferences.get(i).checktable();
            try {
                LuaValue prefClass = prefConfig.get(1);
                if (prefClass.isnil()) {
                    throw new IllegalArgumentException("First value must be a Preference class");
                }

                Preference preference = (Preference) prefClass.jcall(requireContext());
                preference.setOnPreferenceChangeListener(this);
                preference.setOnPreferenceClickListener(this);

                for (LuaValue key : prefConfig.keys()) {
                    if (key.isstring()) {
                        setPreferenceProperty(preference, key.tojstring(), prefConfig.get(key.tojstring()));
                    }
                }

                screen.addPreference(preference);
            } catch (Exception e) {
                LuaLog.getInstance().addError("LuaPreferenceFragment", e);
            }
        }
    }

    private void setPreferenceProperty(Preference preference, String key, Object value) {
        CoerceJavaToLua.coerce(preference).jset(key, value);
    }

    public void setOnPreferenceChangeListener(Preference.OnPreferenceChangeListener listener) {
        mOnPreferenceChangeListener = listener;
    }

    public void setOnPreferenceClickListener(Preference.OnPreferenceClickListener listener) {
        mOnPreferenceClickListener = listener;
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        return mOnPreferenceChangeListener == null || mOnPreferenceChangeListener.onPreferenceChange(preference, newValue);
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        return mOnPreferenceClickListener != null && mOnPreferenceClickListener.onPreferenceClick(preference);
    }
}