package io.github.flimkit.fiji;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Phasor plot...")
public class PhasorCommand implements Command {

    @Parameter
    private UIService ui;

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            new PhasorWindow(client, Session.datasetId()).open();
        } catch (Exception e) {
            ui.showDialog("Could not open the phasor plot.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
