package org.clonetuner.dashboard;

import static org.clonetuner.dashboard.DashboardDefinition.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public final class DashboardReader {
    private DashboardReader() {}
    public static final Set<String> PAINTERS = Set.of("Analog Gauge", "Asymetric Sweep Gauge",
        "Basic Readout", "Horizontal Bar Gauge", "Horizontal Line Gauge", "Basic Rectangle Indicator");

    public static DashboardDefinition read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Android does not implement Xerces feature switches. Reject DTDs before parsing.
            if (Files.size(path) > 16 * 1024 * 1024) throw new IOException("Dashboard exceeds 16 MiB");
            String xml = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            if (xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) throw new IOException("DOCTYPE is not permitted");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entities are not permitted"); });
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            var doc = builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
            if (!"dsh".equals(doc.getDocumentElement().getLocalName()))
                throw new IOException("Expected dsh root");
            Element version = first(doc, "versionInfo"), cluster = first(doc, "gaugeCluster");
            if (cluster == null || version == null || !"3.0".equals(version.getAttribute("fileFormat"))) {
                throw new IOException("Missing cluster or unsupported dashboard format");
            }
            Map<String, String> clusterProps = new LinkedHashMap<>();
            for (int i = 0; i < cluster.getAttributes().getLength(); i++) {
                Node a = cluster.getAttributes().item(i);
                clusterProps.put(a.getNodeName(), a.getNodeValue());
            }
            List<Component> components = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            Map<String, Resource> resources = new LinkedHashMap<>();
            List<String> diagnostics = new ArrayList<>();
            for (Node node = cluster.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element el))
                    continue;
                if ("imageFile".equals(el.getLocalName())) {
                    byte[] bytes = Base64.getDecoder().decode(el.getTextContent().replaceAll("\\s", ""));
                    resources.put(el.getAttribute("imageId"),
                        new Resource(el.getAttribute("fileName"), el.getAttribute("type"), bytes));
                } else if ("dashComp".equals(el.getLocalName())) {
                    Map<String, String> props = new LinkedHashMap<>();
                    for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
                        if (n instanceof Element e)
                            props.put(e.getLocalName(), e.getTextContent());
                    }
                    String id = props.getOrDefault("Id", "");
                    if (id.isBlank())
                        id = "component-" + components.size();
                    if (!ids.add(id))
                        throw new IOException(id + ": duplicate component ID");
                    for (String key : List.of("RelativeX", "RelativeY", "RelativeWidth", "RelativeHeight")) {
                        double v = finite(props.getOrDefault(key, "NaN"), id, key);
                        if ((key.endsWith("Width") || key.endsWith("Height")) && v <= 0)
                            throw new IOException(id + ": invalid " + key);
                    }
                    for (String key :
                        List.of("StartAngle", "SweepAngle", "SweepBeginDegree", "FaceAngle", "BorderWidth",
                            "MajorTicks", "MinorTicks", "LabelDigits", "FontSizeAdjustment")) {
                        if (props.containsKey(key))
                            finite(props.get(key), id, key);
                    }
                    String painter = props.getOrDefault("GaugePainter", props.getOrDefault("Painter", ""));
                    if (!PAINTERS.contains(painter))
                        diagnostics.add(id + ": unsupported painter " + painter);
                    components.add(new Component(id, painter, props));
                }
            }
            return new DashboardDefinition(path.getFileName().toString(),
                version.getAttribute("firmwareSignature"), clusterProps, components, resources, diagnostics);
        } catch (Exception e) {
            throw new IOException(path.getFileName() + ": " + e.getMessage(), e);
        }
    }
    private static double finite(String value, String id, String key) throws IOException {
        try {
            double number = Double.parseDouble(value);
            if (Double.isFinite(number))
                return number;
        } catch (NumberFormatException ignored) {
        }
        throw new IOException(id + ": invalid " + key + " (" + value + ")");
    }
    private static Element first(Document doc, String name) {
        NodeList nodes = doc.getElementsByTagNameNS("*", name);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }
}
