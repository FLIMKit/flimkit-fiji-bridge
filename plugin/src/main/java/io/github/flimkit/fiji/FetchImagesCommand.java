package io.github.flimkit.fiji;

import ij.ImagePlus;
import ij.io.Opener;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.ByteArrayInputStream;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Fetch FLIMKit images")
public class FetchImagesCommand implements Command {

    static final String SOURCE_PROPERTY = "FLIMKit source";
    static final String UNIT_PROPERTY = "FLIMKit value unit";

    @Parameter
    private UIService ui;

    static ImagePlus fetch(BridgeClient client, String imageId, String title)
            throws Exception {
        BridgeClient.Image served = client.image(imageId);
        String unit = served.unit();
        if (unit == null || unit.isEmpty())
            throw new IllegalStateException("FLIMKit served " + imageId
                    + " without a value unit, so the pixels have no meaning");
        ImagePlus image = new Opener().openTiff(
                new ByteArrayInputStream(served.tiff()), title);
        if (image == null)
            throw new IllegalStateException("Fiji could not decode the " + imageId + " TIFF");
        image.getCalibration().setValueUnit(unit);
        image.setProperty(UNIT_PROPERTY, unit);
        image.setProperty(SOURCE_PROPERTY, client.baseUrl());
        return image;
    }

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            var intensity = fetch(client, "intensity", "FLIMKit intensity");
            var lifetime = fetch(client, "lifetime", "FLIMKit lifetime");
            intensity.show();
            lifetime.show();
        } catch (Exception e) {
            ui.showDialog("Could not fetch the FLIMKit images.\n\n" + e.getMessage()
                    + "\n\nFit a dataset in FLIMKit first.", "FLIMKit bridge");
        }
    }
}
