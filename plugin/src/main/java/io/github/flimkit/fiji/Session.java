package io.github.flimkit.fiji;

import ij.ImagePlus;
import ij.WindowManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Session {

    static final String DATASET_PROPERTY = "FLIMKit dataset";

    private static final Map<String, String> open = new LinkedHashMap<>();

    private Session() {}

    public static void opened(String id, String path) {
        open.remove(id);
        open.put(id, path);
    }

    public static void tag(ImagePlus image, String id) {
        if (image != null)
            image.setProperty(DATASET_PROPERTY, id);
    }

    public static String datasetIdOf(ImagePlus image) {
        if (image == null)
            return null;
        Object held = image.getProperty(DATASET_PROPERTY);
        return held == null ? null : held.toString();
    }

    public static String datasetId() {
        String front = datasetIdOf(WindowManager.getCurrentImage());
        if (front != null)
            return front;
        if (open.isEmpty())
            throw new IllegalStateException(
                    "No FLIM file is open. Use Plugins > FLIMKit > Open FLIM file "
                    + "first, so FLIMKit knows which decays to fit.");
        if (open.size() == 1)
            return open.keySet().iterator().next();
        throw new IllegalStateException(
                "FLIMKit has " + open.size() + " files open, and the window in front "
                + "is not one of them, so there is no way to tell which you mean. "
                + "Click the FLIMKit image window for the file you want, then run "
                + "this again.\n\nOpen: " + names());
    }

    static String names() {
        return open.values().stream()
                .map(FlimFileOpener::nameOf)
                .collect(Collectors.joining(", "));
    }

    public static String datasetPath() {
        return open.isEmpty() ? null : datasetPath(datasetId());
    }

    public static String datasetPath(String id) {
        return open.get(id);
    }

    public static boolean isOpen() {
        return !open.isEmpty();
    }

    static void clear() {
        open.clear();
    }
}
