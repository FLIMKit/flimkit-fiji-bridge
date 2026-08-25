package io.github.flimkit.fiji;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;

import java.util.ArrayList;
import java.util.List;

public class GeoJson {

    private GeoJson() {}

    public static String toFeatureCollection(List<Roi> rois) {
        var features = new JsonArray();
        for (int i = 0; i < rois.size(); i++) {
            Roi roi = rois.get(i);
            FloatPolygon polygon = roi.getFloatPolygon();
            if (polygon == null || polygon.npoints < 3)
                continue;
            var ring = new JsonArray();
            for (int p = 0; p < polygon.npoints; p++)
                ring.add(pair(polygon.xpoints[p], polygon.ypoints[p]));
            ring.add(pair(polygon.xpoints[0], polygon.ypoints[0]));
            var coordinates = new JsonArray();
            coordinates.add(ring);
            var geometry = new JsonObject();
            geometry.addProperty("type", "Polygon");
            geometry.add("coordinates", coordinates);
            var properties = new JsonObject();
            String name = roi.getName();
            properties.addProperty("name",
                    name == null || name.isEmpty() ? "Fiji region " + (i + 1) : name);
            var feature = new JsonObject();
            feature.addProperty("type", "Feature");
            feature.add("properties", properties);
            feature.add("geometry", geometry);
            features.add(feature);
        }
        var body = new JsonObject();
        body.addProperty("type", "FeatureCollection");
        body.add("features", features);
        return body.toString();
    }

    private static JsonArray pair(double x, double y) {
        var point = new JsonArray();
        point.add(x);
        point.add(y);
        return point;
    }

    public static List<Roi> toRois(String text) {
        JsonObject body;
        try {
            body = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("could not read the GeoJSON: " + e.getMessage());
        }
        if (!body.has("type") || !"FeatureCollection".equals(body.get("type").getAsString()))
            throw new IllegalArgumentException(
                    "expected a GeoJSON FeatureCollection, got "
                            + (body.has("type") ? body.get("type").getAsString() : "no type"));
        var found = new ArrayList<Roi>();
        var features = body.getAsJsonArray("features");
        for (int i = 0; i < features.size(); i++) {
            var feature = features.get(i).getAsJsonObject();
            var geometry = feature.getAsJsonObject("geometry");
            String kind = geometry.get("type").getAsString();
            if (!"Polygon".equals(kind))
                throw new IllegalArgumentException(
                        "the Fiji bridge only carries Polygon geometries, got " + kind);
            var ring = geometry.getAsJsonArray("coordinates").get(0).getAsJsonArray();
            int count = ring.size();
            if (count > 1 && ring.get(0).toString().equals(ring.get(count - 1).toString()))
                count--;
            float[] xs = new float[count];
            float[] ys = new float[count];
            for (int p = 0; p < count; p++) {
                var point = ring.get(p).getAsJsonArray();
                xs[p] = point.get(0).getAsFloat();
                ys[p] = point.get(1).getAsFloat();
            }
            var roi = new PolygonRoi(xs, ys, count, Roi.POLYGON);
            var properties = feature.getAsJsonObject("properties");
            if (properties != null && properties.has("name"))
                roi.setName(properties.get("name").getAsString());
            found.add(roi);
        }
        return found;
    }
}
