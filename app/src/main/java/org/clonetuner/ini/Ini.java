package org.clonetuner.ini;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Raw reader for the MegaSquirt / TunerStudio INI dialect.
 *
 * <p>Handles section splitting, comment stripping, the {@code #if/#else/#endif/#unset} preprocessor
 * and the comma tokenizer that understands quoted strings, {@code {expressions}} and
 * {@code [shapes]}.</p>
 */
public final class Ini {

    public record Line(String section, String key, String value, int lineNo) {
    }

    public record Tok(String text, boolean quoted) {
        public boolean isBlank() {
            return text == null || text.isBlank();
        }

        @Override
        public String toString() {
            return quoted ? '"' + text + '"' : text;
        }
    }

    public static final class Document {
        private final List<Line> lines;
        private final Set<String> defines;
        private final Map<String, List<Line>> sections = new LinkedHashMap<>();

        Document(List<Line> lines, Set<String> defines) {
            this.lines = lines;
            this.defines = defines;
            for (Line l : lines) {
                sections.computeIfAbsent(l.section(), k -> new ArrayList<>()).add(l);
            }
        }

        public List<Line> lines() {
            return lines;
        }

        public Set<String> defines() {
            return defines;
        }

        public boolean isDefined(String symbol) {
            return defines.contains(symbol.toUpperCase(Locale.ROOT));
        }

        public List<Line> section(String name) {
            return sections.getOrDefault(name.toUpperCase(Locale.ROOT), List.of());
        }

        public boolean hasSection(String name) {
            return sections.containsKey(name.toUpperCase(Locale.ROOT));
        }

        public Line first(String section, String key) {
            for (Line l : section(section)) {
                if (l.key().equalsIgnoreCase(key)) {
                    return l;
                }
            }
            return null;
        }

        public String str(String section, String key, String def) {
            Line l = first(section, key);
            return l == null ? def : l.value();
        }

        public List<Tok> tokens(String section, String key) {
            Line l = first(section, key);
            return l == null ? List.of() : Ini.tokenize(l.value());
        }
    }

    private Ini() {
    }

    // ------------------------------------------------------------------ loading

    public static Document parse(Path iniFile, Path settingGroupsXml) throws IOException {
        String text = new String(Files.readAllBytes(iniFile), StandardCharsets.UTF_8);
        List<String> raw = java.util.Arrays.asList(text.split("\\R", -1));
        Set<String> defines = collectSymbols(raw, settingGroupsXml);
        List<Line> active = preprocess(raw, defines);
        return new Document(active, defines);
    }

    /** Resolves every setting group to its active option: setting groups become compile time symbols. */
    private static Set<String> collectSymbols(List<String> raw, Path settingGroupsXml) {
        Set<String> defines = new LinkedHashSet<>();
        // Built-in groups shipped with TunerStudio (config/settingGroups.xml).
        if (settingGroupsXml != null && Files.isRegularFile(settingGroupsXml)) {
            try {
                parseBuiltinGroups(new String(Files.readAllBytes(settingGroupsXml), StandardCharsets.UTF_8), defines);
            } catch (IOException ignored) {
            }
        }
        // Groups declared in the INI itself.  Default option is "DEFAULT" when present, else the first one.
        Map<String, List<String>> groups = new LinkedHashMap<>();
        String current = null;
        for (String line : raw) {
            String s = stripComment(line).trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            if (s.equalsIgnoreCase("[SettingGroups]")) {
                continue;
            }
            int eq = s.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = s.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = s.substring(eq + 1).trim();
            if (key.equals("settinggroup")) {
                current = unquote(firstToken(val));
                groups.computeIfAbsent(current, k -> new ArrayList<>());
            } else if (key.equals("settingoption") && current != null) {
                groups.get(current).add(unquote(firstToken(val)));
            }
        }
        groups.forEach((group, options) -> {
            if (options.isEmpty()) {
                defines.add(group.toUpperCase(Locale.ROOT));
            } else if (options.stream().anyMatch("DEFAULT"::equalsIgnoreCase)) {
                defines.add("DEFAULT");
            } else {
                defines.add(options.get(0).toUpperCase(Locale.ROOT));
            }
        });
        // "#unset symbol" is a hard disable, even when the group is declared later in the file.
        for (String line : raw) {
            String s = stripComment(line).trim();
            if (s.toLowerCase(Locale.ROOT).startsWith("#unset")) {
                defines.remove(s.substring(6).trim().toUpperCase(Locale.ROOT));
            }
        }
        return defines;
    }

    private static void parseBuiltinGroups(String xml, Set<String> defines) {
        Pattern group = Pattern.compile("<settingGroup[^>]*name=\"([^\"]*)\"[^>]*>(.*?)</settingGroup>",
                Pattern.DOTALL);
        Matcher gm = group.matcher(xml);
        while (gm.find()) {
            String body = gm.group(2);
            Pattern opt = Pattern.compile("<configurationOption([^>]*)/>");
            Matcher om = opt.matcher(body);
            boolean foundDefault = false;
            String first = null;
            while (om.find()) {
                String attrs = om.group(1);
                String name = attr(attrs, "name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (first == null) {
                    first = name;
                }
                if ("true".equalsIgnoreCase(attr(attrs, "default"))) {
                    defines.add(name.toUpperCase(Locale.ROOT));
                    foundDefault = true;
                }
            }
            if (!foundDefault && first != null) {
                defines.add(first.toUpperCase(Locale.ROOT));
            }
        }
    }

    private static String attr(String attrs, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    private static List<Line> preprocess(List<String> raw, Set<String> defines) {
        List<Line> out = new ArrayList<>();
        Deque<boolean[]> stack = new ArrayDeque<>(); // {parentActive, active, seenElse}
        Map<String, String> macros = new LinkedHashMap<>();
        String section = "";
        boolean active = true;
        for (int i = 0; i < raw.size(); i++) {
            String line = raw.get(i);
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("#define") && !active) {
                continue;
            }
            if (lower.startsWith("#define")) {
                String body = stripComment(trimmed.substring(7)).trim();
                int eq = body.indexOf('=');
                if (eq > 0) {
                    String name = body.substring(0, eq).trim();
                    String value = expandMacros(body.substring(eq + 1).trim(), macros);
                    if (!name.isEmpty()) {
                        macros.put(name, value);
                    }
                }
                continue;
            }
            if (lower.startsWith("#if") && (trimmed.length() == 3 || Character.isWhitespace(trimmed.charAt(3)))) {
                String cond = stripComment(trimmed.substring(3)).trim();
                boolean value = active && evalCond(cond, defines);
                stack.push(new boolean[] {active, value, false});
                active = value;
                continue;
            }
            if (lower.equals("#else")) {
                if (!stack.isEmpty()) {
                    boolean[] frame = stack.pop();
                    boolean value = frame[0] && !frame[2];
                    frame[2] = true;
                    active = value;
                    frame[1] = value;
                    stack.push(frame);
                }
                continue;
            }
            if (lower.equals("#endif") || lower.equals("#fi")) {
                if (!stack.isEmpty()) {
                    active = stack.pop()[0];
                }
                continue;
            }
            if (lower.startsWith("#unset") || lower.startsWith("#set") || lower.startsWith("#include")) {
                continue;
            }
            if (!active) {
                continue;
            }
            String noComment = stripComment(line);
            String t = noComment.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("[") && t.endsWith("]")) {
                section = t.substring(1, t.length() - 1).trim().toUpperCase(Locale.ROOT);
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = t.substring(0, eq).trim();
            String value = t.substring(eq + 1).trim();
            if (!macros.isEmpty()) {
                value = expandMacros(value, macros);
            }
            out.add(new Line(section, key, value, i + 1));
        }
        return out;
    }

    /**
     * Substitutes {@code $name} references (INI text macros) with their {@code #define} body.
     * Escaped {@code \$name} tokens are left untouched, and longer names win over shorter
     * prefixes so that e.g. {@code $fullStatus_def_1} is not eaten by {@code $fullStatus_def}.
     */
    private static String expandMacros(String value, Map<String, String> macros) {
        if (macros.isEmpty() || value == null || value.indexOf('$') < 0) {
            return value;
        }
        String out = value;
        List<String> names = new ArrayList<>(macros.keySet());
        names.sort((a, b) -> b.length() - a.length());
        for (String name : names) {
            String replacement = Matcher.quoteReplacement(macros.get(name));
            out = out.replaceAll("(?<!\\\\)\\$" + Pattern.quote(name), replacement);
        }
        return out;
    }

    /** Boolean expression made of defined symbols, {@code !}, {@code &&}, {@code ||} and parentheses. */
    public static boolean evalCond(String cond, Set<String> defines) {
        if (cond == null || cond.isBlank()) {
            return true;
        }
        List<String> tk = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < cond.length(); i++) {
            char c = cond.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                cur.append(c);
            } else if (c == '!' || c == '&' || c == '|' || c == '(' || c == ')') {
                if (cur.length() != 0) {
                    tk.add(cur.toString());
                    cur.setLength(0);
                }
                if (c == '&' && i + 1 < cond.length() && cond.charAt(i + 1) == '&') {
                    tk.add("&&");
                    i++;
                } else if (c == '|' && i + 1 < cond.length() && cond.charAt(i + 1) == '|') {
                    tk.add("||");
                    i++;
                } else {
                    tk.add(String.valueOf(c));
                }
            } else if (Character.isWhitespace(c)) {
                if (cur.length() != 0) {
                    tk.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                // Unknown construct: treat the whole condition as a symbol sequence.
                cur.append(c);
            }
        }
        if (cur.length() != 0) {
            tk.add(cur.toString());
        }
        int[] pos = {0};
        boolean[] result = {false};
        try {
            result[0] = orExpr(tk, pos, defines);
        } catch (RuntimeException ignored) {
            return false;
        }
        return result[0];
    }

    private static boolean orExpr(List<String> tk, int[] pos, Set<String> defines) {
        boolean v = andExpr(tk, pos, defines);
        while (pos[0] < tk.size() && tk.get(pos[0]).equals("||")) {
            pos[0]++;
            boolean r = andExpr(tk, pos, defines);
            v = v || r;
        }
        return v;
    }

    private static boolean andExpr(List<String> tk, int[] pos, Set<String> defines) {
        boolean v = unaryExpr(tk, pos, defines);
        while (pos[0] < tk.size() && tk.get(pos[0]).equals("&&")) {
            pos[0]++;
            boolean r = unaryExpr(tk, pos, defines);
            v = v && r;
        }
        return v;
    }

    private static boolean unaryExpr(List<String> tk, int[] pos, Set<String> defines) {
        String t = pos[0] < tk.size() ? tk.get(pos[0]) : "";
        if (t.equals("!")) {
            pos[0]++;
            return !unaryExpr(tk, pos, defines);
        }
        if (t.equals("(")) {
            pos[0]++;
            boolean v = orExpr(tk, pos, defines);
            if (pos[0] < tk.size() && tk.get(pos[0]).equals(")")) {
                pos[0]++;
            }
            return v;
        }
        pos[0]++;
        return defines.contains(t.toUpperCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ text helpers

    /** Removes a {@code ;} comment, honouring quoted strings and backslash escapes. */
    public static String stripComment(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ';' && !inQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /** Splits a value on top-level commas, keeping quotes, braces and brackets intact. */
    public static List<Tok> tokenize(String value) {
        List<Tok> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean pendingQuote = false;
        boolean closedQuote = false;
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' && depth == 0) {
                if (quoted) {
                    out.add(new Tok(cur.toString(), true));
                    cur.setLength(0);
                    quoted = false;
                    pendingQuote = false;
                    closedQuote = true;
                } else {
                    if (cur.toString().isBlank()) cur.setLength(0);
                    quoted = true;
                    pendingQuote = true;
                }
                continue;
            }
            if (quoted) {
                if (c == '\\' && i + 1 < value.length()) {
                    char n = value.charAt(++i);
                    switch (n) {
                        case 'n' -> cur.append('\n');
                        case 'r' -> cur.append('\r');
                        case 't' -> cur.append('\t');
                        case 'x' -> {
                            if (i + 2 < value.length()) {
                                try {
                                    cur.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                                    i += 2;
                                } catch (NumberFormatException e) {
                                    cur.append('x');
                                }
                            }
                        }
                        default -> cur.append(n);
                    }
                } else {
                    cur.append(c);
                }
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                cur.append(c);
            } else if (c == '}' || c == ']' || c == ')') {
                depth = Math.max(0, depth - 1);
                cur.append(c);
            } else if (c == ',' && depth == 0) {
                if (closedQuote && cur.toString().trim().isEmpty()) {
                    // The comma that follows a quoted token was already consumed by closing it.
                    closedQuote = false;
                    cur.setLength(0);
                    continue;
                }
                out.add(new Tok(cur.toString().trim(), pendingQuote));
                cur.setLength(0);
                pendingQuote = false;
                closedQuote = false;
            } else {
                if (!Character.isWhitespace(c)) {
                    closedQuote = false;
                }
                cur.append(c);
            }
        }
        String s = cur.toString().trim();
        if (!s.isEmpty() || pendingQuote || value.stripTrailing().endsWith(",")) {
            out.add(new Tok(s, pendingQuote));
        }
        return out;
    }

    public static String unquote(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            List<Tok> t = tokenize(s);
            return t.isEmpty() ? "" : t.get(0).text();
        }
        return s;
    }

    public static String firstToken(String s) {
        List<Tok> t = tokenize(s);
        return t.isEmpty() ? "" : t.get(0).text();
    }

    /** Removes the surrounding braces of an expression token. */
    public static String exprBody(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    public static boolean isExpr(String s) {
        if (s == null) {
            return false;
        }
        s = s.trim();
        return s.startsWith("{") && s.endsWith("}");
    }

    public static List<String> strings(String value) {
        List<String> out = new ArrayList<>();
        for (Tok t : tokenize(value)) {
            out.add(t.text());
        }
        return out;
    }

    public static String toUpper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
