package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.IJ;
import ij.ImagePlus;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Stitch and fit a mosaic...")
public class StitchCommand implements Command {

    @Parameter
    private UIService ui;

    @Parameter(label = "Container holding the tile positions",
               style = FileWidget.OPEN_STYLE)
    private File container;

    static String requestBody(String container, JsonObject params) {
        var body = new JsonObject();
        body.addProperty("container", container);
        if (params != null && !params.entrySet().isEmpty())
            body.add("params", params);
        return body.toString();
    }

    static List<ImagePlus> openProducts(JsonObject result) {
        var opened = new ArrayList<ImagePlus>();
        if (result == null || !result.has("products"))
            return opened;
        for (var element : result.getAsJsonArray("products")) {
            var product = element.getAsJsonObject();
            ImagePlus image = IJ.openImage(product.get("file").getAsString());
            if (image == null)
                continue;
            image.setTitle("FLIMKit " + product.get("image_id").getAsString());
            if (product.has("unit"))
                image.getCalibration().setValueUnit(product.get("unit").getAsString());
            opened.add(image);
        }
        return opened;
    }

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            var defaults = JsonParser.parseString(
                    client.pipelineDefaults()).getAsJsonObject();
            var params = FitSettings.prompt(defaults, "pipeline",
                    "FLIMKit stitch and fit");
            if (params == null)
                return;
            var started = JsonParser.parseString(client.pipeline(
                    requestBody(container.getAbsolutePath(), params))).getAsJsonObject();
            var result = BridgeJob.await(client, started.get("job").getAsString(),
                    "Stitch and fit");
            var opened = openProducts(result);
            for (var image : opened)
                image.show();
            if (opened.isEmpty())
                ui.showDialog("The run finished but wrote no images to open.\n\n"
                        + "Output: " + started.get("output_dir").getAsString(),
                        "FLIMKit bridge");
            if (result != null && result.has("global_summary"))
                FitResults.summaryTable(result.getAsJsonObject("global_summary"),
                        container.getName()).show("FLIMKit stitch summary");
        } catch (Exception e) {
            ui.showDialog("Could not stitch and fit.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
