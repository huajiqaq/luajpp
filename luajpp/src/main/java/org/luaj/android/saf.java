package org.luaj.android;

import android.content.Intent;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.androlua.activity.LuaActivity;
import com.androlua.internal.LuaConfig;
import com.androlua.util.LuaUtil;

import org.luaj.LuaConstants;
import org.luaj.LuaFunction;
import org.luaj.LuaString;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

@SuppressWarnings("unused")
public class saf {

    private final LuaActivity mActivity;
    private DocumentFile mDocumentFile;

    public saf(LuaActivity activity) {
        mActivity = activity;
        String d = (String) mActivity.getSharedData("_DOCUMENT_TREE", null);
        if (d != null) {
            try {
                mDocumentFile = DocumentFile.fromTreeUri(mActivity, Uri.parse(d));
            } catch (Exception e) {
                LuaConfig.logError("saf", e);
            }
        }
    }

    public DocumentFile get() {
        return mDocumentFile;
    }

    public void list(LuaFunction function) {
        if (mDocumentFile == null) {
            select(new LuaFunction() {
                @Override
                public LuaValue call(LuaValue arg1, LuaValue arg2) {
                    list(function);
                    return LuaConstants.NONE;
                }
            });
            return;
        }
        function.call(CoerceJavaToLua.coerce(mDocumentFile.listFiles()));
    }

    public void select(LuaFunction function) {
        mActivity.openDocumentTree(new LuaFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                Intent intent = (Intent) arg.touserdata();
                if (intent == null || intent.getData() == null) {
                    function.call(LuaConstants.NIL);
                    return LuaConstants.NONE;
                }
                try {
                    mDocumentFile = DocumentFile.fromTreeUri(mActivity, intent.getData());
                    mActivity.setSharedData("_DOCUMENT_TREE", intent.getData().toString());
                    function.call(CoerceJavaToLua.coerce(intent), CoerceJavaToLua.coerce(mDocumentFile));
                } catch (Exception e) {
                    LuaConfig.logError("saf", e);
                    function.call(LuaConstants.NIL);
                }
                return LuaConstants.NONE;
            }
        });
    }

    public void read(LuaFunction function) {
        mActivity.getDocument("*/*", new LuaFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                Intent intent = (Intent) arg.touserdata();
                if (intent == null || intent.getData() == null) {
                    function.call(LuaConstants.NIL);
                    return LuaConstants.NONE;
                }
                try (InputStream in = mActivity.getContentResolver().openInputStream(intent.getData())) {
                    byte[] bs = LuaUtil.readAll(Objects.requireNonNull(in));
                    function.call(CoerceJavaToLua.coerce(intent), LuaString.valueOf(bs));
                } catch (Exception e) {
                    LuaConfig.logError("saf", e);
                    function.call(LuaConstants.NIL);
                }
                return LuaConstants.NONE;
            }
        });
    }

    public void save(String name, LuaString bs, LuaFunction function) {
        mActivity.createDocument("*/*", name, new LuaFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                Intent intent = (Intent) arg.touserdata();
                if (intent == null || intent.getData() == null) {
                    function.call(LuaConstants.NIL);
                    return LuaConstants.NONE;
                }
                try (OutputStream out = mActivity.getContentResolver().openOutputStream(intent.getData())) {
                    LuaUtil.save(out, bs);
                    function.call(CoerceJavaToLua.coerce(intent));
                } catch (Exception e) {
                    LuaConfig.logError("saf", e);
                    function.call(CoerceJavaToLua.coerce(e.toString()));
                }
                return LuaConstants.NONE;
            }
        });
    }

    public LuaValue read(String name) {
        if (mDocumentFile == null) return LuaConstants.NIL;
        DocumentFile f = mDocumentFile.findFile(name);
        if (f == null) return LuaConstants.NIL;
        try (InputStream in = mActivity.getContentResolver().openInputStream(f.getUri())) {
            if (in != null) return LuaString.valueOf(LuaUtil.readAll(in));
        } catch (Exception e) {
            LuaConfig.logError("saf", e);
        }
        return LuaConstants.NIL;
    }

    public LuaValue save(String name, LuaString bs) {
        if (mDocumentFile == null) {
            select(new LuaFunction() {
                @Override
                public LuaValue call(LuaValue arg1, LuaValue arg2) {
                    save(name, bs);
                    return LuaConstants.NONE;
                }
            });
            return LuaConstants.FALSE;
        }
        DocumentFile f = mDocumentFile.createFile("", name);
        if (f == null) return LuaConstants.FALSE;
        try (OutputStream out = mActivity.getContentResolver().openOutputStream(f.getUri())) {
            if (out != null) {
                LuaUtil.save(out, bs);
                return LuaConstants.TRUE;
            }
        } catch (Exception e) {
            LuaConfig.logError("saf", e);
        }
        return LuaConstants.FALSE;
    }
}