package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZStackCommandTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static final String PRODUCT = """
            {"file": "/data/out/RegionA.ome.zarr",
             "group_dir": "/data/out/RegionA",
             "image_id": "RegionA",
             "format": "ome-zarr",
             "channels": ["intensity", "tau_mean_int", "alpha_1"],
             "n_z": 12,
             "voxel_size_um": [2.0, 0.284, 0.284]}
            """;

    @Test
    public void theFolderGoesIntoTheRequest() {
        var body = parse(ZStackCommand.requestBody("/data/slices",
                parse("{\"n_exp\": 2}")));

        assertEquals("/data/slices", body.get("ptu_dir").getAsString());
        assertEquals(2, body.getAsJsonObject("params").get("n_exp").getAsInt());
    }

    @Test
    public void emptySettingsAreLeftOutSoTheBridgeUsesItsOwn() {
        var body = parse(ZStackCommand.requestBody("/data/slices", new JsonObject()));

        assertFalse(body.has("params"));
    }

    @Test
    public void aScanIsDescribedBeforeAnythingRuns() {
        String described = ZStackCommand.describeStacks(parse("""
                {"n_stacks": 2,
                 "stacks": [{"label": "RegionA", "n_slices": 12,
                             "z_first": 1, "z_last": 12},
                            {"label": "RegionB", "n_slices": 8,
                             "z_first": 1, "z_last": 8}]}
                """));

        assertTrue(described.startsWith("Found 2 z-stacks:"), described);
        assertTrue(described.contains("RegionA - 12 slices (z 1 to 12)"), described);
    }

    @Test
    public void oneStackReadsAsSingular() {
        String described = ZStackCommand.describeStacks(parse("""
                {"n_stacks": 1,
                 "stacks": [{"label": "RegionA", "n_slices": 3,
                             "z_first": 1, "z_last": 3}]}
                """));

        assertTrue(described.startsWith("Found 1 z-stack:"), described);
    }

    @Test
    public void anEmptyFolderSaysWhatTheNamesShouldLookLike() {
        String said = ZStackCommand.nothingFound("slices");

        assertTrue(said.contains("region_z1.ptu"), said);
        assertTrue(said.contains("region_t1_s1_z1.ptu"), said);
    }

    @Test
    public void theVoxelSizeIsReadOffTheProduct() {
        var product = parse(PRODUCT);

        assertEquals(2.0, ZStackCommand.voxel(product, 0, 1.0));
        assertEquals(0.284, ZStackCommand.voxel(product, 2, 1.0));
        assertEquals(3, ZStackCommand.channelCount(product));
    }

    @Test
    public void aProductWithoutAVoxelSizeFallsBack() {
        var product = parse("{\"image_id\": \"RegionA\"}");

        assertEquals(1.0, ZStackCommand.voxel(product, 0, 1.0));
        assertEquals(1, ZStackCommand.channelCount(product));
    }

    @Test
    public void theOpenedStackIsLaidOutAsAHyperstack() {
        var shaped = ZStackCommand.shape(
                ij.IJ.createImage("t", "32-bit black", 4, 5, 36), parse(PRODUCT));

        assertEquals(3, shaped.getNChannels());
        assertEquals(12, shaped.getNSlices());
        assertEquals(2.0, shaped.getCalibration().pixelDepth);
        assertEquals(0.284, shaped.getCalibration().pixelWidth);
        assertEquals("ns", shaped.getCalibration().getValueUnit());
        assertTrue(shaped.getTitle().contains("RegionA"), shaped.getTitle());
    }

    @Test
    public void manyChannelsAreShownOneAtATimeNotComposited() {
        var product = parse("""
                {"file": "x", "image_id": "RegionA", "format": "ome-tiff",
                 "n_z": 8,
                 "channels": ["intensity", "tau_mean_int", "tau_mean_amp",
                              "tau_mean", "alpha_1", "alpha_2", "chi2_r",
                              "calibrated_chi2_r"],
                 "units": ["photons", "ns", "ns", "ns", "", "", "", ""]}
                """);

        var shaped = ZStackCommand.shape(
                ij.IJ.createImage("t", "32-bit black", 4, 5, 64), product);

        assertEquals(8, shaped.getNChannels());
        assertTrue(shaped instanceof ij.CompositeImage, "needs a channel slider");
        assertEquals(ij.CompositeImage.GRAYSCALE,
                ((ij.CompositeImage) shaped).getMode(),
                "composite mode warns and misleads above seven channels");
    }

    @Test
    public void eachChannelSaysWhichMapItIsAndInWhatUnit() {
        var product = parse("""
                {"file": "x", "image_id": "RegionA", "format": "ome-tiff",
                 "n_z": 12,
                 "channels": ["intensity", "tau_mean_int", "alpha_1"],
                 "units": ["photons", "ns", ""]}
                """);

        var shaped = ZStackCommand.shape(
                ij.IJ.createImage("t", "32-bit black", 4, 5, 36), product);

        assertEquals("intensity (photons)",
                shaped.getStack().getSliceLabel(shaped.getStackIndex(1, 1, 1)));
        assertEquals("tau_mean_int (ns)",
                shaped.getStack().getSliceLabel(shaped.getStackIndex(2, 1, 1)));
        assertEquals("alpha_1",
                shaped.getStack().getSliceLabel(shaped.getStackIndex(3, 1, 1)),
                "a unitless map should not gain empty brackets");
    }

    private static final String FINISHED = """
            {"output_dir": "/data/out",
             "stacks": [{"label": "Series008", "n_slices": 2,
                         "taus_ns": [0.4123, 2.8710],
                         "pooled": {"taus_ns": [0.4123, 2.8710], "nexp": 2,
                                    "total_pooled_photons": 1731281.0,
                                    "estimate_irf": "machine_irf",
                                    "user_supplied_tau": false,
                                    "calibrated_chi2_pearson": 151.4},
                         "z_series_csv": "/data/out/Series008/Series008_zseries.csv",
                         "reference_fit": "/data/out/Series008/ref.json",
                         "z_series": [
                            {"z": 1, "tau_mean_mean": 1.92, "n_pixels_fitted": 4100,
                             "path": "/data/Series008_z1.ptu"},
                            {"z": 2, "tau_mean_mean": 1.88, "n_pixels_fitted": 4300,
                             "path": "/data/Series008_z2.ptu"}]}]}
            """;

    @Test
    public void everySliceGetsARowOfNumbers() {
        var table = ZStackCommand.seriesTable(parse(FINISHED));

        assertEquals(2, table.size());
        assertEquals("Series008 z1", table.getLabel(0));
        assertEquals(1.92, table.getValue("tau_mean_mean", 0), 1e-9);
        assertEquals(4300.0, table.getValue("n_pixels_fitted", 1), 1e-9);
    }

    @Test
    public void theSourcePathIsNotAMeasurement() {
        var table = ZStackCommand.seriesTable(parse(FINISHED));

        for (int column = 0; column <= table.getLastColumn(); column++)
            assertFalse("path".equals(table.getColumnHeading(column)),
                    "the ptu path is not a number to tabulate");
    }

    @Test
    public void theLockedLifetimesAreReported() {
        var lines = ZStackCommand.lifetimeLines(parse(FINISHED));

        assertTrue(lines.get(0).contains("tau1 = 0.4123 ns"), lines.get(0));
        assertTrue(lines.get(0).contains("tau2 = 2.8710 ns"), lines.get(0));
        assertTrue(lines.get(0).contains("locked across all 2 slices"), lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Series008_zseries.csv")));
    }

    @Test
    public void aStackThatFailedSaysSoInsteadOfLifetimes() {
        var lines = ZStackCommand.lifetimeLines(parse("""
                {"stacks": [{"label": "Series009", "error": "no slice maps"}]}
                """));

        assertEquals(1, lines.size());
        assertEquals("Series009: no slice maps", lines.get(0));
    }

    @Test
    public void thePooledFitGetsItsOwnRowPerStack() {
        var table = ZStackCommand.pooledTable(parse(FINISHED));

        assertEquals(1, table.size());
        assertEquals("Series008", table.getLabel(0));
        assertEquals(0.4123, table.getValue("taus_ns 1", 0), 1e-9);
        assertEquals(2.8710, table.getValue("taus_ns 2", 0), 1e-9);
        assertEquals(1731281.0, table.getValue("total_pooled_photons", 0), 1e-6);
        assertEquals("machine_irf",
                table.getStringValue("estimate_irf", 0));
    }

    @Test
    public void aStackWithNoPooledFitIsLeftOutOfThatTable() {
        var table = ZStackCommand.pooledTable(parse("""
                {"stacks": [{"label": "Series009", "error": "no slice maps"}]}
                """));

        assertEquals(0, table.size());
    }

    @Test
    public void aResultWithNoStacksTabulatesNothing() {
        assertEquals(0, ZStackCommand.seriesTable(parse("{}")).size());
        assertEquals(0, ZStackCommand.pooledTable(parse("{}")).size());
        assertTrue(ZStackCommand.lifetimeLines(null).isEmpty());
    }

    @Test
    public void aStackThatDoesNotAddUpIsLeftAlone() {
        var shaped = ZStackCommand.shape(
                ij.IJ.createImage("t", "32-bit black", 4, 5, 10), parse(PRODUCT));

        assertEquals(1, shaped.getNChannels());
        assertEquals(2.0, shaped.getCalibration().pixelDepth);
    }

    @Test
    public void aZarrStoreIsNeverHandedToImageJ() throws Exception {
        var store = java.nio.file.Files.createTempDirectory("RegionA.ome.zarr");

        assertFalse(ZStackCommand.worthOpeningDirectly(
                parse(PRODUCT), store.toString()),
                "opening a store pops an unsupported-format dialog");
    }

    @Test
    public void aTiffThatIsThereIsOpenedDirectly() throws Exception {
        var file = java.nio.file.Files.createTempFile("RegionA", ".ome.tif");
        var product = parse("""
                {"file": "x", "image_id": "RegionA", "format": "ome-tiff"}
                """);

        assertTrue(ZStackCommand.worthOpeningDirectly(product, file.toString()));
    }

    @Test
    public void aTiffOnAMachineWeCannotSeeGoesStraightToTheFallback() {
        var product = parse("""
                {"file": "x", "image_id": "RegionA", "format": "ome-tiff"}
                """);

        assertFalse(ZStackCommand.worthOpeningDirectly(
                product, "/nowhere/RegionA.ome.tif"));
    }

    @Test
    public void anErroredJobReportsItsMessageRatherThanWaiting() {
        String reason = BridgeJob.reasonOf(parse("""
                {"state": "error",
                 "error": {"type": "ValueError", "message": "no such folder"}}
                """));

        assertEquals(": no such folder", reason);
        assertEquals("", BridgeJob.reasonOf(parse("{\"state\": \"done\"}")));
    }
}
