package com.androlua.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.myopicmobile.textwarrior.android.FreeScrollingTextField;
import com.myopicmobile.textwarrior.android.TouchNavigationMethod;
import com.myopicmobile.textwarrior.android.TrackpadNavigationMethod;
import com.myopicmobile.textwarrior.android.YoyoNavigationMethod;
import com.myopicmobile.textwarrior.common.ColorScheme;
import com.myopicmobile.textwarrior.common.ColorSchemeDark;
import com.myopicmobile.textwarrior.common.ColorSchemeLight;
import com.myopicmobile.textwarrior.common.Document;
import com.myopicmobile.textwarrior.common.DocumentProvider;
import com.myopicmobile.textwarrior.common.LanguageLua;
import com.myopicmobile.textwarrior.common.Lexer;
import com.myopicmobile.textwarrior.common.LinearSearchStrategy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Lua 代码编辑器
 */
@SuppressWarnings("unused")
public class LuaEditor extends FreeScrollingTextField {

    private static final float BASE_TEXT_SIZE_PIXELS = 14f;

    private final TouchNavigationMethod mDefNavigationMethod;
    private final YoyoNavigationMethod mSelNavigationMethod;
    private final Context mContext;

    private boolean mIsWordWrap;
    private String mLastSelectedFile;
    private int mPendingCaretPosition;
    private LinearSearchStrategy mFinder;
    private int mFindPosition;
    private String mKeyword;

    @SuppressLint("StaticFieldLeak")
    public LuaEditor(Context context) {
        super(context);
        mContext = context;
        initEditor();
        mDefNavigationMethod = initNavigationMethod();
        mSelNavigationMethod = new YoyoNavigationMethod(this);
        initColors();
    }

    private void initEditor() {
        setTypeface(Typeface.MONOSPACE);
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, BASE_TEXT_SIZE_PIXELS, dm);
        setTextSize((int) textSize);
        setShowLineNumbers(true);
        setHighlightCurrentRow(true);
        setWordWrap(false);
        setAutoIndentWidth(2);
        Lexer.setLanguage(LanguageLua.getInstance());
    }

    private TouchNavigationMethod initNavigationMethod() {
        TouchNavigationMethod method;
        if (isAccessibilityEnabled()) {
            method = new TrackpadNavigationMethod(this);
        } else {
            method = new YoyoNavigationMethod(this);
        }
        setNavigationMethod(method);
        return method;
    }

    private void initColors() {
        try (TypedArray array = mContext.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorHighlight,
        })) {
            int textColor = array.getColor(1, 0xFF00FF);
            int textHighlightColor = array.getColor(2, 0xFF00FF);
            setTextColor(textColor);
            setTextHighlightColor(textHighlightColor);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mPendingCaretPosition != 0 && right > 0) {
            moveCaret(mPendingCaretPosition);
            mPendingCaretPosition = 0;
        }
    }

    // ==================== 颜色设置 ====================

    public void setDark(boolean isDark) {
        if (isDark) {
            setColorScheme(new ColorSchemeDark());
        } else {
            setColorScheme(new ColorSchemeLight());
        }
        initColors();
    }

    public void setKeywordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.KEYWORD, color);
    }

    public void setUserwordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.LITERAL, color);
    }

    public void setBasewordColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.NAME, color);
    }

    public void setStringColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.STRING, color);
    }

    public void setCommentColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.COMMENT, color);
    }

    @Override
    public void setBackgroundColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.BACKGROUND, color);
        super.setBackgroundColor(color);
    }

    public void setTextColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.FOREGROUND, color);
    }

    public void setGlobalColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.GLOBAL, color);
    }

    public void setLocalColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.LOCAL, color);
    }

    public void setUpvalColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.UPVAL, color);
    }

    public void setTextHighlightColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.SELECTION_BACKGROUND, color);
    }

    public void setLineColor(int color) {
        getColorScheme().setColor(ColorScheme.Colorable.NON_PRINTING_GLYPH, color);
    }

    public void setPanelBackgroundColor(int color) {
        _autoCompletePanel.setBackgroundColor(color);
    }

    public void setPanelBackground(Drawable drawable) {
        _autoCompletePanel.setBackground(drawable);
    }

    public void setPanelTextColor(int color) {
        _autoCompletePanel.setTextColor(color);
    }

    // ==================== 代码补全 ====================

    public void addNames(String[] names) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        String[] oldNames = lang.getNames();
        String[] newNames = new String[oldNames.length + names.length];
        System.arraycopy(oldNames, 0, newNames, 0, oldNames.length);
        System.arraycopy(names, 0, newNames, oldNames.length, names.length);
        lang.setNames(newNames);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    public void addPackage(String pkg, String[] names) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        lang.addBasePackage(pkg, names);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    public void removePackage(String pkg) {
        LanguageLua lang = (LanguageLua) Lexer.getLanguage();
        lang.removeBasePackage(pkg);
        Lexer.setLanguage(lang);
        respan();
        invalidate();
    }

    // ==================== 文本操作 ====================

    public String getSelectedText() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        return _hDoc.subSequence(start, end - start).toString();
    }

    public DocumentProvider getText() {
        return createDocumentProvider();
    }

    public void setText(CharSequence text) {
        Document doc = new Document(this);
        doc.setWordWrap(mIsWordWrap);
        doc.setText(text);
        setDocumentProvider(new DocumentProvider(doc));
    }

    public void setText(CharSequence text, boolean replace) {
        replaceText(0, getLength() - 1, text.toString());
    }

    public void insert(int position, String text) {
        selectText(false);
        moveCaret(position);
        paste(text);
    }

    public void setSelection(int position) {
        selectText(false);
        if (!hasLayout()) {
            moveCaret(position);
        } else {
            mPendingCaretPosition = position;
        }
    }

    public void gotoLine(int line) {
        int totalLines = _hDoc.getRowCount();
        if (line > totalLines) line = totalLines;
        if (line < 1) line = 1;
        int position = getText().getLineOffset(line - 1);
        setSelection(position);
    }

    @Override
    public void setWordWrap(boolean enable) {
        mIsWordWrap = enable;
        super.setWordWrap(enable);
    }

    public void undo() {
        DocumentProvider doc = createDocumentProvider();
        int newPosition = doc.undo();
        if (newPosition >= 0) {
            setEdited(true);
            respan();
            selectText(false);
            moveCaret(newPosition);
            invalidate();
        }
    }

    public void redo() {
        DocumentProvider doc = createDocumentProvider();
        int newPosition = doc.redo();
        if (newPosition >= 0) {
            setEdited(true);
            respan();
            selectText(false);
            moveCaret(newPosition);
            invalidate();
        }
    }

    // ==================== 文件操作 ====================

    public String getFilePath() {
        return mLastSelectedFile;
    }

    public void open(String filename) throws IOException {
        mLastSelectedFile = filename;
        File file = new File(filename);

        if (!file.exists()) {
            file.createNewFile();
            setText("");
            return;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        if (content.length() > 1) {
            content.setLength(content.length() - 1);
        }
        setText(content);
    }

    public boolean save() throws IOException {
        return save(mLastSelectedFile);
    }

    public boolean save(String filename) throws IOException {
        if (filename == null) return true;

        File file = new File(filename);
        if (file.exists() && !file.canWrite()) return false;

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(getText().toString());
        }
        return true;
    }

    // ==================== 搜索功能 ====================

    public void startFindMode() {
        startActionMode(new FindActionModeCallback());
    }

    public void startGotoMode() {
        startActionMode(new GotoActionModeCallback());
    }

    public void search() {
        startFindMode();
    }

    public void gotoLine() {
        startGotoMode();
    }

    public boolean findNext() {
        return findNext(mKeyword);
    }

    public boolean findNext(String keyword) {
        if (!keyword.equals(mKeyword)) {
            mKeyword = keyword;
            mFindPosition = 0;
        }

        if (mKeyword.isEmpty()) {
            selectText(false);
            return false;
        }

        if (mFinder == null) {
            mFinder = new LinearSearchStrategy();
        }

        int docLength = getLength();
        mFindPosition = mFinder.find(getText(), mKeyword, mFindPosition, docLength, false, false);

        if (mFindPosition == -1) {
            selectText(false);
            Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            mFindPosition = 0;
            return false;
        }

        setSelection(mFindPosition, mKeyword.length());
        mFindPosition += mKeyword.length();
        moveCaret(mFindPosition);
        return true;
    }

    public boolean findBack() {
        return findBack(mKeyword);
    }

    public boolean findBack(String keyword) {
        if (!keyword.equals(mKeyword)) {
            mKeyword = keyword;
            mFindPosition = getLength();
        }

        if (mKeyword == null || mKeyword.isEmpty()) {
            selectText(false);
            return false;
        }

        if (mFinder == null) {
            mFinder = new LinearSearchStrategy();
        }

        mFindPosition = mFinder.findBackwards(getText(), mKeyword, mFindPosition - mKeyword.length() - 1, 0, false, false);

        if (mFindPosition == -1) {
            selectText(false);
            Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
            mFindPosition = getLength();
            return false;
        }

        setSelection(mFindPosition, mKeyword.length());
        mFindPosition += mKeyword.length();
        moveCaret(mFindPosition);
        return true;
    }

    // ==================== 键盘快捷键 ====================

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        int filteredMetaState = event.getMetaState() & ~KeyEvent.META_CTRL_MASK;
        if (KeyEvent.metaStateHasNoModifiers(filteredMetaState)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    selectAll();
                    return true;
                case KeyEvent.KEYCODE_X:
                    cut();
                    return true;
                case KeyEvent.KEYCODE_C:
                    copy();
                    return true;
                case KeyEvent.KEYCODE_V:
                    paste();
                    return true;
                case KeyEvent.KEYCODE_L:
                    format();
                    return true;
                case KeyEvent.KEYCODE_S:
                    startFindMode();
                    return true;
                case KeyEvent.KEYCODE_G:
                    startGotoMode();
                    return true;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    // ==================== 无障碍支持 ====================

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
            case AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY: {
                int granularity = arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
                boolean isNext = action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
                if (granularity == AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE) {
                    if (isNext) moveCaretDown();
                    else moveCaretUp();
                } else if (granularity == AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER) {
                    if (isNext) moveCaretRight();
                    else moveCaretLeft();
                }
                return true;
            }

            case AccessibilityNodeInfo.ACTION_SET_SELECTION: {
                int start = arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                int end = arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 0);
                boolean extend = arguments.getBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false);
                if (extend) {
                    setSelectionRange(start, end - start);
                } else {
                    setSelection(start, end);
                }
                return true;
            }

            case AccessibilityNodeInfo.ACTION_SET_TEXT: {
                selectText(false);
                CharSequence text = arguments != null
                        ? arguments.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE) : null;
                setText(text != null ? text : "", true);
                return true;
            }

            case AccessibilityNodeInfo.ACTION_PASTE:
                paste();
                return true;
            case AccessibilityNodeInfo.ACTION_COPY:
                copy();
                return true;
            case AccessibilityNodeInfo.ACTION_CUT:
                cut();
                return true;
            default:
                return super.performAccessibilityAction(action, arguments);
        }
    }

    public YoyoNavigationMethod getmSelNavigationMethod() {
        return mSelNavigationMethod;
    }

    public TouchNavigationMethod getmDefNavigationMethod() {
        return mDefNavigationMethod;
    }

    // ==================== 内部类 ====================

    private class FindActionModeCallback implements ActionMode.Callback {
        private EditText mEditText;
        private int mFindPosition;

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.setTitle("搜索");
            mEditText = new EditText(mContext);
            mEditText.setSingleLine(true);
            mEditText.setImeOptions(3);
            mEditText.setOnEditorActionListener((tv, actionId, event) -> {
                findNext();
                return true;
            });
            mEditText.setLayoutParams(new LinearLayout.LayoutParams(getWidth() / 3, LinearLayout.LayoutParams.WRAP_CONTENT));

            menu.add(0, 1, 0, "").setActionView(mEditText);
            menu.add(0, 2, 0, mContext.getString(android.R.string.search_go));
            mEditText.requestFocus();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == 2) {
                findNext();
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }

        private void findNext() {
            String keyword = mEditText.getText().toString();
            if (keyword.isEmpty()) {
                selectText(false);
                return;
            }

            if (mFinder == null) mFinder = new LinearSearchStrategy();
            int length = getLength();
            mFindPosition = mFinder.find(getText(), keyword, mFindPosition, length, false, false);

            if (mFindPosition == -1) {
                selectText(false);
                Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show();
                mFindPosition = 0;
                return;
            }

            setSelection(mFindPosition, keyword.length());
            mFindPosition += keyword.length();
            moveCaret(mFindPosition);
        }
    }

    private class GotoActionModeCallback implements ActionMode.Callback {
        private EditText mEditText;

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.setTitle("转到");
            mEditText = new EditText(mContext);
            mEditText.setSingleLine(true);
            mEditText.setInputType(2);
            mEditText.setImeOptions(2);
            mEditText.setOnEditorActionListener((tv, actionId, event) -> {
                gotoLineNumber();
                return true;
            });
            mEditText.setLayoutParams(new LinearLayout.LayoutParams(getWidth() / 3, LinearLayout.LayoutParams.WRAP_CONTENT));

            menu.add(0, 1, 0, "").setActionView(mEditText);
            menu.add(0, 2, 0, mContext.getString(android.R.string.ok));
            mEditText.requestFocus();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == 2) {
                gotoLineNumber();
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }

        private void gotoLineNumber() {
            String text = mEditText.getText().toString();
            if (text.isEmpty()) return;

            try {
                int line = Integer.parseInt(text);
                gotoLine(line);
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
