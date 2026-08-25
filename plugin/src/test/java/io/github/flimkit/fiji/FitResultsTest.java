package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FitResultsTest {

    private static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void aSecondFitAddsARowRatherThanReplacingTheTable() {
        var table = FitResults.summaryTable(
                parse("{\"tau_mean_ns\": 2.1, \"chi2_r\": 1.05}"), "first.ptu");

        FitResults.addSummary(table,
                parse("{\"tau_mean_ns\": 3.4, \"chi2_r\": 1.11}"), "second.ptu");

        assertEquals(2, table.size());
        assertEquals("first.ptu", table.getLabel(0));
        assertEquals("second.ptu", table.getLabel(1));
        assertEquals(2.1, table.getValue("tau_mean_ns", 0), 1e-9);
        assertEquals(3.4, table.getValue("tau_mean_ns", 1), 1e-9);
    }

    @Test
    void aColumnOnlyTheSecondFitHasStillLandsOnTheSecondRow() {
        var table = FitResults.summaryTable(parse("{\"tau_mean_ns\": 2.1}"), "first.ptu");

        FitResults.addSummary(table,
                parse("{\"tau_mean_ns\": 3.4, \"n_exp\": 2}"), "second.ptu");

        assertEquals(2, table.size());
        assertEquals(2, table.getValue("n_exp", 1), 1e-9);
        assertTrue(Double.isNaN(table.getValue("n_exp", 0)));
    }

    @Test
    void everyRowIsLabelledSoTheFileIsNeverAmbiguous() {
        var table = FitResults.summaryTable(parse("{\"tau_mean_ns\": 2.1}"), "first.ptu");
        FitResults.addSummary(table, parse("{\"tau_mean_ns\": 3.4}"), "second.ptu");
        FitResults.addSummary(table, parse("{\"tau_mean_ns\": 2.9}"), "first.ptu");

        assertEquals(3, table.size());
        for (int row = 0; row < table.size(); row++)
            assertTrue(table.getLabel(row) != null && !table.getLabel(row).isEmpty(),
                    "row " + row + " has no file label");
    }
}
