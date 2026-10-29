package org.clonetuner.ini;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluator for the expression language used inside INI files: {@code { rpm > 500 && tps < 5 }}.
 *
 * <p>Supported: arithmetic, comparisons, boolean logic, ternary, string literals, identifier
 * lookups and the TunerStudio helper functions {@code arrayValue()} and {@code bitStringValue()}.</p>
 */
public final class Expr {

    /** Value source used while evaluating. */
    public interface Ctx {
        double num(String name);

        default String str(String name) {
            return null;
        }

        default double array(String name, int index) {
            return 0;
        }

        default String bitString(String name, int index) {
            return null;
        }
    }

    public static final Ctx EMPTY = new Ctx() {
        @Override
        public double num(String name) {
            return 0;
        }
    };

    private Expr() {
    }

    // ------------------------------------------------------------------ public API

    public static double eval(String expression, Ctx ctx) {
        Object v = value(expression, ctx);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return s.isBlank() ? 0 : 1;
            }
        }
        return 0;
    }

    public static boolean test(String expression, Ctx ctx) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        return eval(Ini.exprBody(expression), ctx) != 0;
    }

    public static String text(String expression, Ctx ctx) {
        Object v = value(expression, ctx);
        if (v == null) {
            return null;
        }
        if (v instanceof Double d) {
            return formatNumber(d);
        }
        return String.valueOf(v);
    }

    public static Object value(String expression, Ctx ctx) {
        if (expression == null) {
            return 0.0;
        }
        String src = Ini.isExpr(expression) ? Ini.exprBody(expression) : expression;
        try {
            Node node = CACHE.computeIfAbsent(src, s -> new Parser(s).parse());
            return node.eval(ctx);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    /** Formats a number with at most {@code digits} decimals and no trailing zeros. */
    public static String formatNumber(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        String s = String.format(Locale.ROOT, "%.4f", d);
        while (s.contains(".") && (s.endsWith("0"))) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // ------------------------------------------------------------------ AST

    private static final Map<String, Node> CACHE = new ConcurrentHashMap<>();

    private interface Node {
        Object eval(Ctx ctx);
    }

    private static double num(Object o, Ctx ctx) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return s.isBlank() ? 0 : 1;
            }
        }
        return 0;
    }

    private static boolean bool(Object o, Ctx ctx) {
        if (o instanceof Boolean b) {
            return b;
        }
        return num(o, ctx) != 0;
    }

    private static final class Lit implements Node {
        final Object value;

        Lit(Object value) {
            this.value = value;
        }

        @Override
        public Object eval(Ctx ctx) {
            return value;
        }
    }

    private static final class Ident implements Node {
        final String name;

        Ident(String name) {
            this.name = name;
        }

        @Override
        public Object eval(Ctx ctx) {
            String s = ctx.str(name);
            return s != null ? s : ctx.num(name);
        }
    }

    private static final class Unary implements Node {
        final String op;
        final Node a;

        Unary(String op, Node a) {
            this.op = op;
            this.a = a;
        }

        @Override
        public Object eval(Ctx ctx) {
            Object v = a.eval(ctx);
            return switch (op) {
                case "!" -> !bool(v, ctx);
                case "-" -> -num(v, ctx);
                default -> num(v, ctx);
            };
        }
    }

    private static final class Binary implements Node {
        final String op;
        final Node a;
        final Node b;

        Binary(String op, Node a, Node b) {
            this.op = op;
            this.a = a;
            this.b = b;
        }

        @Override
        public Object eval(Ctx ctx) {
            Object x = a.eval(ctx);
            Object y = b.eval(ctx);
            switch (op) {
                case "&&":
                    return bool(x, ctx) && bool(y, ctx);
                case "||":
                    return bool(x, ctx) || bool(y, ctx);
                case "==":
                    return bothNumeric(x, y) ? num(x, ctx) == num(y, ctx) : String.valueOf(x).equalsIgnoreCase(String.valueOf(y));
                case "!=":
                    return bothNumeric(x, y) ? num(x, ctx) != num(y, ctx) : !String.valueOf(x).equalsIgnoreCase(String.valueOf(y));
                case "+":
                    if (x instanceof String || y instanceof String) {
                        return String.valueOf(str(x)) + str(y);
                    }
                    return num(x, ctx) + num(y, ctx);
                case "-":
                    return num(x, ctx) - num(y, ctx);
                case "*":
                    return num(x, ctx) * num(y, ctx);
                case "/":
                    return num(y, ctx) == 0 ? 0.0 : num(x, ctx) / num(y, ctx);
                case "%":
                    return num(y, ctx) == 0 ? 0.0 : num(x, ctx) % num(y, ctx);
                case "<":
                    return num(x, ctx) < num(y, ctx);
                case "<=":
                    return num(x, ctx) <= num(y, ctx);
                case ">":
                    return num(x, ctx) > num(y, ctx);
                case ">=":
                    return num(x, ctx) >= num(y, ctx);
                case "&":
                    return (double) ((long) num(x, ctx) & (long) num(y, ctx));
                case "|":
                    return (double) ((long) num(x, ctx) | (long) num(y, ctx));
                default:
                    return 0.0;
            }
        }

        private boolean bothNumeric(Object x, Object y) {
            return !(x instanceof String) && !(y instanceof String);
        }

        private String str(Object o) {
            return o instanceof String s ? s : formatNumber(num(o, null));
        }
    }

    private static final class Ternary implements Node {
        final Node cond;
        final Node yes;
        final Node no;

        Ternary(Node cond, Node yes, Node no) {
            this.cond = cond;
            this.yes = yes;
            this.no = no;
        }

        @Override
        public Object eval(Ctx ctx) {
            return bool(cond.eval(ctx), ctx) ? yes.eval(ctx) : no.eval(ctx);
        }
    }

    private static final class Call implements Node {
        final String name;
        final List<Node> args;

        Call(String name, List<Node> args) {
            this.name = name.toLowerCase(Locale.ROOT);
            this.args = args;
        }

        @Override
        public Object eval(Ctx ctx) {
            try {
                switch (name) {
                    case "arrayvalue": {
                        if (args.size() < 2) {
                            return 0.0;
                        }
                        String list = nameOf(args.get(0));
                        int idx = (int) num(args.get(1).eval(ctx), ctx);
                        return ctx.array(list, idx);
                    }
                    case "bitstringvalue": {
                        if (args.size() < 2) {
                            return "";
                        }
                        String list = nameOf(args.get(0));
                        int idx = (int) num(args.get(1).eval(ctx), ctx);
                        String s = ctx.bitString(list, idx);
                        return s == null ? "" : s;
                    }
                    case "abs":
                        return Math.abs(num(args.get(0).eval(ctx), ctx));
                    case "min":
                        return Math.min(num(args.get(0).eval(ctx), ctx), num(args.get(1).eval(ctx), ctx));
                    case "max":
                        return Math.max(num(args.get(0).eval(ctx), ctx), num(args.get(1).eval(ctx), ctx));
                    case "round":
                        return (double) Math.round(num(args.get(0).eval(ctx), ctx));
                    case "int":
                        return (double) (long) num(args.get(0).eval(ctx), ctx);
                    case "defined":
                        return args.isEmpty() ? 0.0 : (ctx.num(nameOf(args.get(0))) != 0 ? 1.0 : 0.0);
                    default:
                        return 0.0;
                }
            } catch (RuntimeException e) {
                return 0.0;
            }
        }

        private String nameOf(Node n) {
            if (n instanceof Ident i) {
                return i.name;
            }
            return String.valueOf(n.eval(EMPTY));
        }
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        Node parse() {
            Node n = ternary();
            skip();
            if (pos < src.length()) {
                // Tolerate trailing debris instead of failing the whole expression.
                return n;
            }
            return n;
        }

        private void skip() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private boolean take(String s) {
            skip();
            if (src.startsWith(s, pos)) {
                pos += s.length();
                return true;
            }
            return false;
        }

        private Node ternary() {
            Node cond = or();
            if (take("?")) {
                Node yes = ternary();
                if (!take(":")) {
                    return cond;
                }
                Node no = ternary();
                return new Ternary(cond, yes, no);
            }
            return cond;
        }

        private Node or() {
            Node left = and();
            while (true) {
                skip();
                if (src.startsWith("||", pos)) {
                    pos += 2;
                    left = new Binary("||", left, and());
                } else {
                    return left;
                }
            }
        }

        private Node and() {
            Node left = comparison();
            while (true) {
                skip();
                if (src.startsWith("&&", pos)) {
                    pos += 2;
                    left = new Binary("&&", left, comparison());
                } else {
                    return left;
                }
            }
        }

        private Node comparison() {
            Node left = additive();
            while (true) {
                skip();
                if (src.startsWith("&&", pos) || src.startsWith("||", pos)) {
                    // Let and()/or() handle the two-character operators; a bare
                    // "&"/"|" below must not eat their first character.
                    return left;
                }
                String op = null;
                for (String candidate : new String[] {"==", "!=", "<=", ">=", "<", ">", "&", "|"}) {
                    if (src.startsWith(candidate, pos)) {
                        op = candidate;
                        break;
                    }
                }
                if (op == null) {
                    return left;
                }
                pos += op.length();
                left = new Binary(op, left, additive());
            }
        }

        private Node additive() {
            Node left = multiplicative();
            while (true) {
                skip();
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    String op = String.valueOf(src.charAt(pos++));
                    left = new Binary(op, left, multiplicative());
                } else {
                    return left;
                }
            }
        }

        private Node multiplicative() {
            Node left = unary();
            while (true) {
                skip();
                if (pos < src.length() && (src.charAt(pos) == '*' || src.charAt(pos) == '/' || src.charAt(pos) == '%')) {
                    String op = String.valueOf(src.charAt(pos++));
                    left = new Binary(op, left, unary());
                } else {
                    return left;
                }
            }
        }

        private Node unary() {
            skip();
            if (take("!")) {
                return new Unary("!", unary());
            }
            if (take("-")) {
                return new Unary("-", unary());
            }
            if (take("+")) {
                return new Unary("+", unary());
            }
            return primary();
        }

        private Node primary() {
            skip();
            if (take("(")) {
                Node n = ternary();
                take(")");
                return n;
            }
            if (pos >= src.length()) {
                return new Lit(0.0);
            }
            char c = src.charAt(pos);
            if (c == '"' || c == '\'') {
                return new Lit(readString(c));
            }
            if (Character.isDigit(c) || c == '.') {
                return new Lit(readNumber());
            }
            String id = readIdent();
            if (id.isEmpty()) {
                pos++;
                return new Lit(0.0);
            }
            skip();
            if (pos < src.length() && src.charAt(pos) == '(') {
                pos++;
                List<Node> args = new ArrayList<>();
                skip();
                if (!take(")")) {
                    do {
                        args.add(ternary());
                        skip();
                    } while (take(","));
                    take(")");
                }
                return new Call(id, args);
            }
            if (id.equalsIgnoreCase("true")) {
                return new Lit(true);
            }
            if (id.equalsIgnoreCase("false")) {
                return new Lit(false);
            }
            return new Ident(id);
        }

        private String readIdent() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$') {
                    pos++;
                } else {
                    break;
                }
            }
            return src.substring(start, pos);
        }

        private double readNumber() {
            int start = pos;
            boolean hex = src.startsWith("0x", pos) || src.startsWith("0X", pos);
            if (hex) {
                pos += 2;
                while (pos < src.length() && Character.digit(src.charAt(pos), 16) >= 0) {
                    pos++;
                }
                try {
                    return Long.parseLong(src.substring(start + 2, pos), 16);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.'
                    || src.charAt(pos) == 'e' || src.charAt(pos) == 'E'
                    || ((src.charAt(pos) == '+' || src.charAt(pos) == '-')
                        && pos > start && (src.charAt(pos - 1) == 'e' || src.charAt(pos - 1) == 'E')))) {
                pos++;
            }
            try {
                return Double.parseDouble(src.substring(start, pos));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private String readString(char quote) {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && src.charAt(pos) != quote) {
                char c = src.charAt(pos++);
                if (c == '\\' && pos < src.length()) {
                    c = src.charAt(pos++);
                }
                sb.append(c);
            }
            if (pos < src.length()) {
                pos++;
            }
            return sb.toString();
        }
    }
}
