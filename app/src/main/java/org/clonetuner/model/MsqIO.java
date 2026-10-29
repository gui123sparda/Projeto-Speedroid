package org.clonetuner.model;

import org.clonetuner.ini.IniModel;
import org.clonetuner.ini.IniModel.Constant;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Reads and writes TunerStudio MSQ 5.0 files with named constants per page.
 * Older CloneTuner files containing a raw hex page are still accepted.
 */
public final class MsqIO {

    private MsqIO() {
    }

    public static void save(Path path, Ecu ecu) throws Exception {
        IniModel ini = ecu.ini();
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.newDocument();

        Element root = doc.createElement("msq");
        root.setAttribute("xmlns", "http://www.msefi.com/msq");
        doc.appendChild(root);

        Element bib = doc.createElement("bibliography");
        bib.setAttribute("author", "CloneTuner");
        bib.setAttribute("fileFormat", "5.0");
        root.appendChild(bib);

        Element info = doc.createElement("versionInfo");
        info.setAttribute("signature", ini.signature);
        info.setAttribute("firmwareInfo", ini.versionInfo);
        info.setAttribute("nPages", String.valueOf(ini.nPages));
        info.setAttribute("fileFormat", "5.0");
        root.appendChild(info);

        Element pcPage = doc.createElement("page");
        root.appendChild(pcPage);
        for (Constant c : ini.pcScalars.values()) appendConstant(doc, pcPage, ecu, c, "pcVariable");
        for (Constant c : ini.pcBits.values()) appendConstant(doc, pcPage, ecu, c, "pcVariable");

        for (int i = 0; i < ini.nPages; i++) {
            Element p = doc.createElement("page");
            p.setAttribute("number", String.valueOf(i));
            p.setAttribute("size", String.valueOf(ecu.page(i + 1).length));
            for (Constant c : ini.constants.values()) {
                if (c.page == i + 1) appendConstant(doc, p, ecu, c, "constant");
            }
            root.appendChild(p);
        }

        Transformer tr = TransformerFactory.newInstance().newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        tr.transform(new DOMSource(doc), new StreamResult(path.toFile()));
    }

    /** Loads page data into {@code ecu}; returns the tune signature found in the file (may be blank). */
    public static String load(Path path, Ecu ecu) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Android's parser does not implement Xerces-specific feature switches.
        // Parse bounded UTF-8 text, reject DTDs, and prohibit external resolution.
        if (Files.size(path) > 8 * 1024 * 1024) throw new IOException("MSQ exceeds 8 MiB");
        String xml = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        if (xml.toUpperCase(java.util.Locale.ROOT).contains("<!DOCTYPE"))
            throw new IOException("DOCTYPE is not permitted in MSQ files");
        DocumentBuilder db = factory.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) -> {
            throw new org.xml.sax.SAXException("External entities are not permitted");
        });
        Document doc = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        doc.getDocumentElement().normalize();

        String signature = "";
        NodeList infos = doc.getElementsByTagName("versionInfo");
        if (infos.getLength() > 0) {
            signature = ((Element) infos.item(0)).getAttribute("signature");
        }
        if (!signature.isBlank() && !signature.equalsIgnoreCase(ecu.ini().signature)) {
            throw new IOException("Tune signature '" + signature + "' does not match INI '" + ecu.ini().signature + "'");
        }

        NodeList pages = doc.getElementsByTagName("page");
        int loaded = 0;
        for (int i = 0; i < pages.getLength(); i++) {
            Node n = pages.item(i);
            if (!(n instanceof Element el)) {
                continue;
            }
            NodeList constants = el.getElementsByTagName("constant");
            NodeList pcVariables = el.getElementsByTagName("pcVariable");
            if (constants.getLength() + pcVariables.getLength() > 0) {
                int pageNumber = parseInt(el.getAttribute("number"), -1) + 1;
                loaded += loadConstants(constants, ecu, pageNumber);
                loaded += loadConstants(pcVariables, ecu, 0);
            } else if (el.hasAttribute("number")) {
                // Legacy CloneTuner MSQ: 1-based page number and raw hex bytes.
                String raw = el.hasAttribute("data") ? el.getAttribute("data") : el.getTextContent();
                byte[] data = bytes(raw);
                int number = parseInt(el.getAttribute("number"), -1);
                if (number >= 1 && number <= ecu.allPages().length && data.length == ecu.page(number).length) {
                    ecu.replacePage(number, data);
                    ecu.markDirty(number - 1);
                    loaded++;
                }
            }
        }
        if (loaded == 0) {
            throw new IOException("No page data found in " + path.getFileName());
        }
        ecu.fireChanged();
        return signature;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static void appendConstant(Document doc, Element page, Ecu ecu, Constant c, String tag) {
        Element element = doc.createElement(tag);
        element.setAttribute("name", c.name);
        if (c.units != null && !c.units.isBlank()) element.setAttribute("units", c.units);
        if (!c.isBits()) element.setAttribute("digits", String.valueOf(c.digits));
        if (c.isArray()) {
            if (c.rows() > 1) element.setAttribute("rows", String.valueOf(c.rows()));
            if (c.cols() > 1) element.setAttribute("cols", String.valueOf(c.cols()));
            StringBuilder values = new StringBuilder("\n");
            int cols = c.cols();
            for (int i = 0; i < c.length(); i++) {
                if (i % cols == 0) values.append("         ");
                values.append(number(ecu.get(c, i))).append(' ');
                if (i % cols == cols - 1) values.append('\n');
            }
            element.setTextContent(values.toString());
        } else if (c.isBits()) {
            element.setTextContent(ecu.optionLabel(c, (int) Math.round(ecu.get(c, 0))));
        } else {
            element.setTextContent(number(ecu.get(c, 0)));
        }
        page.appendChild(element);
    }

    private static String number(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static int loadConstants(NodeList nodes, Ecu ecu, int page) {
        int loaded = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element el)) continue;
            Constant c = ecu.constant(el.getAttribute("name"));
            if (c == null || c.page != page) continue;
            String content = el.getTextContent().trim();
            try {
                if (c.isArray()) {
                    String[] tokens = content.split("[\\s,]+");
                    if (tokens.length != c.length()) continue;
                    double[] values = new double[tokens.length];
                    for (int j = 0; j < tokens.length; j++) values[j] = Double.parseDouble(tokens[j]);
                    ecu.setArray(c.name, values);
                } else if (c.isBits()) {
                    int choice = c.options.indexOf(content);
                    ecu.set(c.name, choice >= 0 ? choice : Double.parseDouble(content));
                } else {
                    ecu.set(c.name, Double.parseDouble(content));
                }
                loaded++;
            } catch (NumberFormatException ignored) {
                // An unknown constant value must not overwrite a valid tune value.
            }
        }
        return loaded;
    }

    private static byte[] bytes(String raw) {
        String s = raw.replaceAll("\\s+", "").trim();
        if (s.isEmpty()) {
            return new byte[0];
        }
        if (s.matches("[0-9a-fA-F]*") && s.length() % 2 == 0) {
            byte[] out = new byte[s.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        try {
            return Base64.getMimeDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    public static Path defaultDir() {
        Path home = java.nio.file.Paths.get(System.getProperty("user.home", "."));
        Path dir = home.resolve("Documents").resolve("CloneTuner");
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                return home;
            }
        }
        return dir;
    }
}
