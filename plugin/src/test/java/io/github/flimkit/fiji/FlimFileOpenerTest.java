package io.github.flimkit.fiji;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlimFileOpenerTest {

    @Test
    void aWindowsFileUriDoesNotKeepTheSlashBeforeTheDriveLetter() {
        // what URI.getPath() hands back for a drag and drop on Windows. The
        // leading slash is not part of the path and nothing can open it.
        assertEquals("C:/data/cells.ptu",
                FlimFileOpener.stripDriveSlash("/C:/data/cells.ptu"));
    }

    @Test
    void aPosixPathIsLeftAlone() {
        assertEquals("/Users/someone/cells.ptu",
                FlimFileOpener.stripDriveSlash("/Users/someone/cells.ptu"));
        assertEquals("/data/x.sdt", FlimFileOpener.stripDriveSlash("/data/x.sdt"));
    }

    @Test
    void decodingAFileUriGivesAPathThatCanBeOpened() {
        String posix = FlimFileOpener.decode("file:/data/my%20cells.ptu");
        assertTrue(posix.endsWith("my cells.ptu"), posix);
        assertFalse(posix.startsWith("file:"), posix);
    }

    @Test
    void decodingNeverLeavesASlashBeforeADriveLetter() {
        for (String uri : new String[] {
                "file:/C:/data/cells.ptu",
                "file://C:/data/cells.ptu",
                "file:///C:/data/cells.ptu" }) {
            String path = FlimFileOpener.decode(uri).replace('\\', '/');
            assertFalse(path.startsWith("/C:"), uri + " gave " + path);
            assertTrue(path.toUpperCase().startsWith("C:"), uri + " gave " + path);
        }
    }

    @Test
    void theWindowSeparatorIsRecognisedWhenNamingTheImage() {
        assertEquals("cells.ptu", FlimFileOpener.nameOf("C:\\data\\cells.ptu"));
        assertEquals("cells.ptu", FlimFileOpener.nameOf("/data/cells.ptu"));
        assertEquals("cells.ptu", FlimFileOpener.nameOf("cells.ptu"));
    }

    @Test
    void theExtensionDecidesWhatIsSupported() {
        assertTrue(FlimFileOpener.EXTENSIONS.contains("ptu"));
        for (String path : new String[] {
                "C:\\data\\cells.PTU", "/data/cells.ptu", "file:/C:/data/cells.ptu" })
            assertEquals("ptu", FlimFileOpener.extensionOf(path), path);
        assertEquals("", FlimFileOpener.extensionOf("C:\\data\\noextension"));
    }
}
