package org.clonetuner.ini;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Semantic model of a MegaSquirt/TunerStudio INI file: menus, dialogs, constants, tables,
 * curves, gauges, output channels and datalog definitions.
 */
public final class IniModel {

    // ------------------------------------------------------------------ definitions

    public static final class Constant {
        public String name = "";
        public String cls = "scalar";      // scalar | array | bits
        public String type = "U08";
        public int offset;
        public int page = 1;
        public int[] shape;                // arrays: [rows, cols] or [len]
        public int bitLo;
        public int bitHi = 7;
        public String units = "";
        public String scaleRaw = "1.0";
        public String translateRaw = "0.0";
        public String loRaw = "0";
        public String hiRaw = "0";
        public int digits;
        public List<String> options = new ArrayList<>();

        public boolean isBits() {
            return "bits".equalsIgnoreCase(cls);
        }

        public boolean isArray() {
            return "array".equalsIgnoreCase(cls);
        }

        public int length() {
            if (shape == null) {
                return 1;
            }
            int n = 1;
            for (int s : shape) {
                n *= s;
            }
            return n;
        }

        public int rows() {
            return shape != null && shape.length > 1 ? shape[0] : 1;
        }

        public int cols() {
            if (shape == null) {
                return 1;
            }
            return shape.length > 1 ? shape[1] : shape[0];
        }

        public int bytesPerItem() {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "U08", "S08" -> 1;
                case "U16", "S16" -> 2;
                case "U32", "S32", "F32" -> 4;
                default -> 1;
            };
        }

        @Override
        public String toString() {
            return name + " (" + cls + " " + type + " @" + page + ":" + offset + ")";
        }
    }

    public static final class Channel {
        public String name = "";
        public String cls = "scalar";
        public String type = "U08";
        public int offset;
        public String units = "";
        public String scaleRaw = "1.0";
        public String translateRaw = "0.0";
        public int digits = 2;
        public int bitLo;
        public int bitHi;
        /** For derived channels ({@code name = { expr }} in [OutputChannels]). */
        public String expr = "";

        public boolean isBits() {
            return "bits".equalsIgnoreCase(cls);
        }

        public boolean isExpr() {
            return "expr".equalsIgnoreCase(cls) && !expr.isEmpty();
        }

        public int bytesPerItem() {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "U08", "S08" -> 1;
                case "U16", "S16" -> 2;
                case "U32", "S32", "F32" -> 4;
                default -> 1;
            };
        }
    }

    public static final class MenuEntry {
        public enum Kind { DIALOG, SEPARATOR, GROUP, STD }

        public Kind kind = Kind.DIALOG;
        public String id = "";
        public String label = "";
        public String page = "";
        public String vis = "";
        public List<MenuEntry> children = new ArrayList<>();

        @Override
        public String toString() {
            return label + " -> " + id;
        }
    }

    public static final class Menu {
        public String label = "";
        public List<MenuEntry> entries = new ArrayList<>();
    }

    public static final class Dialog {
        public String name = "";
        public String title = "";
        public String layout = "yAxis";
        public String topicHelp = "";
        public List<Item> items = new ArrayList<>();
    }

    public static final class Item {
        public enum Type {
            FIELD, PANEL, LABEL, COMMAND, SLIDER, INDICATOR, GAUGE, TEXT, DISPLAY_ONLY,
            SETTING_SELECTOR, LIVE_GRAPH, GRAPH_LINE, UNKNOWN
        }

        public Type type = Type.UNKNOWN;
        public String label = "";
        public String constant = "";
        public String constraint = "";
        public String vis = "";
        public String enable = "";
        public String command = "";
        public String help = "";
        public List<String> values = new ArrayList<>();
        public double n1;
        public double n2;
        public String graphId = "";
        public List<Item> children = new ArrayList<>();

        @Override
        public String toString() {
            return type + " " + label + (constant.isEmpty() ? "" : " [" + constant + "]");
        }
    }

    public static final class Table {
        public String id = "";
        public String map3dId = "";
        public String title = "";
        public int page = 1;
        public String xBins = "";
        public String yBins = "";
        public String zBins = "";
        public String xLabel = "X";
        public String yLabel = "Y";
        public List<String> xyLabels = new ArrayList<>();
        public double gridHeight = 2.0;
        public List<Double> gridOrient = new ArrayList<>();
        public List<String> upDownLabels = new ArrayList<>();
        public String topicHelp = "";
    }

    public static final class Curve {
        public String id = "";
        public String title = "";
        public String xBins = "";
        public String yBins = "";
        public List<String> columnLabels = new ArrayList<>();
        public double[] xAxis = {0, 100, 6};
        public double[] yAxis = {0, 100, 6};
        public String topicHelp = "";
    }

    public static final class Logger {
        public String id="", type="", startCommand="", stopCommand="", readCommand="", readyCondition="";
        public int recordLength;
    }

    public static final class Gauge {
        public String name = "";
        public String var = "";
        public String title = "";
        public String unitsRaw = "";
        public String loRaw = "0";
        public String hiRaw = "100";
        public String loDRaw = "-1";
        public String loWRaw = "-1";
        public String hiWRaw = "999";
        public String hiDRaw = "999";
        public int vd;
        public int ld;
        public String category = "Main";
    }

    public static final class Indicator {
        public String expr = "";
        public String offLabel = "OFF";
        public String onLabel = "ON";
        public String offBg = "white";
        public String offFg = "black";
        public String onBg = "green";
        public String onFg = "black";
    }

    public static final class DatalogEntry {
        public String channel = "";
        public String label = "";
        public String type = "float";
        public String format = "%.3f";
        public String condition = "";
    }

    // ------------------------------------------------------------------ contents

    public Path iniPath;
    public String signature = "";
    public String queryCommand = "Q";
    public String versionInfo = "S";
    public String iniSpecVersion = "";
    public String endianness = "little";
    public int nPages = 1;
    public int[] pageSize = {128};
    public int blockingFactor = 128;
    public int ochBlockSize = 0;
    public String ochGetCommand = "";
    public String helpUrl = "";

    public final Map<String, Constant> constants = new LinkedHashMap<>();
    public final Map<String, Constant> pcBits = new LinkedHashMap<>();
    public final Map<String, Constant> pcScalars = new LinkedHashMap<>();
    /** defaultValue entries from [ConstantsExtensions]: constant name to raw value token(s). */
    public final Map<String, List<String>> defaults = new LinkedHashMap<>();
    public final Map<String, List<String>> pcLists = new LinkedHashMap<>();
    public final Map<String, String> pcExprs = new LinkedHashMap<>();
    public final Map<String, Channel> channels = new LinkedHashMap<>();
    public final Map<String, Dialog> dialogs = new LinkedHashMap<>();
    public final List<Dialog> dialogList = new ArrayList<>();
    public final Map<String, Logger> loggers = new LinkedHashMap<>();
    public final Map<String, Table> tables = new LinkedHashMap<>();
    public final Map<String, Curve> curves = new LinkedHashMap<>();
    public final Map<String, Gauge> gauges = new LinkedHashMap<>();
    public final Map<String, List<Menu>> menuSets = new LinkedHashMap<>();
    public final Map<String, String> settingHelp = new LinkedHashMap<>();
    public final Map<String, List<String>> controllerCommands = new LinkedHashMap<>();
    public final Map<String, String[]> tools = new LinkedHashMap<>();
    public final List<DatalogEntry> datalog = new ArrayList<>();
    public final List<Gauge> frontGauges = new ArrayList<>(8);
    public final List<Indicator> indicators = new ArrayList<>();
    public final List<String> gaugeCategories = new ArrayList<>();
    /** [Help] entries: help id -> target URL (or other target). */
    public final Map<String, String> helpLinks = new LinkedHashMap<>();

    public Constant constant(String name) {
        Constant c = constants.get(name);
        if (c == null) {
            c = pcScalars.get(name);
        }
        if (c == null) {
            c = pcBits.get(name);
        }
        if (c == null) {
            c = constants.get(name.toLowerCase(Locale.ROOT));
        }
        return c;
    }

    public Channel channel(String name) {
        return channels.get(name);
    }

    public Gauge gauge(String name) {
        return gauges.get(name);
    }

    // ------------------------------------------------------------------ loading

    public static IniModel load(Path iniPath, Path settingGroupsXml) throws IOException {
        Ini.Document doc = Ini.parse(iniPath, settingGroupsXml);
        IniModel m = new IniModel();
        m.iniPath = iniPath;
        m.parseHeader(doc);
        m.parseConstants(doc);
        m.parsePcVariables(doc);
        m.parseDefaults(doc);
        m.parseOutputChannels(doc);
        m.parseMenus(doc);
        m.parseDialogs(doc);
        m.parseTables(doc);
        m.parseCurves(doc);
        m.parseGauges(doc);
        m.parseFrontPage(doc);
        m.parseDatalog(doc);
        m.parseTools(doc);
        m.parseControllerCommands(doc);
        m.parseSettingHelp(doc);
        m.parseHelp(doc);
        m.parseLoggers(doc);
        return m;
    }

    public String description() {
        return signature + "  [" + constants.size() + " constants, " + dialogList.size() + " dialogs, "
                + tables.size() + " tables, " + curves.size() + " curves, " + gauges.size() + " gauges]";
    }

    private void parseLoggers(Ini.Document doc) {
        Logger current=null;
        for(Ini.Line line:doc.section("LoggerDefinition")) {
            List<String> tokens=Ini.strings(line.value());
            if(tokens.isEmpty()) continue;
            if(line.key().equalsIgnoreCase("loggerDef")) {
                current=new Logger(); current.id=tokens.get(0);
                current.type=tokens.size()>2?tokens.get(2):"";
                loggers.put(current.id,current);
            } else if(current!=null) {
                switch(line.key().toLowerCase(Locale.ROOT)) {
                    case "startcommand" -> current.startCommand=tokens.get(0);
                    case "stopcommand" -> current.stopCommand=tokens.get(0);
                    case "datareadcommand" -> current.readCommand=tokens.get(0);
                    case "datareadycondition" -> current.readyCondition=line.value().trim();
                    case "recorddef" -> current.recordLength=tokens.size()>2?parseInt(tokens.get(2),0):0;
                    default -> { }
                }
            }
        }
    }

    private void parseHeader(Ini.Document doc) {
        signature = unq(doc.str("MegaTune", "signature", ""));
        queryCommand = unq(doc.str("MegaTune", "queryCommand", "Q"));
        versionInfo = unq(doc.str("MegaTune", "versionInfo", "S"));
        iniSpecVersion = unq(doc.str("TunerStudio", "iniSpecVersion", ""));
        helpUrl = unq(doc.str("MegaTune", "help", ""));
    }

    private void parseConstants(Ini.Document doc) {
        endianness = unq(doc.str("Constants", "endianness", "little"));
        try {
            nPages = (int) Double.parseDouble(unq(doc.str("Constants", "nPages", "1")));
        } catch (NumberFormatException e) {
            nPages = 1;
        }
        List<String> sizes = Ini.strings(doc.str("Constants", "pageSize", "128"));
        pageSize = new int[Math.max(1, sizes.size())];
        for (int i = 0; i < pageSize.length; i++) {
            pageSize[i] = parseInt(sizes.get(i), 128);
        }
        blockingFactor = parseInt(unq(doc.str("Constants", "blockingFactor", "128")), 128);
        int page = 1;
        for (Ini.Line line : doc.section("Constants")) {
            String key = line.key();
            String value = line.value();
            switch (key.toLowerCase(Locale.ROOT)) {
                case "page" -> page = parseInt(unq(value), page);
                case "endianness", "npages", "pagesize", "pageidentifier", "blockingfactor",
                     "tableblockingfactor", "ochgetcommand", "ochblocksize" -> {
                }
                default -> {
                    Constant c = parseConstant(key, value, page);
                    if (c != null) {
                        constants.put(c.name, c);
                    }
                }
            }
        }
    }

    private void parsePcVariables(Ini.Document doc) {
        for (Ini.Line line : doc.section("PcVariables")) {
            String key = line.key();
            String value = line.value().trim();
            if (value.startsWith("{")) {
                pcExprs.put(key, Ini.exprBody(value));
                continue;
            }
            List<String> t = Ini.strings(value);
            if (t.isEmpty()) {
                continue;
            }
            String cls = t.get(0).toLowerCase(Locale.ROOT);
            if (cls.equals("bits")) {
                Constant c = new Constant();
                c.name = key;
                c.cls = "bits";
                c.type = t.size() > 1 ? t.get(1) : "U08";
                c.page = 0;
                c.offset = 0;
                for (int i = 2; i < t.size(); i++) {
                    String tok = t.get(i);
                    if (tok.startsWith("[")) {
                        int[] r = parseRange(tok);
                        c.bitLo = r[0];
                        c.bitHi = r[1];
                    } else if (!tok.isBlank()) {
                        c.options.add(resolve(tok));
                    }
                }
                pcBits.put(key, c);
            } else if (cls.equals("scalar")) {
                Constant c = new Constant();
                c.name = key;
                c.cls = "scalar";
                c.type = t.size() > 1 ? t.get(1).toUpperCase(Locale.ROOT) : "U08";
                c.page = 0;
                c.offset = 0;
                c.units = t.size() > 2 ? t.get(2) : "";
                c.scaleRaw = t.size() > 3 ? t.get(3) : "1.0";
                c.translateRaw = t.size() > 4 ? t.get(4) : "0.0";
                c.loRaw = t.size() > 5 ? t.get(5) : "0";
                c.hiRaw = t.size() > 6 ? t.get(6) : "0";
                c.digits = t.size() > 7 ? parseInt(t.get(7), 0) : 0;
                pcScalars.put(key, c);
            } else {
                List<String> values = new ArrayList<>();
                for (int i = 1; i < t.size(); i++) {
                    if (!t.get(i).isBlank()) {
                        values.add(t.get(i));
                    }
                }
                pcLists.put(key, values);
            }
        }
    }

    /** [ConstantsExtensions] defaultValue = name, value[, value...] */
    private void parseDefaults(Ini.Document doc) {
        for (Ini.Line line : doc.section("ConstantsExtensions")) {
            if (!line.key().equalsIgnoreCase("defaultValue")) {
                continue;
            }
            List<String> t = Ini.strings(line.value());
            if (t.size() < 2) {
                continue;
            }
            String name = t.get(0).trim();
            List<String> values = new ArrayList<>();
            for (int i = 1; i < t.size(); i++) {
                if (!t.get(i).isBlank()) {
                    values.add(t.get(i).trim());
                }
            }
            defaults.put(name, values);
        }
    }

    private void parseOutputChannels(Ini.Document doc) {
        ochBlockSize = parseInt(unq(doc.str("OutputChannels", "ochBlockSize", "0")), 0);
        ochGetCommand = unq(doc.str("OutputChannels", "ochGetCommand", ""));
        for (Ini.Line line : doc.section("OutputChannels")) {
            String key = line.key();
            if (key.equalsIgnoreCase("ochBlockSize") || key.equalsIgnoreCase("ochGetCommand")
                    || key.equalsIgnoreCase("ochGetCommand2")) {
                continue;
            }
            String rawValue = line.value().trim();
            if (rawValue.startsWith("{")) {
                // Derived output channel: name = { expression }, "units"
                Channel c = new Channel();
                c.name = key;
                c.cls = "expr";
                int open = rawValue.indexOf('{');
                int close = rawValue.lastIndexOf('}');
                c.expr = close > open ? rawValue.substring(open + 1, close).trim() : rawValue;
                String rest = close >= 0 ? rawValue.substring(close + 1) : "";
                rest = rest.replaceFirst("^\\s*,", "").trim();
                c.units = unq(rest);
                channels.put(key, c);
                continue;
            }
            List<String> t = Ini.strings(rawValue);
            if (t.size() < 3) {
                continue;
            }
            Channel c = new Channel();
            c.name = key;
            c.cls = t.get(0).toLowerCase(Locale.ROOT);
            c.type = t.get(1).toUpperCase(Locale.ROOT);
            c.offset = parseInt(t.get(2), 0);
            if (c.isBits()) {
                int[] r = parseRange(t.size() > 3 ? t.get(3) : "[0:0]");
                c.bitLo = r[0];
                c.bitHi = r[1];
                c.digits = 0;
            } else {
                c.units = t.size() > 3 ? t.get(3) : "";
                c.scaleRaw = t.size() > 4 ? t.get(4) : "1.0";
                c.translateRaw = t.size() > 5 ? t.get(5) : "0.0";
                c.digits = t.size() > 6 ? parseInt(t.get(6), 2) : 2;
            }
            channels.put(key, c);
        }
    }

    private void parseMenus(Ini.Document doc) {
        String currentSet = "main";
        Menu currentMenu = null;
        MenuEntry currentGroup = null;
        for (Ini.Line line : doc.section("Menu")) {
            String key = line.key().toLowerCase(Locale.ROOT);
            String raw = line.value();
            switch (key) {
                case "menudialog" -> {
                    currentSet = Ini.firstToken(raw);
                    menuSets.computeIfAbsent(currentSet, k -> new ArrayList<>());
                }
                case "menu" -> {
                    String label = firstString(raw);
                    currentMenu = null;
                    for (Menu m : menuSets.computeIfAbsent(currentSet, k -> new ArrayList<>())) {
                        if (m.label.equalsIgnoreCase(label)) {
                            currentMenu = m;
                            break;
                        }
                    }
                    if (currentMenu == null) {
                        currentMenu = new Menu();
                        currentMenu.label = label;
                        menuSets.get(currentSet).add(currentMenu);
                    }
                    currentGroup = null;
                }
                case "groupmenu" -> {
                    if (currentMenu != null) {
                        currentGroup = new MenuEntry();
                        currentGroup.kind = MenuEntry.Kind.GROUP;
                        currentGroup.label = firstString(raw);
                        currentMenu.entries.add(currentGroup);
                    }
                }
                case "groupchildmenu" -> {
                    if (currentGroup != null) {
                        currentGroup.children.add(menuEntry(raw, MenuEntry.Kind.DIALOG));
                    }
                }
                case "submenu" -> {
                    if (currentMenu != null) {
                        currentGroup = null;
                        MenuEntry e = menuEntry(raw, MenuEntry.Kind.DIALOG);
                        if (e.id.toLowerCase(Locale.ROOT).startsWith("std_")) {
                            e.kind = MenuEntry.Kind.STD;
                            if (e.id.equalsIgnoreCase("std_separator")) {
                                e.kind = MenuEntry.Kind.SEPARATOR;
                            }
                        }
                        currentMenu.entries.add(e);
                    }
                }
                default -> {
                }
            }
        }
    }

    private MenuEntry menuEntry(String raw, MenuEntry.Kind kind) {
        MenuEntry e = new MenuEntry();
        e.kind = kind;
        String rest = raw.trim();
        int q = indexOfQuote(rest);
        String id;
        String label = "";
        String after = "";
        if (q >= 0) {
            id = rest.substring(0, q).trim().replaceAll(",$", "").trim();
            int end = closingQuote(rest, q);
            label = unescape(rest.substring(q + 1, end));
            after = end + 1 < rest.length() ? rest.substring(end + 1).trim() : "";
            if (after.startsWith(",")) {
                after = after.substring(1).trim();
            }
        } else {
            int comma = rest.indexOf(',');
            id = comma >= 0 ? rest.substring(0, comma).trim() : rest;
            after = comma >= 0 ? rest.substring(comma + 1).trim() : "";
        }
        e.id = id;
        e.label = label.isEmpty() ? id : label;
        for (String tok : Ini.strings(after)) {
            String t = tok.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("{")) {
                e.vis = t;
            } else if (t.matches("-?\\d+")) {
                e.page = t;
            } else if (e.label.isEmpty() || e.label.equals(e.id)) {
                e.label = t;
            }
        }
        return e;
    }

    private void parseDialogs(Ini.Document doc) {
        for (Ini.Line line : doc.section("UserDefined")) {
            if (!line.key().equalsIgnoreCase("dialog")) {
                continue;
            }
            List<String> t = Ini.strings(line.value());
            if (t.isEmpty()) {
                continue;
            }
            Dialog d = new Dialog();
            d.name = t.get(0);
            d.title = t.size() > 1 ? t.get(1) : "";
            d.layout = t.size() > 2 ? t.get(2) : "yAxis";
            dialogs.put(d.name, d);
            dialogList.add(d);
        }
        // Attach children to the dialog they follow.
        Dialog current = null;
        for (Ini.Line line : doc.section("UserDefined")) {
            String key = line.key();
            String value = line.value();
            if (key.equalsIgnoreCase("dialog")) {
                current = dialogs.get(Ini.strings(value).get(0));
                continue;
            }
            if (key.equalsIgnoreCase("help")) {
                List<String> t = Ini.strings(value);
                if (t.isEmpty()) {
                    current = null;
                    continue;
                }
                Dialog h = new Dialog();
                h.name = t.get(0);
                h.title = t.size() > 1 ? t.get(1) : "Help";
                dialogs.put(h.name, h);
                dialogList.add(h);
                current = h;
                continue;
            }
            if (current == null) {
                continue;
            }
            switch (key.toLowerCase(Locale.ROOT)) {
                case "topichelp" -> current.topicHelp = firstString(value);
                case "webhelp" -> {
                    Item it = new Item();
                    it.type = Item.Type.COMMAND;
                    it.label = "Web Help";
                    it.command = "url:" + unq(firstString(value));
                    current.items.add(it);
                }
                case "panel" -> {
                    Item it = new Item();
                    it.type = Item.Type.PANEL;
                    List<String> t = Ini.strings(value);
                    it.constant = t.isEmpty() ? "" : t.get(0);
                    for (int i = 1; i < t.size(); i++) {
                        String token = t.get(i).trim();
                        if (token.startsWith("{")) it.vis = token;
                        else if (!token.isEmpty()) it.constraint = token;
                    }
                    current.items.add(it);
                }
                case "field" -> current.items.add(fieldItem(value));
                case "displayonlyfield" -> {
                    Item it = fieldItem(value);
                    it.type = Item.Type.DISPLAY_ONLY;
                    current.items.add(it);
                }
                case "commandbutton" -> {
                    List<String> t = Ini.strings(value);
                    Item it = new Item();
                    it.type = Item.Type.COMMAND;
                    it.label = t.size() > 0 ? t.get(0) : "";
                    it.command = t.size() > 1 ? t.get(1) : "";
                    if (t.size() > 2) {
                        it.vis = t.get(2);
                    }
                    current.items.add(it);
                }
                case "slider" -> {
                    List<String> t = Ini.strings(value);
                    Item it = new Item();
                    it.type = Item.Type.SLIDER;
                    it.label = t.size() > 0 ? t.get(0) : "";
                    it.constant = t.size() > 1 ? t.get(1) : "";
                    it.constraint = t.size() > 2 ? t.get(2) : "horizontal";
                    if (t.size() > 3) {
                        it.vis = t.get(3);
                    }
                    current.items.add(it);
                }
                case "indicator" -> current.items.add(indicatorItem(value));
                case "gauge" -> {
                    Item it = new Item();
                    it.type = Item.Type.GAUGE;
                    it.constant = Ini.firstToken(value);
                    current.items.add(it);
                }
                case "text" -> {
                    Item it = new Item();
                    it.type = Item.Type.TEXT;
                    it.label = unq(firstString(value));
                    current.items.add(it);
                }
                case "settingsselector", "settingselector" -> {
                    Item it = new Item();
                    it.type = Item.Type.SETTING_SELECTOR;
                    List<String> t = Ini.strings(value);
                    it.label = t.isEmpty() ? "" : t.get(0);
                    if (t.size() > 1) {
                        it.vis = t.get(1);
                    }
                    current.items.add(it);
                }
                case "livegraph" -> {
                    List<String> t = Ini.strings(value);
                    Item it = new Item();
                    it.type = Item.Type.LIVE_GRAPH;
                    it.graphId = t.size() > 0 ? t.get(0) : "";
                    it.label = t.size() > 1 ? t.get(1) : it.graphId;
                    current.items.add(it);
                }
                case "graphline" -> {
                    List<String> t = Ini.strings(value);
                    Item it = new Item();
                    it.type = Item.Type.GRAPH_LINE;
                    it.constant = t.size() > 0 ? t.get(0) : "";
                    it.values.addAll(t);
                    current.items.add(it);
                }
                default -> {
                }
            }
        }
    }

    private Item fieldItem(String value) {
        Item it = new Item();
        it.type = Item.Type.FIELD;
        String rest = value.trim();
        String label = "";
        String after = rest;
        if (rest.startsWith("\"") || rest.startsWith("!")) {
            boolean bang = rest.startsWith("!");
            int start = bang ? 1 : 0;
            if (start < rest.length() && rest.charAt(start) == '"') {
                int end = closingQuote(rest, start);
                label = unescape(rest.substring(start + 1, end));
                after = end + 1 < rest.length() ? rest.substring(end + 1).trim() : "";
                if (after.startsWith(",")) {
                    after = after.substring(1).trim();
                }
                if (bang) {
                    label = "!" + label;
                }
            }
        }
        List<String> t = Ini.strings(after);
        String constant = t.isEmpty() ? "" : t.get(0);
        if (constant.startsWith("{")) {
            constant = "";
        }
        it.label = label;
        it.constant = constant;
        List<String> exprs = new ArrayList<>();
        for (int i = 1; i < t.size(); i++) {
            String tok = t.get(i).trim();
            if (tok.startsWith("{")) {
                exprs.add(tok);
            }
        }
        if (exprs.size() == 1) {
            it.vis = exprs.get(0);
        } else if (exprs.size() >= 2) {
            it.enable = exprs.get(0);
            it.vis = exprs.get(1);
        }
        if (it.label.isEmpty()) {
            it.label = constant;
        }
        return it;
    }

    private Item indicatorItem(String value) {
        Item it = new Item();
        it.type = Item.Type.INDICATOR;
        String rest = value.trim();
        if (rest.startsWith("{")) {
            int depth = 0;
            int end = 0;
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            it.vis = rest.substring(0, end + 1);
            rest = rest.substring(end + 1).trim();
            if (rest.startsWith(",")) {
                rest = rest.substring(1).trim();
            }
        }
        it.values.addAll(Ini.strings(rest));
        return it;
    }

    private void parseTables(Ini.Document doc) {
        Table current = null;
        for (Ini.Line line : doc.section("TableEditor")) {
            String key = line.key();
            if (key.equalsIgnoreCase("table")) {
                List<String> t = Ini.strings(line.value());
                if (t.size() < 3) {
                    continue;
                }
                Table tb = new Table();
                tb.id = t.get(0);
                tb.map3dId = t.get(1);
                tb.title = t.get(2);
                tb.page = t.size() > 3 ? parseInt(t.get(3), 1) : 1;
                tables.put(tb.id, tb);
                current = tb;
                continue;
            }
            if (current == null) {
                continue;
            }
            String v = line.value().trim();
            switch (key.toLowerCase(Locale.ROOT)) {
                case "xaxis", "yaxis" -> {
                }
                case "xbins" -> {
                    current.xBins = Ini.firstToken(v);
                    if (current.xyLabels.isEmpty()) current.xLabel = current.xBins;
                }
                case "ybins" -> {
                    current.yBins = Ini.firstToken(v);
                    if (current.xyLabels.size() < 2) current.yLabel = current.yBins;
                }
                case "zbins" -> current.zBins = Ini.firstToken(v);
                case "xylabel", "xylabels" -> {
                    current.xyLabels.clear();
                    current.xyLabels.addAll(Ini.strings(v));
                    if (!current.xyLabels.isEmpty()) current.xLabel = current.xyLabels.get(0);
                    if (current.xyLabels.size() > 1) current.yLabel = current.xyLabels.get(1);
                }
                case "topichelp" -> current.topicHelp = firstString(v);
                case "gridheight" -> current.gridHeight = parseDouble(Ini.firstToken(v), 2);
                case "gridorient" -> {
                    for (String s : Ini.strings(v)) {
                        current.gridOrient.add(parseDouble(s, 0));
                    }
                }
                case "updownlabel" -> current.upDownLabels.addAll(Ini.strings(v));
                default -> {
                }
            }
        }
    }

    private void parseCurves(Ini.Document doc) {
        Curve current = null;
        for (Ini.Line line : doc.section("CurveEditor")) {
            String key = line.key();
            String v = line.value().trim();
            if (key.equalsIgnoreCase("curve")) {
                List<String> t = Ini.strings(v);
                if (t.isEmpty()) {
                    continue;
                }
                Curve c = new Curve();
                c.id = t.get(0);
                c.title = t.size() > 1 ? t.get(1) : c.id;
                curves.put(c.id, c);
                current = c;
                continue;
            }
            if (current == null) {
                continue;
            }
            switch (key.toLowerCase(Locale.ROOT)) {
                case "columnlabel" -> current.columnLabels.addAll(Ini.strings(v));
                case "xbins" -> current.xBins = Ini.firstToken(v);
                case "ybins" -> current.yBins = Ini.firstToken(v);
                case "topichelp" -> current.topicHelp = firstString(v);
                case "xaxis" -> {
                    List<String> t = Ini.strings(v);
                    current.xAxis = new double[] {parseDouble(t.get(0), 0),
                            parseDouble(t.size() > 1 ? t.get(1) : "100", 100),
                            parseDouble(t.size() > 2 ? t.get(2) : "6", 6)};
                }
                case "yaxis" -> {
                    List<String> t = Ini.strings(v);
                    current.yAxis = new double[] {parseDouble(t.get(0), 0),
                            parseDouble(t.size() > 1 ? t.get(1) : "100", 100),
                            parseDouble(t.size() > 2 ? t.get(2) : "6", 6)};
                }
                default -> {
                }
            }
        }
    }

    private void parseGauges(Ini.Document doc) {
        String category = "Main";
        for (Ini.Line line : doc.section("GaugeConfigurations")) {
            if (line.key().equalsIgnoreCase("gaugeCategory")) {
                category = firstString(line.value());
                if (!gaugeCategories.contains(category)) {
                    gaugeCategories.add(category);
                }
                continue;
            }
            List<String> t = Ini.strings(line.value());
            if (t.size() < 3) {
                continue;
            }
            Gauge g = new Gauge();
            g.category = category;
            g.name = line.key();
            g.var = t.get(0);
            g.title = t.get(1);
            g.unitsRaw = t.get(2);
            g.loRaw = t.size() > 3 ? t.get(3) : "0";
            g.hiRaw = t.size() > 4 ? t.get(4) : "100";
            g.loDRaw = t.size() > 5 ? t.get(5) : "-1";
            g.loWRaw = t.size() > 6 ? t.get(6) : "-1";
            g.hiWRaw = t.size() > 7 ? t.get(7) : "999";
            g.hiDRaw = t.size() > 8 ? t.get(8) : "999";
            g.vd = t.size() > 9 ? parseInt(t.get(9), 0) : 0;
            g.ld = t.size() > 10 ? parseInt(t.get(10), 0) : 0;
            gauges.put(g.name, g);
        }
    }

    private void parseFrontPage(Ini.Document doc) {
        for (Ini.Line line : doc.section("FrontPage")) {
            String key = line.key();
            if (key.toLowerCase(Locale.ROOT).startsWith("gauge")) {
                String name = Ini.firstToken(line.value());
                if (gauges.containsKey(name)) {
                    frontGauges.add(gauges.get(name));
                }
            } else if (key.equalsIgnoreCase("indicator")) {
                Item it = indicatorItem(line.value());
                Indicator ind = new Indicator();
                ind.expr = it.vis;
                if (it.values.size() > 0) {
                    ind.offLabel = it.values.get(0);
                }
                if (it.values.size() > 1) {
                    ind.onLabel = it.values.get(1);
                }
                if (it.values.size() > 2) {
                    ind.offBg = it.values.get(2);
                }
                if (it.values.size() > 3) {
                    ind.offFg = it.values.get(3);
                }
                if (it.values.size() > 4) {
                    ind.onBg = it.values.get(4);
                }
                if (it.values.size() > 5) {
                    ind.onFg = it.values.get(5);
                }
                indicators.add(ind);
            }
        }
    }

    private void parseDatalog(Ini.Document doc) {
        for (Ini.Line line : doc.section("Datalog")) {
            if (!line.key().equalsIgnoreCase("entry")) {
                continue;
            }
            List<String> t = Ini.strings(line.value());
            if (t.size() < 2) {
                continue;
            }
            DatalogEntry e = new DatalogEntry();
            e.channel = t.get(0);
            e.label = t.get(1);
            e.type = t.size() > 2 ? t.get(2) : "float";
            e.format = t.size() > 3 ? t.get(3) : "%.3f";
            e.condition = t.size() > 4 ? t.get(4) : "";
            datalog.add(e);
        }
    }

    private void parseTools(Ini.Document doc) {
        for (Ini.Line line : doc.section("Tools")) {
            if (!line.key().equalsIgnoreCase("addTool")) {
                continue;
            }
            List<String> t = Ini.strings(line.value());
            if (t.size() >= 2) {
                String table = t.size() > 2 ? t.get(2) : "";
                tools.put(t.get(0), new String[] {t.get(1), table});
            }
        }
    }

    private void parseControllerCommands(Ini.Document doc) {
        for (Ini.Line line : doc.section("ControllerCommands")) {
            List<String> cmds = new ArrayList<>(Ini.strings(line.value()));
            controllerCommands.put(line.key(), cmds);
        }
    }

    private void parseHelp(Ini.Document doc) {
        for (Ini.Line line : doc.section("Help")) {
            if (!line.key().equalsIgnoreCase("help")) {
                continue;
            }
            List<String> t = Ini.strings(line.value());
            if (t.size() < 1) {
                continue;
            }
            String target = t.size() > 1 ? t.get(1) : "";
            helpLinks.put(t.get(0), target.trim());
        }
    }

    private void parseSettingHelp(Ini.Document doc) {
        for (Ini.Line line : doc.section("SettingContextHelp")) {
            settingHelp.put(line.key(), firstString(line.value()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private Constant parseConstant(String name, String value, int page) {
        List<String> t = Ini.strings(value);
        if (t.size() < 2) {
            return null;
        }
        Constant c = new Constant();
        c.name = name;
        c.page = page;
        c.cls = t.get(0).toLowerCase(Locale.ROOT);
        c.type = t.get(1).toUpperCase(Locale.ROOT);
        if (c.cls.equals("bits")) {
            c.offset = t.size() > 2 ? parseInt(t.get(2), 0) : 0;
            int optStart = 3;
            if (t.size() > 3 && t.get(3).startsWith("[")) {
                int[] r = parseRange(t.get(3));
                c.bitLo = r[0];
                c.bitHi = r[1];
                optStart = 4;
            }
            for (int i = optStart; i < t.size(); i++) {
                String opt = t.get(i).trim();
                if (opt.isEmpty() || opt.equals("{}")) {
                    continue;
                }
                if (opt.startsWith("$")) {
                    c.options.addAll(pcLists.getOrDefault(opt.substring(1), List.of()));
                } else if (opt.startsWith("{")) {
                    c.options.add(opt);
                } else {
                    c.options.add(opt);
                }
            }
            return c;
        }
        boolean array = c.cls.equals("array");
        int idx = 2;
        c.offset = t.size() > idx ? parseInt(t.get(idx), 0) : 0;
        idx++;
        if (array) {
            if (t.size() > idx && t.get(idx).startsWith("[")) {
                c.shape = parseShape(t.get(idx));
                idx++;
            } else {
                c.shape = new int[] {1};
            }
        }
        c.units = t.size() > idx ? t.get(idx) : "";
        idx++;
        c.scaleRaw = t.size() > idx ? t.get(idx) : "1.0";
        idx++;
        c.translateRaw = t.size() > idx ? t.get(idx) : "0.0";
        idx++;
        c.loRaw = t.size() > idx ? t.get(idx) : "0";
        idx++;
        c.hiRaw = t.size() > idx ? t.get(idx) : "0";
        idx++;
        c.digits = t.size() > idx ? parseInt(t.get(idx), 0) : 0;
        return c;
    }

    private String resolve(String token) {
        String s = token.trim();
        if (s.startsWith("$")) {
            List<String> list = pcLists.get(s.substring(1));
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        }
        return s;
    }

    static int[] parseShape(String token) {
        String s = token.replace("[", "").replace("]", "").trim();
        String[] parts = s.split("[xX,]");
        int[] shape = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            shape[i] = parseInt(parts[i].trim(), 1);
        }
        return shape;
    }

    static int[] parseRange(String token) {
        String s = token.replace("[", "").replace("]", "").trim();
        int idx = s.indexOf(':');
        if (idx < 0) {
            int v = parseInt(s, 0);
            return new int[] {v, v};
        }
        return new int[] {parseInt(s.substring(0, idx), 0), parseInt(s.substring(idx + 1), 7)};
    }

    private static int indexOfQuote(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int closingQuote(String s, int start) {
        for (int i = start + 1; i < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                i++;
            } else if (s.charAt(i) == '"') {
                return i;
            }
        }
        return s.length() - 1;
    }

    private static String firstString(String raw) {
        String s = raw.trim();
        int q = indexOfQuote(s);
        if (q >= 0) {
            int end = closingQuote(s, q);
            return unescape(s.substring(q + 1, end));
        }
        return Ini.firstToken(s);
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String unq(String s) {
        return Ini.unquote(s);
    }

    private static int parseInt(String s, int def) {
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    public Set<String> allConstantNames() {
        return new LinkedHashSet<>(constants.keySet());
    }

    public static Path findIni(Path... candidates) {
        for (Path p : candidates) {
            if (p != null && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
