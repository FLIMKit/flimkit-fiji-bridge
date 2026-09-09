package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.IJ;

public class BridgeJob {

    private BridgeJob() {}

    static double fractionOf(JsonObject status) {
        if (!status.has("progress") || !status.get("progress").isJsonObject())
            return 0.0;
        var progress = status.getAsJsonObject("progress");
        if (!progress.has("fraction") || progress.get("fraction").isJsonNull())
            return 0.0;
        return progress.get("fraction").getAsDouble();
    }

    static String messageOf(JsonObject status, String state) {
        if (status.has("message") && !status.get("message").isJsonNull())
            return status.get("message").getAsString();
        return state;
    }

    /** The bridge reports an error as an object, not a string. */
    static String reasonOf(JsonObject status) {
        if (!status.has("error") || status.get("error").isJsonNull())
            return "";
        var error = status.get("error");
        if (error.isJsonObject()) {
            var described = error.getAsJsonObject();
            if (described.has("message"))
                return ": " + described.get("message").getAsString();
            return ": " + described;
        }
        return ": " + error.getAsString();
    }

    public static JsonObject await(BridgeClient client, String jobId, String what)
            throws Exception {
        while (true) {
            var status = JsonParser.parseString(client.jobStatus(jobId)).getAsJsonObject();
            String state = status.get("state").getAsString();
            IJ.showProgress(fractionOf(status));
            IJ.showStatus(what + ": " + messageOf(status, state));
            if ("done".equals(state)) {
                IJ.showProgress(1.0);
                return JsonParser.parseString(client.jobResult(jobId))
                        .getAsJsonObject().getAsJsonObject("result");
            }
            // The bridge calls a failed job "error"; "failed" was never a state
            // it reports, so waiting only on that spun here forever.
            if ("error".equals(state) || "failed".equals(state)
                    || "cancelled".equals(state)) {
                IJ.showProgress(1.0);
                throw new IllegalStateException(what + " " + state + reasonOf(status));
            }
            if (IJ.escapePressed()) {
                client.cancelJob(jobId);
                throw new IllegalStateException(what + " cancelled");
            }
            Thread.sleep(500);
        }
    }
}
