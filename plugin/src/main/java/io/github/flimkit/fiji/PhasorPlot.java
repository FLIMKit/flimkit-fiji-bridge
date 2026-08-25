package io.github.flimkit.fiji;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class PhasorPlot {

    private PhasorPlot() {}

    public static final double G_MIN = -0.05;
    public static final double G_MAX = 1.05;
    public static final double S_MIN = -0.05;
    public static final double S_MAX = 0.65;

    public static int[] decodeCounts(String base64) {
        byte[] raw = Base64.getDecoder().decode(base64);
        int[] counts = new int[raw.length / 4];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = (raw[i * 4] & 0xFF)
                    | ((raw[i * 4 + 1] & 0xFF) << 8)
                    | ((raw[i * 4 + 2] & 0xFF) << 16)
                    | ((raw[i * 4 + 3] & 0xFF) << 24);
        }
        return counts;
    }

    public static String describe(JsonObject entry) {
        int pixels = entry.get("n_pixels").getAsInt();
        var text = new StringBuilder(entry.get("id").getAsString())
                .append(": ").append(pixels).append(" px");
        if (pixels == 0)
            return text.toString();
        if (has(entry, "tau_phi_ns"))
            text.append(String.format("  tau_phi %.2f ns",
                    entry.get("tau_phi_ns").getAsDouble()));
        if (has(entry, "tau_mod_ns"))
            text.append(String.format("  tau_m %.2f ns",
                    entry.get("tau_mod_ns").getAsDouble()));
        if (has(entry, "mean_g") && has(entry, "mean_s"))
            text.append(String.format("  (G %.3f, S %.3f)",
                    entry.get("mean_g").getAsDouble(),
                    entry.get("mean_s").getAsDouble()));
        return text.toString();
    }

    private static boolean has(JsonObject entry, String key) {
        return entry.has(key) && !entry.get(key).isJsonNull();
    }

    public static String requestBody(List<Cursor> cursors, JsonObject options,
                                     boolean labels) {
        var body = new JsonObject();
        var array = new JsonArray();
        for (var cursor : cursors) {
            var entry = new JsonObject();
            entry.addProperty("id", cursor.id());
            if (cursor.vertices() != null) {
                entry.addProperty("type", "polygon");
                var vertices = new JsonArray();
                for (var vertex : cursor.vertices()) {
                    var pair = new JsonArray();
                    pair.add(vertex[0]);
                    pair.add(vertex[1]);
                    vertices.add(pair);
                }
                entry.add("vertices", vertices);
            }
            else {
                entry.addProperty("center_g", cursor.g());
                entry.addProperty("center_s", cursor.s());
                entry.addProperty("radius", cursor.radius());
            }
            array.add(entry);
        }
        body.add("cursors", array);
        body.addProperty("min_photons", 1.0);
        if (options != null && !options.entrySet().isEmpty())
            body.add("options", options);
        if (labels)
            body.addProperty("output", "labels");
        return body.toString();
    }

    public static String optionsQuery(JsonObject options) {
        if (options == null)
            return "";
        var parts = new ArrayList<String>();
        for (var key : options.keySet()) {
            parts.add(java.net.URLEncoder.encode(key,
                    java.nio.charset.StandardCharsets.UTF_8) + "="
                    + java.net.URLEncoder.encode(options.get(key).getAsString(),
                            java.nio.charset.StandardCharsets.UTF_8));
        }
        return String.join("&", parts);
    }

    public record Cursor(String id, double g, double s, double radius,
                         List<double[]> vertices) {

        public static Cursor ellipse(String id, double g, double s, double radius) {
            return new Cursor(id, g, s, radius, null);
        }

        public static Cursor polygon(String id, List<double[]> vertices) {
            return new Cursor(id, 0, 0, 0, vertices);
        }
    }
}
