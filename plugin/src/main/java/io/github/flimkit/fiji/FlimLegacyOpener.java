package io.github.flimkit.fiji;

import ij.ImagePlus;

import net.imagej.legacy.plugin.LegacyOpener;

import org.scijava.Priority;
import org.scijava.plugin.Plugin;

// ImageJ 1.x owns drag-and-drop and File>Open, and it only reaches the
// SciJava IOPlugins through DefaultLegacyOpener, which does nothing unless
// the user has ticked "SciJava I/O" under Edit>Options>ImageJ2. That option
// ships off, so a dropped .ptu died as "Format not supported or reader
// plugin not found". LegacyHooks asks every LegacyOpener in priority order
// and takes the first non-null answer, so claiming only the FLIM extensions
// here leaves every other format to the openers behind us.
@Plugin(type = LegacyOpener.class, priority = Priority.HIGH)
public class FlimLegacyOpener implements LegacyOpener {

    @Override
    public Object open(String source, int flags, boolean displayResult) {
        if (source == null)
            return null;
        String path = FlimFileOpener.decode(source);
        if (FlimFileOpener.claims(path) == false)
            return null;
        try {
            ImagePlus imp = new FlimFileOpener().open(path);
            if (imp != null && displayResult == true)
                imp.show();
            return imp;
        } catch (Exception e) {
            // Returning null here would hand the file back to ImageJ 1.x,
            // which would blame the format rather than say what went wrong.
            ij.IJ.error("FLIMKit", e.getMessage());
            return null;
        }
    }
}
