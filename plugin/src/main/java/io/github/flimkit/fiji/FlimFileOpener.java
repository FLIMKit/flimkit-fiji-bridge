package io.github.flimkit.fiji;

import ij.ImagePlus;

import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.location.Location;
import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Plugin(type = IOPlugin.class, priority = 100.0)
public class FlimFileOpener extends AbstractIOPlugin<ImagePlus> {

    static final Set<String> EXTENSIONS = Set.of(
            "ptu", "sdt", "photons", "ifli", "tdflim", "iss-tdflim", "bin", "phu");

    @Override
    public Class<ImagePlus> getDataType() {
        return ImagePlus.class;
    }

    static String extensionOf(String source) {
        if (source == null)
            return "";
        String path = source;
        int query = path.indexOf('?');
        if (query >= 0)
            path = path.substring(0, query);
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1)
            return "";
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean supportsOpen(String source) {
        return EXTENSIONS.contains(extensionOf(source));
    }

    @Override
    public boolean supportsOpen(Location source) {
        return source != null && supportsOpen(pathOf(source));
    }

    @Override
    public ImagePlus open(Location source) throws IOException {
        return open(pathOf(source));
    }

    static String pathOf(Location source) {
        var uri = source.getURI();
        if (uri == null)
            return "";
        String path = uri.getPath();
        return path == null ? decode(uri.toString()) : path;
    }

    @Override
    public ImagePlus open(String source) throws IOException {
        String path = decode(source);
        try {
            var client = ConnectCommand.connect();
            String id = OpenDatasetCommand.open(client, path);
            return FetchImagesCommand.fetchPlane(
                    client, id, "intensity", "FLIMKit " + nameOf(path));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "FLIMKit could not open " + path + ": " + e.getMessage()
                    + ". Start FLIMKit, or run flimkit-bridge.", e);
        }
    }

    static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    static String decode(String source) {
        String path = source;
        if (path.startsWith("file:"))
            path = path.substring("file:".length());
        return java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
    }
}
