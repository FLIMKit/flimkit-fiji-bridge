package io.github.flimkit.fiji;

import com.google.gson.JsonParser;

import ij.IJ;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Open FLIM file...")
public class OpenDatasetCommand implements Command {

    @Parameter
    private UIService ui;

    @Parameter(label = "FLIM file", style = FileWidget.OPEN_STYLE)
    private File file;

    static String open(BridgeClient client, String path) throws Exception {
        var reply = JsonParser.parseString(client.openDataset(path)).getAsJsonObject();
        String id = reply.get("id").getAsString();
        Session.opened(id, path);
        return id;
    }

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            String id = open(client, file.getAbsolutePath());
            var intensity = FetchImagesCommand.fetch(
                    client, "intensity", "FLIMKit intensity");
            intensity.show();
            IJ.showStatus("FLIMKit opened " + file.getName() + " as " + id);
        } catch (Exception e) {
            ui.showDialog("Could not open that file through FLIMKit.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
