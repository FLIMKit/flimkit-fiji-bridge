package io.github.flimkit.fiji;

import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ByteProcessor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    private static ImagePlus blank(String title) {
        return new ImagePlus(title, new ByteProcessor(4, 4));
    }

    @BeforeEach
    @AfterEach
    void reset() {
        WindowManager.setTempCurrentImage(null);
        Session.clear();
    }

    @Test
    void theFrontWindowDecidesWhichDatasetIsUsed() {
        Session.opened("ds_1", "/data/first.ptu");
        ImagePlus first = blank("FLIMKit first");
        Session.tag(first, "ds_1");
        Session.opened("ds_2", "/data/second.ptu");
        Session.tag(blank("FLIMKit second"), "ds_2");

        WindowManager.setTempCurrentImage(first);

        assertEquals("ds_1", Session.datasetId());
    }

    @Test
    void theLastOpenedIsStillUsedWhenItIsTheOnlyOne() {
        Session.opened("ds_1", "/data/first.ptu");
        WindowManager.setTempCurrentImage(blank("something else"));

        assertEquals("ds_1", Session.datasetId());
    }

    @Test
    void anUntaggedWindowWithSeveralOpenIsAnError() {
        Session.opened("ds_1", "/data/first.ptu");
        Session.opened("ds_2", "/data/second.ptu");
        WindowManager.setTempCurrentImage(blank("something else"));

        var thrown = assertThrows(IllegalStateException.class, Session::datasetId);

        assertTrue(thrown.getMessage().contains("first.ptu"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("second.ptu"), thrown.getMessage());
    }

    @Test
    void nothingOpenSaysToOpenAFile() {
        var thrown = assertThrows(IllegalStateException.class, Session::datasetId);

        assertTrue(thrown.getMessage().contains("Open FLIM file"), thrown.getMessage());
    }

    @Test
    void reopeningTheSameDatasetDoesNotListItTwice() {
        Session.opened("ds_1", "/data/first.ptu");
        Session.opened("ds_1", "/data/first.ptu");
        WindowManager.setTempCurrentImage(blank("something else"));

        assertEquals("ds_1", Session.datasetId());
    }
}
