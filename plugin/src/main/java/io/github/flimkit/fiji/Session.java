package io.github.flimkit.fiji;

public class Session {

    private static String datasetId;
    private static String datasetPath;

    private Session() {}

    public static void opened(String id, String path) {
        datasetId = id;
        datasetPath = path;
    }

    public static String datasetId() {
        if (datasetId == null)
            throw new IllegalStateException(
                    "No FLIM file is open. Use Plugins > FLIMKit > Open FLIM file "
                    + "first, so FLIMKit knows which decays to fit.");
        return datasetId;
    }

    public static String datasetPath() {
        return datasetPath;
    }

    public static boolean isOpen() {
        return datasetId != null;
    }

    static void clear() {
        datasetId = null;
        datasetPath = null;
    }
}
