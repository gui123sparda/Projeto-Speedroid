package org.clonetuner.dashboard;

import java.util.*;

/** Data from a .dash document; no reference application code is loaded. */
public record DashboardDefinition(String name, String firmwareSignature, Map<String, String> clusterProperties,
        List<Component> components, Map<String, Resource> resources, List<String> diagnostics) {
    public DashboardDefinition {
        clusterProperties = immutable(clusterProperties);
        components = List.copyOf(components);
        resources = immutable(resources);
        diagnostics = List.copyOf(diagnostics);
    }
    private static <K, V> Map<K, V> immutable(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
    public record Component(String id, String painter, Map<String, String> properties) {
        public Component {
            properties = immutable(properties);
        }
        public String text(String key) {
            String value = properties.getOrDefault(key, "");
            boolean reference = key.equals("OutputChannel") || key.endsWith("ImageFileName")
                || key.endsWith("ClickAction") || key.equals("FontFamily");
            return reference && "null".equals(value.strip()) ? "" : value;
        }
        public double number(String key, double fallback) {
            try {
                double n = Double.parseDouble(text(key));
                return Double.isFinite(n) ? n : fallback;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        public boolean flag(String key, boolean fallback) {
            return text(key).isBlank() ? fallback : Boolean.parseBoolean(text(key));
        }
    }
    public record Resource(String fileName, String type, byte[] bytes) {
        public Resource {
            bytes = bytes.clone();
        }
        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
