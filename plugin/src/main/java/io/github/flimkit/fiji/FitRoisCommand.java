package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.util.Arrays;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Fit ROI decays...")
public class FitRoisCommand implements Command {

    @Parameter
    private UIService ui;

    static String requestBody(List<Roi> rois, JsonObject params) {
        var body = new JsonObject();
        body.add("rois", JsonParser.parseString(GeoJson.toFeatureCollection(rois)));
        if (params != null && !params.entrySet().isEmpty())
            body.add("params", params);
        return body.toString();
    }

    @Override
    public void run() {
        try {
            RoiManager manager = RoiManager.getInstance();
            if (manager == null || manager.getCount() == 0)
                throw new IllegalStateException(
                        "The ROI Manager is empty. Draw a region and press T to add it.");
            SendRoisCommand.checkCoordinateSpace(WindowManager.getCurrentImage());
            var client = ConnectCommand.connect();
            String id = Session.datasetId();
            var defaults = JsonParser.parseString(client.fitDefaults()).getAsJsonObject();
            var params = FitSettings.prompt(defaults, "roi", "FLIMKit ROI fit");
            if (params == null)
                return;
            List<Roi> rois = Arrays.asList(manager.getRoisAsArray());
            var payload = JsonParser.parseString(
                    client.fitRois(id, requestBody(rois, params))).getAsJsonObject();
            var table = FitResults.toTable(payload);
            table.show("FLIMKit ROI fits");
            var errors = FitResults.errors(payload);
            if (!errors.isEmpty())
                ui.showDialog(String.join("\n", errors), "FLIMKit bridge");
        } catch (Exception e) {
            ui.showDialog("Could not fit.\n\n" + e.getMessage(), "FLIMKit bridge");
        }
    }
}
