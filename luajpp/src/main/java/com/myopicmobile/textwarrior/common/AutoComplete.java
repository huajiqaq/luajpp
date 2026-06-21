package com.myopicmobile.textwarrior.common;


import com.androlua.internal.LuaLog;

import java.io.IOException;
import java.util.Arrays;

public class AutoComplete {
    public static int createAutoIndent(CharSequence text) {
        LuaLexer lexer = new LuaLexer(text);
        int idt = 0;
        try {
            while (true) {
                LuaTokenTypes type = lexer.advance();
                if (type == null) {
                    break;
                }
                idt += indent(type);
            }
        } catch (IOException e) {
            LuaLog.getInstance().addError("AutoComplete", e);
        }
        return idt;
    }


    private static int indent(LuaTokenTypes t) {
        return switch (t) {
            case DO, FUNCTION, THEN, REPEAT, LCURLY -> 1;
            case UNTIL, ELSEIF, END, RCURLY -> -1;
            default -> 0;
        };
    }

    public static CharSequence format(CharSequence text, int width) {
        StringBuilder builder = new StringBuilder();
        boolean isNewLine = true;
        LuaLexer lexer = new LuaLexer(text);
        try {
            int idt = 0;

            while (true) {
                LuaTokenTypes type = lexer.advance();
                if (type == null)
                    break;
                if (type == LuaTokenTypes.NEW_LINE) {
                    isNewLine = true;
                    builder.append('\n');
                    idt = Math.max(0, idt);

                } else if (isNewLine) {
                    if (type == LuaTokenTypes.WHITE_SPACE) {
                    } else if (type == LuaTokenTypes.ELSE) {
                        idt--;
                        builder.append(createIndent(idt * width));
                        builder.append(lexer.yytext());
                        idt++;
                        isNewLine = false;
                    } else if (type == LuaTokenTypes.ELSEIF || type == LuaTokenTypes.END || type == LuaTokenTypes.UNTIL || type == LuaTokenTypes.RCURLY) {
                        idt--;
                        builder.append(createIndent(idt * width));
                        builder.append(lexer.yytext());

                        isNewLine = false;
                    } else {
                        builder.append(createIndent(idt * width));
                        builder.append(lexer.yytext());
                        idt += indent(type);
                        isNewLine = false;
                    }
                } else if (type == LuaTokenTypes.WHITE_SPACE) {
                    builder.append(' ');
                } else {
                    builder.append(lexer.yytext());
                    idt += indent(type);
                }

            }
        } catch (IOException e) {
            LuaLog.getInstance().addError("AutoComplete", e);
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

}
