package io.github.flimkit.fiji;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FlimLegacyOpenerTest {

    private final FlimLegacyOpener opener = new FlimLegacyOpener();

    @Test
    void leavesOtherFormatsToImageJ() {
        assertNull(opener.open("/data/cells.tif", 0, false));
        assertNull(opener.open("C:\\data\\cells.png", 0, false));
        assertNull(opener.open("/data/notes.txt", 0, false));
    }

    @Test
    void leavesNullAlone() {
        assertNull(opener.open(null, 0, false));
    }

    @Test
    void claimsFlimExtensionsOnEitherPlatform() {
        for (String ext : FlimFileOpener.EXTENSIONS) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    FlimFileOpener.claims("/data/sample." + ext), ext);
            org.junit.jupiter.api.Assertions.assertTrue(
                    FlimFileOpener.claims("C:\\data\\sample." + ext.toUpperCase()), ext);
        }
    }

    @Test
    void claimsWindowsFileUri() {
        org.junit.jupiter.api.Assertions.assertTrue(
                FlimFileOpener.claims(FlimFileOpener.decode("file:/C:/data/x.ptu")));
    }
}
