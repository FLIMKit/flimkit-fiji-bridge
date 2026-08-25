package io.github.flimkit.fiji;

import com.google.gson.JsonParser;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.util.Arrays;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Send ROIs to FLIMKit")
public class SendRoisCommand implements Command {

    @Parameter
    private UIService ui;

    static void checkCoordinateSpace(ImagePlus current) {
        if (current == null || current.getProperty(FetchImagesCommand.SOURCE_PROPERTY) == null)
            throw new IllegalStateException(
                    "FLIMKit expects regions in FLIM image-pixel coordinates. Draw them "
                    + "on an image from Plugins > FLIMKit > Fetch FLIMKit images, or "
                    + "align yours to it first.");
    }

    @Override
    public void run() {
        try {
            RoiManager manager = RoiManager.getInstance();
            if (manager == null || manager.getCount() == 0)
                throw new IllegalStateException(
                        "The ROI Manager is empty. Draw a region and press T to add it.");
            checkCoordinateSpace(WindowManager.getCurrentImage());
            List<Roi> rois = Arrays.asList(manager.getRoisAsArray());
            var client = ConnectCommand.connect();
            var reply = JsonParser.parseString(
                    client.sendRois(GeoJson.toFeatureCollection(rois))).getAsJsonObject();
            ui.showDialog("Sent " + reply.get("received_features").getAsInt()
                    + " region(s) to FLIMKit.", "FLIMKit bridge");
        } catch (Exception e) {
            ui.showDialog("Could not send the regions.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
