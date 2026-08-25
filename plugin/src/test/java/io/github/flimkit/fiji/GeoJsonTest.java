package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.gui.OvalRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoJsonTest {

    private static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static Roi triangle() {
        var roi = new PolygonRoi(
                new float[] {1.25f, 4.5f, 3.0f},
                new float[] {2.5f, 2.5f, 4.0f},
                3, Roi.POLYGON);
        roi.setName("Fiji polygon");
        return roi;
    }

    @Test
    void aPolygonBecomesAClosedRing() {
        var body = parse(GeoJson.toFeatureCollection(List.of(triangle())));

        assertEquals("FeatureCollection", body.get("type").getAsString());
        var feature = body.getAsJsonArray("features").get(0).getAsJsonObject();
        assertEquals("Fiji polygon",
                feature.getAsJsonObject("properties").get("name").getAsString());
        var ring = feature.getAsJsonObject("geometry")
                .getAsJsonArray("coordinates").get(0).getAsJsonArray();
        assertEquals(4, ring.size());
        assertEquals(ring.get(0).toString(), ring.get(3).toString());
    }

    @Test
    void theVerticesSurviveTheRoundTrip() {
        var back = GeoJson.toRois(GeoJson.toFeatureCollection(List.of(triangle())));

        assertEquals(1, back.size());
        var polygon = back.get(0).getFloatPolygon();
        assertEquals(3, polygon.npoints);
        assertEquals(1.25f, polygon.xpoints[0], 1e-6);
        assertEquals(2.5f, polygon.ypoints[0], 1e-6);
        assertEquals("Fiji polygon", back.get(0).getName());
    }

    @Test
    void anOvalDoesNotCollapseToItsBox() {
        var body = parse(GeoJson.toFeatureCollection(List.of(new OvalRoi(0, 0, 10, 10))));

        var ring = body.getAsJsonArray("features").get(0).getAsJsonObject()
                .getAsJsonObject("geometry").getAsJsonArray("coordinates")
                .get(0).getAsJsonArray();
        assertTrue(ring.size() > 5, "an oval traced as a box loses its shape");
    }

    @Test
    void anUnnamedRegionStillGetsAName() {
        var body = parse(GeoJson.toFeatureCollection(List.of(new Roi(0, 0, 2, 2))));

        assertEquals("Fiji region 1", body.getAsJsonArray("features").get(0)
                .getAsJsonObject().getAsJsonObject("properties").get("name").getAsString());
    }

    @Test
    void anEmptyListIsAnEmptyCollection() {
        assertEquals(0, parse(GeoJson.toFeatureCollection(List.of()))
                .getAsJsonArray("features").size());
    }

    @Test
    void onlyTheOuterRingComesBack() {
        var withHole = """
                {"type": "FeatureCollection", "features": [{
                  "type": "Feature", "properties": {"name": "ring"},
                  "geometry": {"type": "Polygon", "coordinates": [
                    [[0,0],[10,0],[10,10],[0,10],[0,0]],
                    [[2,2],[4,2],[4,4],[2,4],[2,2]]]}}]}
                """;

        var back = GeoJson.toRois(withHole);

        assertEquals(1, back.size());
        assertEquals(4, back.get(0).getFloatPolygon().npoints);
    }

    @Test
    void aNonPolygonGeometryIsRefusedRatherThanGuessed() {
        var point = """
                {"type": "FeatureCollection", "features": [{
                  "type": "Feature", "properties": {},
                  "geometry": {"type": "Point", "coordinates": [1, 2]}}]}
                """;

        var raised = assertThrows(IllegalArgumentException.class,
                () -> GeoJson.toRois(point));

        assertTrue(raised.getMessage().contains("Point"), raised.getMessage());
    }

    @Test
    void somethingThatIsNotAFeatureCollectionIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> GeoJson.toRois("{\"type\": \"Polygon\"}"));
    }
}
