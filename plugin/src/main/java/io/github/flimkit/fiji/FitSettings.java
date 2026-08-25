package io.github.flimkit.fiji;

import com.google.gson.JsonObject;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.List;

public class FitSettings {

    private FitSettings() {}

    static boolean appliesTo(JsonObject entry, String mode) {
        for (var applies : entry.getAsJsonArray("applies_to")) {
            if (applies.getAsString().equals(mode))
                return true;
        }
        return false;
    }

    public static JsonObject prompt(JsonObject defaults, String mode, String title) {
        var values = defaults.getAsJsonObject("values");
        var schema = defaults.getAsJsonArray("schema");
        var dialog = new GenericDialog(title);
        var shown = new ArrayList<JsonObject>();
        for (var element : schema) {
            JsonObject entry = element.getAsJsonObject();
            String key = entry.get("key").getAsString();
            if (!appliesTo(entry, mode) || !values.has(key) || values.get(key).isJsonNull())
                continue;
            String label = entry.get("label").getAsString();
            switch (entry.get("type").getAsString()) {
                case "int" -> dialog.addNumericField(label, values.get(key).getAsInt(), 0);
                case "float" -> dialog.addNumericField(label, values.get(key).getAsDouble(), 3);
                case "bool" -> dialog.addCheckbox(label, values.get(key).getAsBoolean());
                case "choice" -> {
                    List<String> choices = new ArrayList<>();
                    for (var choice : entry.getAsJsonArray("choices"))
                        choices.add(choice.getAsString());
                    dialog.addChoice(label, choices.toArray(new String[0]),
                            values.get(key).getAsString());
                }
                case "path" -> dialog.addStringField(label, values.get(key).getAsString(), 30);
                default -> {
                    continue;
                }
            }
            shown.add(entry);
        }
        dialog.showDialog();
        if (dialog.wasCanceled())
            return null;
        return collect(dialog, shown);
    }

    static JsonObject collect(GenericDialog dialog, List<JsonObject> shown) {
        var chosen = new JsonObject();
        for (var entry : shown) {
            String key = entry.get("key").getAsString();
            switch (entry.get("type").getAsString()) {
                case "int" -> chosen.addProperty(key, (int) dialog.getNextNumber());
                case "float" -> chosen.addProperty(key, dialog.getNextNumber());
                case "bool" -> chosen.addProperty(key, dialog.getNextBoolean());
                case "choice" -> chosen.addProperty(key, dialog.getNextChoice());
                case "path" -> {
                    String typed = dialog.getNextString();
                    if (typed != null && !typed.isBlank())
                        chosen.addProperty(key, typed.trim());
                }
                default -> {
                }
            }
        }
        return chosen;
    }
}
