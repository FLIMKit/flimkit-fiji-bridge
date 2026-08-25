package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhasorPlotTest {

    private static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void countsDecodeAsLittleEndianUnsignedInts() {
        var buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0).putInt(7).putInt(70000);

        int[] counts = PhasorPlot.decodeCounts(
                Base64.getEncoder().encodeToString(buffer.array()));

        assertEquals(3, counts.length);
        assertEquals(0, counts[0]);
        assertEquals(7, counts[1]);
        assertEquals(70000, counts[2]);
    }

    @Test
    void anEllipseGoesOnTheWireAsACentreAndARadius() {
        var body = parse(PhasorPlot.requestBody(
                List.of(PhasorPlot.Cursor.ellipse("c1", 0.3, 0.4, 0.05)), null, false));
        var entry = body.getAsJsonArray("cursors").get(0).getAsJsonObject();

        assertEquals("c1", entry.get("id").getAsString());
        assertFalse(entry.has("type"));
        assertEquals(0.3, entry.get("center_g").getAsDouble(), 1e-9);
        assertEquals(0.05, entry.get("radius").getAsDouble(), 1e-9);
    }

    @Test
    void anOutlineGoesOnTheWireAsAPolygon() {
        var body = parse(PhasorPlot.requestBody(
                List.of(PhasorPlot.Cursor.polygon("c1", List.of(
                        new double[] {0.25, 0.35},
                        new double[] {0.35, 0.35},
                        new double[] {0.35, 0.45}))), null, false));
        var entry = body.getAsJsonArray("cursors").get(0).getAsJsonObject();

        assertEquals("polygon", entry.get("type").getAsString());
        assertFalse(entry.has("center_g"));
        assertEquals(3, entry.getAsJsonArray("vertices").size());
    }

    @Test
    void theMaskUsesTheSameFloorTheDensityDoes() {
        var body = parse(PhasorPlot.requestBody(
                List.of(PhasorPlot.Cursor.ellipse("c1", 0.3, 0.4, 0.05)), null, false));

        assertEquals(PhasorPlot.DEFAULT_MIN_PHOTONS,
                body.get("min_photons").getAsDouble(), 1e-9);
        assertEquals(0.01, PhasorPlot.DEFAULT_MIN_PHOTONS, 1e-9,
                "a higher floor hides pixels the plot draws, on a dim file nearly all");
    }

    @Test
    void theLabelsFlagAsksForALabelImage() {
        var cursors = List.of(PhasorPlot.Cursor.ellipse("c1", 0.3, 0.4, 0.05));

        assertFalse(parse(PhasorPlot.requestBody(cursors, null, false)).has("output"));
        assertEquals("labels", parse(PhasorPlot.requestBody(cursors, null, true))
                .get("output").getAsString());
    }

    @Test
    void settingsRideOnTheBodyOnlyOnceChosen() {
        var cursors = List.of(PhasorPlot.Cursor.ellipse("c1", 0.3, 0.4, 0.05));

        assertFalse(parse(PhasorPlot.requestBody(cursors, new JsonObject(), false))
                .has("options"));
        assertEquals("median", parse(PhasorPlot.requestBody(cursors,
                parse("{\"phasor_filter\": \"median\"}"), false))
                .getAsJsonObject("options").get("phasor_filter").getAsString());
    }

    @Test
    void chosenOptionsBecomeAnEscapedQueryString() {
        assertEquals("", PhasorPlot.optionsQuery(new JsonObject()));
        assertEquals("phasor_filter=median",
                PhasorPlot.optionsQuery(parse("{\"phasor_filter\": \"median\"}")));
        assertEquals("irf=machine%2Ba",
                PhasorPlot.optionsQuery(parse("{\"irf\": \"machine+a\"}")));
    }

    @Test
    void anEmptyCursorReportsOnlyItsPixelCount() {
        assertEquals("c1: 0 px",
                PhasorPlot.describe(parse("{\"id\": \"c1\", \"n_pixels\": 0}")));
    }

    @Test
    void aPopulatedCursorReportsBothLifetimes() {
        var line = PhasorPlot.describe(parse("""
                {"id": "c1", "n_pixels": 128, "tau_phi_ns": 2.345,
                 "tau_mod_ns": 2.567, "mean_g": 0.301, "mean_s": 0.402}
                """));

        assertTrue(line.startsWith("c1: 128 px"), line);
        assertTrue(line.contains("tau_phi 2.35"), line);
        assertTrue(line.contains("(G 0.301, S 0.402)"), line);
    }

    @Test
    void aNullLifetimeIsLeftOutRatherThanPrintedAsNull() {
        var line = PhasorPlot.describe(parse("""
                {"id": "c1", "n_pixels": 5, "tau_phi_ns": null, "tau_mod_ns": 2.0}
                """));

        assertFalse(line.contains("null"), line);
        assertTrue(line.contains("tau_m 2.00"), line);
    }
}
