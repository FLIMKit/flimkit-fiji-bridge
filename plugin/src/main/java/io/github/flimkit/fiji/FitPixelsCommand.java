package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.ImagePlus;
import ij.io.Opener;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Fit per-pixel lifetimes...")
public class FitPixelsCommand implements Command {

    @Parameter
    private UIService ui;

    static List<String> planeNames(JsonObject result) {
        var names = new ArrayList<String>();
        if (result.has("planes")) {
            for (var element : result.getAsJsonArray("planes"))
                names.add(element.isJsonObject()
                        ? element.getAsJsonObject().get("id").getAsString()
                        : element.getAsString());
        }
        return names;
    }

    static ImagePlus stack(BridgeClient client, String datasetId, List<String> names)
            throws Exception {
        byte[] tiff = client.planes(datasetId, String.join(",", names));
        ImagePlus image = new Opener().openTiff(
                new ByteArrayInputStream(tiff), "FLIMKit lifetime maps");
        if (image == null)
            throw new IllegalStateException("Fiji could not decode the lifetime maps");
        for (int i = 0; i < names.size() && i < image.getStackSize(); i++)
            image.getStack().setSliceLabel(names.get(i), i + 1);
        image.setProperty(FetchImagesCommand.SOURCE_PROPERTY, client.baseUrl());
        return image;
    }

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            String id = Session.datasetId();
            var defaults = JsonParser.parseString(client.fitDefaults()).getAsJsonObject();
            var params = FitSettings.prompt(defaults, "per_pixel",
                    "FLIMKit per-pixel fit");
            if (params == null)
                return;
            var body = new JsonObject();
            if (!params.entrySet().isEmpty())
                body.add("params", params);
            var started = JsonParser.parseString(
                    client.fitPixels(id, body.toString())).getAsJsonObject();
            String jobId = started.get("job").getAsString();
            var result = BridgeJob.await(client, jobId, "Per-pixel fit");
            var names = planeNames(result);
            if (names.isEmpty())
                names = List.of("tau_mean_amp", "tau_mean_int");
            stack(client, id, names).show();
            if (result.has("global") && result.get("global").isJsonObject())
                FitResults.summaryTable(result.getAsJsonObject("global"),
                        "summed fit").show("FLIMKit per-pixel summary");
        } catch (Exception e) {
            ui.showDialog("Could not fit per-pixel.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
