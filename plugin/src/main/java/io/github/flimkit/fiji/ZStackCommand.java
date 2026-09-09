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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>FLIMKit>Fit a z-stack...")
public class ZStackCommand implements Command {

    @Parameter
    private UIService ui;

    @Parameter(label = "Folder holding the z-slices",
               style = FileWidget.DIRECTORY_STYLE)
    private File ptuDir;

    static String requestBody(String ptuDir, JsonObject params) {
        var body = new JsonObject();
        body.addProperty("ptu_dir", ptuDir);
        if (params != null && !params.entrySet().isEmpty())
            body.add("params", params);
        return body.toString();
    }

    static String describeStacks(JsonObject found) {
        var lines = new ArrayList<String>();
        int stacks = found.get("n_stacks").getAsInt();
        lines.add(stacks == 1 ? "Found 1 z-stack:" : "Found " + stacks + " z-stacks:");
        for (var element : found.getAsJsonArray("stacks")) {
            var stack = element.getAsJsonObject();
            lines.add("  " + stack.get("label").getAsString() + " - "
                    + stack.get("n_slices").getAsInt() + " slices (z "
                    + stack.get("z_first").getAsInt() + " to "
                    + stack.get("z_last").getAsInt() + ")");
        }
        return String.join("\n", lines);
    }

    static String nothingFound(String folder) {
        return "No z-stack slices in " + folder + ".\n\n"
                + "FLIMKit reads one file per slice, named region_z1.ptu, "
                + "region_z2.ptu and so on\n(region_t1_s1_z1.ptu also works).";
    }

    static int channelCount(JsonObject product) {
        return product.has("channels") && product.get("channels").isJsonArray()
                ? product.getAsJsonArray("channels").size() : 1;
    }

    static double voxel(JsonObject product, int axis, double fallback) {
        if (!product.has("voxel_size_um") || !product.get("voxel_size_um").isJsonArray())
            return fallback;
        var sizes = product.getAsJsonArray("voxel_size_um");
        return axis < sizes.size() ? sizes.get(axis).getAsDouble() : fallback;
    }

    /**
     * Lays the opened TIFF out as a hyperstack and gives it the voxel size the
     * fit was told about. The file is written z-major with the channels inside
     * each slice, which is ImageJ's own czt order, so the dimensions go on
     * without reordering anything.
     *
     * The channels are shown one at a time rather than composited. They are
     * unrelated quantities, photons against nanoseconds against ratios, so
     * blending them into one colour image says nothing; ImageJ also refuses
     * composite mode above seven channels, and a fit carries more than that.
     */
    static ImagePlus shape(ImagePlus image, JsonObject product) {
        int channels = channelCount(product);
        int slices = product.has("n_z") ? product.get("n_z").getAsInt() : 1;
        boolean stacked = channels * slices == image.getStackSize()
                && channels * slices > 1;
        if (stacked) {
            image.setDimensions(channels, slices, 1);
            image.setOpenAsHyperStack(true);
        }
        if (stacked && channels > 1)
            image = new ij.CompositeImage(image, ij.CompositeImage.GRAYSCALE);
        var calibration = image.getCalibration();
        calibration.pixelDepth = voxel(product, 0, 1.0);
        calibration.pixelHeight = voxel(product, 1, 1.0);
        calibration.pixelWidth = voxel(product, 2, 1.0);
        calibration.setUnit("um");
        calibration.setValueUnit("ns");
        image.setTitle("FLIMKit " + product.get("image_id").getAsString() + " z-stack");
        nameChannels(image, product);
        return image;
    }

    /** Puts the map names on the channel slider, so a channel says what it is. */
    static void nameChannels(ImagePlus image, JsonObject product) {
        if (!(image instanceof ij.CompositeImage) || !product.has("channels")
                || !product.get("channels").isJsonArray())
            return;
        var names = product.getAsJsonArray("channels");
        var units = product.has("units") && product.get("units").isJsonArray()
                ? product.getAsJsonArray("units") : null;
        for (int c = 0; c < names.size() && c < image.getNChannels(); c++) {
            String unit = units != null && c < units.size()
                    ? units.get(c).getAsString() : "";
            String label = names.get(c).getAsString()
                    + (unit == null || unit.isBlank() ? "" : " (" + unit + ")");
            image.getStack().setSliceLabel(label, image.getStackIndex(c + 1, 1, 1));
        }
    }

    /**
     * The numbers behind the images: a row per z-slice, carrying the mean
     * lifetime and whatever else the fit recorded for that slice, labelled
     * with its stack and depth. The lifetimes themselves are one per stack,
     * not per slice, since the whole stack is fitted as one FOV, so they go
     * on every row of that stack rather than into a table of their own.
     */
    static ij.measure.ResultsTable seriesTable(JsonObject result) {
        var table = new ij.measure.ResultsTable();
        if (result == null || !result.has("stacks") || !result.get("stacks").isJsonArray())
            return table;
        for (var element : result.getAsJsonArray("stacks")) {
            var stack = element.getAsJsonObject();
            String label = stack.has("label")
                    ? stack.get("label").getAsString() : "z-stack";
            if (!stack.has("z_series") || !stack.get("z_series").isJsonArray())
                continue;
            for (var row : stack.getAsJsonArray("z_series")) {
                var slice = row.getAsJsonObject().deepCopy();
                slice.remove("path");
                String z = slice.has("z") ? slice.get("z").getAsString() : "?";
                FitResults.addSummary(table, slice, label + " z" + z);
            }
        }
        return table;
    }

    /**
     * The fit that every slice was locked to: one row per stack, from the
     * decay pooled over all of its slices. This is the fit that decided the
     * lifetimes; the per-slice table only has the amplitudes that followed.
     */
    static ij.measure.ResultsTable pooledTable(JsonObject result) {
        var table = new ij.measure.ResultsTable();
        if (result == null || !result.has("stacks") || !result.get("stacks").isJsonArray())
            return table;
        for (var element : result.getAsJsonArray("stacks")) {
            var stack = element.getAsJsonObject();
            if (!stack.has("pooled") || !stack.get("pooled").isJsonObject())
                continue;
            FitResults.addSummary(table, stack.getAsJsonObject("pooled"),
                    stack.has("label") ? stack.get("label").getAsString() : "z-stack");
        }
        return table;
    }

    /** What the pooled fit settled on, and where the rest of it was written. */
    static List<String> lifetimeLines(JsonObject result) {
        var lines = new ArrayList<String>();
        if (result == null || !result.has("stacks") || !result.get("stacks").isJsonArray())
            return lines;
        for (var element : result.getAsJsonArray("stacks")) {
            var stack = element.getAsJsonObject();
            String label = stack.has("label")
                    ? stack.get("label").getAsString() : "z-stack";
            if (stack.has("error")) {
                lines.add(label + ": " + stack.get("error").getAsString());
                continue;
            }
            var parts = new ArrayList<String>();
            if (stack.has("taus_ns") && stack.get("taus_ns").isJsonArray()) {
                var taus = stack.getAsJsonArray("taus_ns");
                for (int i = 0; i < taus.size(); i++)
                    parts.add(String.format("tau%d = %.4f ns", i + 1,
                            taus.get(i).getAsDouble()));
            }
            lines.add(label + ": " + (parts.isEmpty() ? "no lifetimes reported"
                    : String.join(", ", parts) + "  (locked across all "
                            + (stack.has("n_slices")
                                    ? stack.get("n_slices").getAsString() : "?")
                            + " slices)"));
            if (stack.has("z_series_csv") && !stack.get("z_series_csv").isJsonNull())
                lines.add("  per-slice CSV: " + stack.get("z_series_csv").getAsString());
            if (stack.has("reference_fit") && !stack.get("reference_fit").isJsonNull())
                lines.add("  pooled fit: " + stack.get("reference_fit").getAsString());
        }
        return lines;
    }

    private static void logLifetimes(JsonObject result) {
        for (String line : lifetimeLines(result))
            IJ.log("[FLIMKit] " + line);
    }

    /**
     * Whether it is worth handing this path to ImageJ at all. A Zarr store is
     * a directory of chunks that ImageJ has no reader for, and asking it to
     * open one puts "Unsupported format or file not found" in front of the
     * user before the fallback below has had its turn.
     */
    static boolean worthOpeningDirectly(JsonObject product, String path) {
        if (product.has("format") && !product.get("format").isJsonNull()
                && "ome-zarr".equals(product.get("format").getAsString()))
            return false;
        return new File(path).isFile();
    }

    private ImagePlus open(BridgeClient client, JsonObject product,
                           List<String> fetched) {
        String path = product.get("file").getAsString();
        ImagePlus image = null;
        if (worthOpeningDirectly(product, path)) {
            // Send a reader complaint to the Log rather than a dialog: there
            // is still the fallback to try.
            IJ.redirectErrorMessages();
            image = IJ.openImage(path);
            if (image != null)
                return image;
        }
        // Either ImageJ has no reader for it, or it was written on a machine
        // this Fiji cannot see. Either way the bridge will send the same
        // volume as a TIFF over the connection it is already using.
        if (!product.has("group_dir"))
            return null;
        try {
            IJ.showStatus("FLIMKit: downloading "
                    + product.get("image_id").getAsString() + "...");
            Path downloaded = client.zstackVolume(
                    product.get("group_dir").getAsString(),
                    product.get("image_id").getAsString(),
                    voxel(product, 0, 1.0), voxel(product, 2, 0.0));
            image = IJ.openImage(downloaded.toString());
            if (image != null)
                fetched.add(product.get("image_id").getAsString());
            return image;
        } catch (Exception e) {
            IJ.log("[FLIMKit] could not fetch " + path + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void run() {
        try {
            var client = ConnectCommand.connect();
            var found = JsonParser.parseString(
                    client.zstackScan(ptuDir.getAbsolutePath())).getAsJsonObject();
            if (found.get("n_stacks").getAsInt() == 0) {
                ui.showDialog(nothingFound(ptuDir.getName()), "FLIMKit bridge");
                return;
            }
            IJ.log("[FLIMKit] " + describeStacks(found));
            var defaults = JsonParser.parseString(
                    client.zstackDefaults()).getAsJsonObject();
            var params = FitSettings.prompt(defaults, "zstack", "FLIMKit z-stack fit");
            if (params == null)
                return;
            var started = JsonParser.parseString(client.zstack(
                    requestBody(ptuDir.getAbsolutePath(), params))).getAsJsonObject();
            var result = BridgeJob.await(client, started.get("job").getAsString(),
                    "Z-stack fit");
            var fetched = new ArrayList<String>();
            var opened = new ArrayList<ImagePlus>();
            if (result != null && result.has("products")) {
                for (var element : result.getAsJsonArray("products")) {
                    var product = element.getAsJsonObject();
                    ImagePlus image = open(client, product, fetched);
                    if (image == null)
                        continue;
                    opened.add(shape(image, product));
                }
            }
            for (var image : opened)
                image.show();
            var pooled = pooledTable(result);
            if (pooled.size() > 0)
                pooled.show("FLIMKit pooled fit");
            var table = seriesTable(result);
            if (table.size() > 0)
                table.show("FLIMKit z-series");
            logLifetimes(result);
            if (opened.isEmpty())
                ui.showDialog("The fit finished but nothing could be opened.\n\n"
                        + "Output: " + started.get("output_dir").getAsString(),
                        "FLIMKit bridge");
            else if (!fetched.isEmpty())
                IJ.log("[FLIMKit] downloaded over the bridge as OME-TIFF: "
                        + String.join(", ", fetched));
        } catch (Exception e) {
            ui.showDialog("Could not fit the z-stack.\n\n" + e.getMessage(),
                    "FLIMKit bridge");
        }
    }
}
