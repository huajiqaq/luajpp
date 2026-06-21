package org.luaj;

/**
 * Holder class for Lua value constants that reference subclasses.
 * <p>
 * This class uses the Initialization-on-demand holder idiom to avoid
 * class loading deadlocks that can occur when a superclass (LuaValue)
 * directly references its subclasses during static initialization.
 * <p>
 * All constants are accessed via static fields to ensure proper
 * initialization order and thread safety. These constants replace
 * the deprecated static fields in {@link LuaValue}.
 *
 * @see LuaValue
 */
public final class LuaConstants {

    private LuaConstants() {
        // Utility class, not instantiable
    }

    /**
     * Varargs implemenation with no values.
     * <p>
     * This is an internal class not intended to be used directly.
     * Instead use the predefined constant {@link LuaConstants#NONE}
     *
     * @see #NONE
     */
    private static final class None extends LuaNil {
        static None _NONE = new None();

        @Override
        public LuaValue arg(int i) {
            return LuaConstants.NIL;
        }

        @Override
        public int narg() {
            return 0;
        }

        @Override
        public LuaValue arg1() {
            return LuaConstants.NIL;
        }

        @Override
        public String tojstring() {
            return "none";
        }

        @Override
        public Varargs subargs(final int start) {
            return start > 0 ? this : argerror(1, "start must be > 0");
        }

        @Override
        void copyto(LuaValue[] dest, int offset, int length) {
            for (; length > 0; length--) dest[offset++] = NIL;
        }
    }

    // ======================== Nil ========================

    /**
     * LuaValue constant corresponding to lua {@code #NIL}
     */
    public static final LuaValue NIL = LuaNil._NIL;

    // ======================== Boolean ========================

    /**
     * LuaBoolean constant corresponding to lua {@code true}
     */
    public static final LuaBoolean TRUE = LuaBoolean._TRUE;

    /**
     * LuaBoolean constant corresponding to lua {@code false}
     */
    public static final LuaBoolean FALSE = LuaBoolean._FALSE;

    // ======================== None ========================

    /**
     * LuaValue constant corresponding to a {@link Varargs} list of no values
     */
    public static final LuaValue NONE = None._NONE;

    // ======================== Numbers ========================

    /**
     * LuaValue number constant equal to 0
     */
    public static final LuaNumber ZERO = LuaInteger.valueOf(0);

    /**
     * LuaValue number constant equal to 1
     */
    public static final LuaNumber ONE = LuaInteger.valueOf(1);

    /**
     * LuaValue number constant equal to -1
     */
    public static final LuaNumber MINUSONE = LuaInteger.valueOf(-1);

    // ======================== No Values Array ========================

    /**
     * LuaValue array constant with no values
     */
    public static final LuaValue[] NOVALS = {};

    // ======================== ENV ========================

    /**
     * The variable name of the environment.
     */
    public static final LuaString ENV = LuaString.valueOf("_ENV");

    // ======================== Empty String ========================

    /**
     * LuaString constant with value ""
     */
    public static final LuaString EMPTYSTRING = LuaString.valueOf("");

    // ======================== Metatag Constants ========================

    /**
     * LuaString constant with value "__index" for use as metatag
     */
    public static final LuaString INDEX = LuaString.valueOf("__index");

    /**
     * LuaString constant with value "__newindex" for use as metatag
     */
    public static final LuaString NEWINDEX = LuaString.valueOf("__newindex");

    /**
     * LuaString constant with value "__call" for use as metatag
     */
    public static final LuaString CALL = LuaString.valueOf("__call");

    /**
     * LuaString constant with value "__mode" for use as metatag
     */
    public static final LuaString MODE = LuaString.valueOf("__mode");

    /**
     * LuaString constant with value "__metatable" for use as metatag
     */
    public static final LuaString METATABLE = LuaString.valueOf("__metatable");

    /**
     * LuaString constant with value "__add" for use as metatag
     */
    public static final LuaString ADD = LuaString.valueOf("__add");

    /**
     * LuaString constant with value "__sub" for use as metatag
     */
    public static final LuaString SUB = LuaString.valueOf("__sub");

    /**
     * LuaString constant with value "__div" for use as metatag
     */
    public static final LuaString DIV = LuaString.valueOf("__div");

    /**
     * LuaString constant with value "__mul" for use as metatag
     */
    public static final LuaString MUL = LuaString.valueOf("__mul");

    /**
     * LuaString constant with value "__pow" for use as metatag
     */
    public static final LuaString POW = LuaString.valueOf("__pow");

    /**
     * LuaString constant with value "__mod" for use as metatag
     */
    public static final LuaString MOD = LuaString.valueOf("__mod");

    /**
     * LuaString constant with value "__unm" for use as metatag
     */
    public static final LuaString UNM = LuaString.valueOf("__unm");

    /**
     * LuaString constant with value "__len" for use as metatag
     */
    public static final LuaString LEN = LuaString.valueOf("__len");

    /**
     * LuaString constant with value "__eq" for use as metatag
     */
    public static final LuaString EQ = LuaString.valueOf("__eq");

    /**
     * LuaString constant with value "__lt" for use as metatag
     */
    public static final LuaString LT = LuaString.valueOf("__lt");

    /**
     * LuaString constant with value "__le" for use as metatag
     */
    public static final LuaString LE = LuaString.valueOf("__le");

    /**
     * LuaString constant with value "__tostring" for use as metatag
     */
    public static final LuaString TOSTRING = LuaString.valueOf("__tostring");

    /**
     * LuaString constant with value "__type" for use as metatag
     */
    public static final LuaString TYPE = LuaString.valueOf("__type");

    /**
     * LuaString constant with value "__pairs" for use as metatag
     */
    public static final LuaString PAIRS = LuaString.valueOf("__pairs");

    /**
     * LuaString constant with value "__ipairs" for use as metatag
     */
    public static final LuaString IPAIRS = LuaString.valueOf("__ipairs");

    /**
     * LuaString constant with value "__concat" for use as metatag
     */
    public static final LuaString CONCAT = LuaString.valueOf("__concat");

    /**
     * LuaString constant with value "__idiv" for use as metatag
     */
    public static final LuaString IDIV = LuaString.valueOf("__idiv");

    /**
     * LuaString constant with value "__band" for use as metatag
     */
    public static final LuaString BAND = LuaString.valueOf("__band");

    /**
     * LuaString constant with value "__bor" for use as metatag
     */
    public static final LuaString BOR = LuaString.valueOf("__bor");

    /**
     * LuaString constant with value "__bxor" for use as metatag
     */
    public static final LuaString BXOR = LuaString.valueOf("__bxor");

    /**
     * LuaString constant with value "__shl" for use as metatag
     */
    public static final LuaString SHL = LuaString.valueOf("__shl");

    /**
     * LuaString constant with value "__shr" for use as metatag
     */
    public static final LuaString SHR = LuaString.valueOf("__shr");

    /**
     * LuaString constant with value "__bnot" for use as metatag
     */
    public static final LuaString BNOT = LuaString.valueOf("__bnot");
}