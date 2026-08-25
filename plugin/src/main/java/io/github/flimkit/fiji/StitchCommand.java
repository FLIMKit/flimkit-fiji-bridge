package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Stitch and fit a mosaic...")
public class StitchCommand implements Command {

    @Parameter
    private UIService ui;

    @Parameter(label = "Container holding the tile positions",
               style = FileWidget.OPEN_STYLE)
    private File container;

    @Parameter(label = "Pipeline", choices = {"tile_fit", "stitch_fit"})
    private String pipeline = "tile_fit";

    static String requestBody(String container, String pipeline) {
        var body = new JsonObject();
        body.addProperty("container", container);
        var params = new JsonObject();
        params.addProperty("pipeline", pipeline);
        body.add("params", params);
        return body.toString();
    }

    static String outputOf(JsonObject started, JsonObject result) {
        if (result != null && result.has("output_dir"))
            return result.get("output_dir").getAsString();
        return started.get("output_dir").getAsString();
    }

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            var started = JsonParser.parseString(client.pipeline(
                    requestBody(container.getAbsolutePath(), pipeline))).getAsJsonObject();
            var result = BridgeJob.await(client, started.get("job").getAsString(),
                    "Stitch and fit");
            String output = outputOf(started, result);
            String id = OpenDatasetCommand.open(client, output);
            var names = FitPixelsCommand.planeNames(result);
            if (names.isEmpty())
                names = List.of("intensity");
            FitPixelsCommand.stack(client, id, names).show();
            if (result != null)
                FitResults.summaryTable(result, container.getName())
                        .show("FLIMKit stitch summary");
        } catch (Exception e) {
            ui.showDialog("Could not stitch and fit.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
