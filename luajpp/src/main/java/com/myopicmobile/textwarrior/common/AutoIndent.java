package com.myopicmobile.textwarrior.common;

import static com.myopicmobile.textwarrior.common.LuaTokenTypes.DOT;
import static com.myopicmobile.textwarrior.common.LuaTokenTypes.NAME;
import static com.myopicmobile.textwarrior.common.LuaTokenTypes.WHITE_SPACE;

import android.graphics.Rect;
import android.text.TextUtils;
import android.util.SparseIntArray;

import com.androlua.internal.LuaLog;

import org.luaj.LuaString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class AutoIndent {
    public static int createAutoIndent(CharSequence text) {
        LuaLexer lexer = new LuaLexer(text);
        int idt = 0;
        try {
            while (true) {
                LuaTokenTypes type = lexer.advance();
                if (type == null) {
                    break;
                }
                if (lexer.yytext().equals("switch"))
                    idt += 1;
                else
                    idt += indent(type);
            }
        } catch (IOException e) {
            LuaLog.getInstance().addError("AutoIndent", e);
        }
        return idt;
    }


    private static int indent(LuaTokenTypes t) {
        return switch (t) {
            case FOR, WHILE, FUNCTION, IF, REPEAT, SWITCH, WHEN, TRY, LCURLY -> 1;
            case UNTIL, END, RCURLY -> -1;
            default -> 0;
        };
    }

    public static CharSequence format(CharSequence text, int width) {
        StringBuilder builder = new StringBuilder();
        boolean isNewLine = true;
        boolean isNewLine2 = true;
        LuaLexer lexer = new LuaLexer(text);
        ArrayList<Rect> lines = new ArrayList<>(LexState.lines);
        if (LexState.lines.isEmpty() || !TextUtils.isEmpty(LexState.errormsg)) {
            lines.clear();
            lines.addAll(Lexer.getLines());
        }
        SparseIntArray idts = new SparseIntArray();
        for (Rect rect : lines) {
            if (rect == null)
                continue;
            idts.put(rect.top, idts.get(rect.top) + 1);
            idts.put(rect.bottom, idts.get(rect.bottom) - 1);
        }
        int line;
        int lastLine = 0;
        try {
            int idt = 0;
            LuaTokenTypes last = WHITE_SPACE;
            while (true) {
                LuaTokenTypes type = lexer.advance();

                if (type == null)
                    break;
                line = lexer.yyline();
                if (lastLine != line)
                    isNewLine2 = true;

                lastLine = line;

                if (type == LuaTokenTypes.NEW_LINE) {
                    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ' ')
                        builder.deleteCharAt(builder.length() - 1);
                    isNewLine = true;
                    builder.append('\n');
                    idt = Math.max(0, idt);
                } else if (isNewLine || isNewLine2) {
                    switch (type) {
                        case WHITE_SPACE:
                            if (!isNewLine)
                                builder.append(' ');
                            //builder.append(createIndent(idt * width));
                            break;
                        case ELSE:
                        case ELSEIF:
                        case CASE:
                        case DEFAULT:
                        case CATCH:
                        case FINALLY:
                            //idt--;
                            if (isNewLine)
                                builder.append(createIndent(idt * width - width / 2));
                            builder.append(lexer.yytext());
                            //idt++;
                            isNewLine = false;
                            //isNewLine2 = false;
                            break;
                        case DOUBLE_COLON:
                        case AT:
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            break;
                        case END:
                        case UNTIL:
                        case RCURLY:
                            //idt--;
                            idt += idts.get(line);
                            if (isNewLine)
                                builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            isNewLine2 = false;
                            break;
                        default:
                            if (isNewLine)
                                builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            //idt += indent(type);
                            idt += idts.get(line);
                            isNewLine = false;
                            isNewLine2 = false;
                    }
                } else if (type == WHITE_SPACE) {
                    builder.append(' ');
                } else {
                    /*switch (type) {
                        case WHITE_SPACE:
                            break;
                        case ELSE:
                        case ELSEIF:
                        case CASE:
                        case DEFAULT:
                        case CATCH:
                        case FINALLY:
                            //idt--;
                            if (isNewLine)
                                builder.append(createIndent(idt * width - width / 2));
                            builder.append(lexer.yytext());
                            //idt++;
                            isNewLine = false;
                            isNewLine2 = false;
                            break;
                        case DOUBLE_COLON:
                        case AT:
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            break;
                        case END:
                        case UNTIL:
                        case RCURLY:

                           //idt--;
                            idt += idts.get(line);
                            if (isNewLine)
                                builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            isNewLine2 = false;
                            break;
                        case IF:
                        case WHILE:
                        case WHEN:
                        case LCURLY:
                        case FOR:
                        case FUNCTION:
                        case REPEAT:
                            if (isNewLine)
                                builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            isNewLine2 = false;
                            idt += idts.get(line);
                            break;
                        default:
                            if (isNewLine)
                                builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            //idt += indent(type);
                            //idt += idts.get(line);
                            isNewLine = false;
                            isNewLine2 = false;
                    }*/

                    builder.append(lexer.yytext());

                    /*if(type == LCURLY){
                        switch (last){
                            case TRY:
                            case CATCH:
                            case FINALLY:
                            case RPAREN:
                                idt += 0;
                                break;
                            default:
                                idt += indent(type);
                        }
                    }*/
                }
                //if (type != WHITE_SPACE && type != NEW_LINE)
                last = type;

                //Log.i("luaj", "format: "+last+";"+line+";"+idt);
            }
        } catch (IOException e) {
            LuaLog.getInstance().addError("AutoIndent", e);
        }

        return builder;
    }

    private static char[] createIndent(int n) {
        if (n < 0)
            return new char[0];
        char[] idts = new char[n];
        Arrays.fill(idts, ' ');
        return idts;
    }

    public static String[] fix(CharSequence text) {
        ArrayList<LuaString> gs = LexState.globalsList;
        ArrayList<String> ret = new ArrayList<>();
        for (LuaString g : gs) {
            ArrayList<String> cs = PackageUtil.fix(g.tojstring());
            if (cs != null) {
                for (String c : cs) {
                    if (!ret.contains(c))
                        ret.add(c);
                }
            }
        }
        if (ret.isEmpty()) {
            LuaLexer lexer = new LuaLexer(text);
            while (true) {
                try {
                    LuaTokenTypes type = lexer.advance();
                    if (type == null)
                        break;
                    if (type == NAME) {
                        ArrayList<String> cs = PackageUtil.fix(lexer.yytext());
                        if (cs != null) {
                            for (String c : cs) {
                                if (!ret.contains(c))
                                    ret.add(c);
                            }
                        }
                    } else if (type == DOT) {
                        lexer.advance();
                    }
                } catch (IOException e) {
                    LuaLog.getInstance().addError("AutoIndent", e);
                }
            }
        }

        String[] arr = new String[ret.size()];
        ret.toArray(arr);
        return arr;
    }
}
