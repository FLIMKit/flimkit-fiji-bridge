package io.github.flimkit.fiji;

import com.google.gson.JsonParser;

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

    static ImagePlus fetchPlane(BridgeClient client, String datasetId, String name,
                                String title) throws Exception {
        return build(client.plane(datasetId, name), name, title, client.baseUrl());
    }

    static ImagePlus fetch(BridgeClient client, String imageId, String title)
            throws Exception {
        return build(client.image(imageId), imageId, title, client.baseUrl());
    }

    static ImagePlus build(BridgeClient.Image served, String imageId, String title,
                           String baseUrl) throws Exception {
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
        image.setProperty(SOURCE_PROPERTY, baseUrl);
        return image;
    }

    static void showEveryPlane(BridgeClient client, String datasetId) throws Exception {
        var planes = JsonParser.parseString(client.planeList(datasetId))
                .getAsJsonObject().getAsJsonArray("planes");
        for (var element : planes) {
            String name = element.getAsJsonObject().get("id").getAsString();
            fetchPlane(client, datasetId, name, "FLIMKit " + name).show();
        }
    }

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            if (Session.isOpen()) {
                showEveryPlane(client, Session.datasetId());
                return;
            }
            fetch(client, "intensity", "FLIMKit intensity").show();
            fetch(client, "lifetime", "FLIMKit lifetime").show();
        } catch (Exception e) {
            ui.showDialog("Could not fetch the FLIMKit images.\n\n" + e.getMessage()
                    + "\n\nFit a dataset in FLIMKit first.", "FLIMKit bridge");
        }
    }
}
