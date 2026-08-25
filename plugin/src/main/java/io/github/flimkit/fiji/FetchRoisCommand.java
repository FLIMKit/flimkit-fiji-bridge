package io.github.flimkit.fiji;

import ij.plugin.frame.RoiManager;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Fetch ROIs from FLIMKit")
public class FetchRoisCommand implements Command {

    @Parameter
    private UIService ui;

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            var rois = GeoJson.toRois(client.fetchRois());
            if (rois.isEmpty()) {
                ui.showDialog("FLIMKit has no regions to send.", "FLIMKit bridge");
                return;
            }
            RoiManager manager = RoiManager.getRoiManager();
            for (var roi : rois)
                manager.addRoi(roi);
            ui.showDialog("Added " + rois.size() + " region(s) to the ROI Manager.",
                    "FLIMKit bridge");
        } catch (Exception e) {
            ui.showDialog("Could not fetch the regions.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
