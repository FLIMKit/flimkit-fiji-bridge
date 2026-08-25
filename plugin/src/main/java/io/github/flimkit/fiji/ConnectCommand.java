package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Connect")
public class ConnectCommand implements Command {

    @Parameter
    private UIService ui;

    static BridgeClient connect() throws Exception {
        var details = Discovery.read();
        if (details.stale())
            throw new IllegalStateException(
                    "The FLIMKit that published this address is no longer running.");
        return new BridgeClient(details.url(), details.token());
    }

    static String mismatchWarning(JsonObject status) {
        if (!status.has("protocol_version") || status.get("protocol_version").isJsonNull())
            return "This bridge is too old to say which protocol it speaks."
                    + "\nUpdate it with: pip install -U flimkit-bridge";
        int theirs = status.get("protocol_version").getAsInt();
        if (theirs == BridgeClient.PROTOCOL_VERSION)
            return null;
        return "This plugin speaks bridge protocol " + BridgeClient.PROTOCOL_VERSION
                + " and the bridge speaks " + theirs + ". Update whichever is older.";
    }

    static String describe(JsonObject status) {
        var text = new StringBuilder("Connected to FLIMKit.\n")
                .append("Bridge ").append(status.get("bridge_version").getAsString())
                .append(", FLIMKit ").append(status.get("flimkit_version").getAsString());
        String warning = mismatchWarning(status);
        if (warning != null)
            text.append("\n\n").append(warning);
        return text.toString();
    }

    @Override
    public void run() {
        try {
            var client = connect();
            var status = JsonParser.parseString(client.status()).getAsJsonObject();
            ui.showDialog(describe(status), "FLIMKit bridge");
        } catch (Exception e) {
            ui.showDialog("Could not connect to FLIMKit.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
