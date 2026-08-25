package io.github.flimkit.fiji;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ij.measure.ResultsTable;

import java.util.ArrayList;
import java.util.List;

public class FitResults {

    private FitResults() {}

    public static ResultsTable toTable(JsonObject payload) {
        var table = new ResultsTable();
        for (var element : payload.getAsJsonArray("results")) {
            JsonObject result = element.getAsJsonObject();
            if (result.has("error"))
                continue;
            table.incrementCounter();
            table.addLabel(result.get("name").getAsString());
            put(table, result, "tau_mean_ns", "tau mean (ns)");
            put(table, result, "tau_mean_amp_ns", "tau mean amp (ns)");
            put(table, result, "tau_mean_int_ns", "tau mean int (ns)");
            put(table, result, "chi2_r", "chi2r");
            put(table, result, "photon_count", "photons");
            put(table, result, "n_pixels", "pixels");
            put(table, result, "n_exp", "components");
            putArray(table, result, "taus_ns", "tau");
            putArray(table, result, "fractions", "f");
        }
        return table;
    }

    private static void put(ResultsTable table, JsonObject result, String key, String label) {
        if (!result.has(key) || result.get(key).isJsonNull())
            return;
        table.addValue(label, result.get(key).getAsDouble());
    }

    private static void putArray(ResultsTable table, JsonObject result, String key,
                                 String prefix) {
        if (!result.has(key) || !result.get(key).isJsonArray())
            return;
        JsonArray values = result.getAsJsonArray(key);
        for (int i = 0; i < values.size(); i++)
            table.addValue(prefix + (i + 1), values.get(i).getAsDouble());
    }

    public static ResultsTable summaryTable(JsonObject summary, String label) {
        var table = new ResultsTable();
        table.incrementCounter();
        table.addLabel(label);
        for (var key : summary.keySet()) {
            var value = summary.get(key);
            if (value == null || value.isJsonNull())
                continue;
            if (value.isJsonArray()) {
                putArray(table, summary, key, key + " ");
                continue;
            }
            if (!value.isJsonPrimitive())
                continue;
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber())
                table.addValue(key, primitive.getAsDouble());
            else if (primitive.isBoolean())
                table.addValue(key, primitive.getAsBoolean() ? 1 : 0);
            else
                table.addValue(key, primitive.getAsString());
        }
        return table;
    }

    public static List<String> errors(JsonObject payload) {
        var found = new ArrayList<String>();
        for (var element : payload.getAsJsonArray("results")) {
            JsonObject result = element.getAsJsonObject();
            if (result.has("error"))
                found.add(result.get("name").getAsString() + ": "
                        + result.get("error").getAsString());
        }
        return found;
    }
}
