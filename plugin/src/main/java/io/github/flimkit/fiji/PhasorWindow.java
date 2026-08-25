package io.github.flimkit.fiji;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.IJ;
import ij.gui.Roi;
import ij.plugin.RoiScaler;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.plugin.frame.RoiManager;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class PhasorWindow extends JPanel {

    private static final int BINS = 256;
    private static final Color[] COLOURS = {
        new Color(0xFF6B6B), new Color(0x4ECDC4), new Color(0xFFE66D),
        new Color(0x95E1D3), new Color(0xC7CEEA), new Color(0xFF8C42),
    };

    private final BridgeClient client;
    private final String datasetId;
    private final List<PhasorPlot.Cursor> cursors = new ArrayList<>();
    private final DefaultListModel<String> lines = new DefaultListModel<>();
    private JsonObject options = new JsonObject();

    private int[] counts = new int[0];
    private int maxCount = 1;
    private List<double[]> outline;
    private boolean drawing;
    private int dragging = -1;

    public PhasorWindow(BridgeClient client, String datasetId) {
        this.client = client;
        this.datasetId = datasetId;
        setPreferredSize(new Dimension(520, 380));
        var mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (drawing) {
                    outline = new ArrayList<>();
                    outline.add(new double[] {e.getX(), e.getY()});
                    return;
                }
                dragging = nearest(e.getX(), e.getY());
                if (dragging < 0 && cursors.size() < COLOURS.length) {
                    cursors.add(PhasorPlot.Cursor.ellipse(
                            nextId(), toG(e.getX()), toS(e.getY()), 0.05));
                    dragging = cursors.size() - 1;
                }
                refresh();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (outline != null) {
                    double[] last = outline.get(outline.size() - 1);
                    if (Math.hypot(e.getX() - last[0], e.getY() - last[1]) >= 3.0)
                        outline.add(new double[] {e.getX(), e.getY()});
                    repaint();
                    return;
                }
                if (dragging >= 0) {
                    var was = cursors.get(dragging);
                    cursors.set(dragging, PhasorPlot.Cursor.ellipse(
                            was.id(), toG(e.getX()), toS(e.getY()), was.radius()));
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (outline != null) {
                    finishOutline();
                    return;
                }
                dragging = -1;
                refresh();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private String nextId() {
        int n = 1;
        while (true) {
            String candidate = "c" + n;
            boolean taken = false;
            for (var cursor : cursors) {
                if (cursor.id().equals(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken)
                return candidate;
            n++;
        }
    }

    void setDrawing(boolean on) {
        drawing = on;
    }

    private void finishOutline() {
        var traced = outline;
        outline = null;
        if (cursors.size() >= COLOURS.length) {
            IJ.showStatus("Six cursors is the limit, matching FLIMKit's palette.");
            refresh();
            return;
        }
        if (traced.size() < 3) {
            IJ.showStatus("That outline had fewer than three points. Drag to trace one.");
            refresh();
            return;
        }
        var vertices = new ArrayList<double[]>();
        for (var point : traced)
            vertices.add(new double[] {toG(point[0]), toS(point[1])});
        cursors.add(PhasorPlot.Cursor.polygon(nextId(), vertices));
        refresh();
    }

    private double toX(double g) {
        return (g - PhasorPlot.G_MIN) / (PhasorPlot.G_MAX - PhasorPlot.G_MIN) * getWidth();
    }

    private double toY(double s) {
        return getHeight()
                - (s - PhasorPlot.S_MIN) / (PhasorPlot.S_MAX - PhasorPlot.S_MIN) * getHeight();
    }

    private double toG(double x) {
        return PhasorPlot.G_MIN + x / getWidth() * (PhasorPlot.G_MAX - PhasorPlot.G_MIN);
    }

    private double toS(double y) {
        return PhasorPlot.S_MIN
                + (getHeight() - y) / getHeight() * (PhasorPlot.S_MAX - PhasorPlot.S_MIN);
    }

    private int nearest(double x, double y) {
        for (int i = 0; i < cursors.size(); i++) {
            var cursor = cursors.get(i);
            if (cursor.vertices() != null)
                continue;
            if (Math.hypot(toX(cursor.g()) - x, toY(cursor.s()) - y) < 12)
                return i;
        }
        return -1;
    }

    private double minPhotons = PhasorPlot.DEFAULT_MIN_PHOTONS;

    void loadDensity() throws Exception {
        String query = PhasorPlot.optionsQuery(options);
        query = (query.isEmpty() ? "" : query + "&") + "min_photons=" + minPhotons;
        var payload = JsonParser.parseString(client.phasorPoints(
                datasetId, BINS, query)).getAsJsonObject();
        counts = PhasorPlot.decodeCounts(payload.get("counts").getAsString());
        maxCount = Math.max(1, payload.get("max_count").getAsInt());
    }

    static void report(String line) {
        IJ.log(line);
        System.out.println(line);
    }

    static String range(List<double[]> vertices, int axis) {
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (var vertex : vertices) {
            low = Math.min(low, vertex[axis]);
            high = Math.max(high, vertex[axis]);
        }
        return String.format("%.3f..%.3f", low, high);
    }

    void refresh() {
        repaint();
        lines.clear();
        if (cursors.isEmpty())
            return;
        try {
            String body = PhasorPlot.requestBody(cursors, options, false, minPhotons);
            var reply = JsonParser.parseString(client.phasorMask(datasetId, body))
                    .getAsJsonObject();
            boolean empty = false;
            for (var element : reply.getAsJsonArray("cursors")) {
                var entry = element.getAsJsonObject();
                lines.addElement(PhasorPlot.describe(entry));
                if (entry.get("n_pixels").getAsInt() == 0)
                    empty = true;
            }
            report("[FLIMKit phasor] min_photons=" + minPhotons + " cursors=" + cursors.size()
                    + " panel=" + getWidth() + "x" + getHeight()
                    + " anyEmpty=" + empty);
            for (var cursor : cursors) {
                if (cursor.vertices() == null) {
                    report("  " + cursor.id() + " ellipse g=" + cursor.g()
                            + " s=" + cursor.s() + " r=" + cursor.radius());
                    continue;
                }
                report("  " + cursor.id() + " polygon vertices="
                        + cursor.vertices().size()
                        + " g " + range(cursor.vertices(), 0)
                        + " s " + range(cursor.vertices(), 1));
            }
            if (empty) {
                report("  reply: " + reply);
                report("  request: " + body);
            }
        } catch (Exception e) {
            IJ.showStatus("Could not count phasor pixels: " + e.getMessage());
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x101418));
        g.fillRect(0, 0, getWidth(), getHeight());
        double cellW = getWidth() / (double) BINS;
        double cellH = getHeight() / (double) BINS;
        double logMax = Math.log1p(maxCount);
        for (int row = 0; row < BINS && counts.length == BINS * BINS; row++) {
            for (int col = 0; col < BINS; col++) {
                int count = counts[row * BINS + col];
                if (count == 0)
                    continue;
                float level = (float) (Math.log1p(count) / logMax);
                g.setColor(Color.getHSBColor((240 - 240 * level) / 360f, 0.85f,
                        0.25f + 0.75f * level));
                g.fillRect((int) (col * cellW),
                        (int) (getHeight() - (row + 1) * cellH),
                        (int) Math.ceil(cellW), (int) Math.ceil(cellH));
            }
        }
        g.setColor(new Color(0x8899AA));
        double previousX = toX(0), previousY = toY(0);
        for (int i = 1; i <= 180; i++) {
            double angle = Math.PI * i / 180.0;
            double x = toX(0.5 + 0.5 * Math.cos(angle));
            double y = toY(0.5 * Math.sin(angle));
            g.drawLine((int) previousX, (int) previousY, (int) x, (int) y);
            previousX = x;
            previousY = y;
        }
        for (int i = 0; i < cursors.size(); i++) {
            var cursor = cursors.get(i);
            g.setColor(COLOURS[i % COLOURS.length]);
            if (cursor.vertices() != null) {
                var vertices = cursor.vertices();
                for (int p = 0; p < vertices.size(); p++) {
                    var a = vertices.get(p);
                    var b = vertices.get((p + 1) % vertices.size());
                    g.drawLine((int) toX(a[0]), (int) toY(a[1]),
                            (int) toX(b[0]), (int) toY(b[1]));
                }
                continue;
            }
            int rx = (int) (cursor.radius() / (PhasorPlot.G_MAX - PhasorPlot.G_MIN) * getWidth());
            int ry = (int) (cursor.radius() / (PhasorPlot.S_MAX - PhasorPlot.S_MIN) * getHeight());
            g.drawOval((int) toX(cursor.g()) - rx, (int) toY(cursor.s()) - ry, rx * 2, ry * 2);
        }
        if (outline != null && outline.size() > 1) {
            g.setColor(COLOURS[cursors.size() % COLOURS.length]);
            for (int i = 1; i < outline.size(); i++)
                g.drawLine((int) outline.get(i - 1)[0], (int) outline.get(i - 1)[1],
                        (int) outline.get(i)[0], (int) outline.get(i)[1]);
        }
    }

    static Roi roiFromLabels(byte[] labels, int width, int height, int label,
                             int binning) {
        var mask = new ByteProcessor(width, height);
        int found = 0;
        for (int i = 0; i < labels.length && i < width * height; i++) {
            if ((labels[i] & 0xFF) != label)
                continue;
            mask.set(i % width, i / width, 255);
            found++;
        }
        if (found == 0)
            return null;
        mask.setThreshold(128, 255, ImageProcessor.NO_LUT_UPDATE);
        Roi roi = new ThresholdToSelection().convert(mask);
        if (roi == null)
            return null;
        if (binning > 1) {
            var scaled = RoiScaler.scale(roi, binning, binning, false);
            scaled.setLocation(roi.getBounds().x * binning, roi.getBounds().y * binning);
            roi = scaled;
        }
        return roi;
    }

    void createRois() throws Exception {
        RoiManager manager = RoiManager.getRoiManager();
        int added = 0;
        for (int i = 0; i < cursors.size(); i++) {
            var cursor = cursors.get(i);
            var reply = JsonParser.parseString(client.phasorMask(datasetId,
                    PhasorPlot.requestBody(List.of(cursor), options, true, minPhotons)))
                    .getAsJsonObject();
            int binning = reply.get("binning").isJsonNull()
                    ? 1 : reply.get("binning").getAsInt();
            byte[] labels = java.util.Base64.getDecoder().decode(
                    reply.get("labels").getAsString());
            Roi roi = roiFromLabels(labels, reply.get("width").getAsInt(),
                    reply.get("height").getAsInt(), 1, binning);
            if (roi == null)
                continue;
            roi.setName("Phasor " + cursor.id());
            roi.setStrokeColor(COLOURS[i % COLOURS.length]);
            manager.addRoi(roi);
            added++;
        }
        IJ.showStatus(added == 0
                ? "No pixels fell inside the cursors."
                : "Added " + added + " phasor region(s) to the ROI Manager.");
    }

    public JFrame open() throws Exception {
        loadDensity();
        var frame = new JFrame("FLIMKit phasor");
        var root = new JPanel(new BorderLayout());
        root.add(this, BorderLayout.CENTER);
        var side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        var summary = JsonParser.parseString(client.phasorSummary(
                datasetId, PhasorPlot.optionsQuery(options))).getAsJsonObject();
        side.add(new JLabel(String.format("%.2f MHz",
                summary.get("frequency_mhz").getAsDouble())));
        side.add(new JScrollPane(new JList<>(lines)));
        var draw = new JToggleButton("Draw region");
        draw.addActionListener(e -> setDrawing(draw.isSelected()));
        side.add(draw);
        var settings = new JButton("Settings...");
        settings.addActionListener(e -> {
            try {
                var defaults = JsonParser.parseString(
                        client.phasorSettings()).getAsJsonObject();
                var chosen = FitSettings.prompt(defaults, "phasor", "FLIMKit phasor");
                if (chosen == null)
                    return;
                options = chosen;
                loadDensity();
                refresh();
            } catch (Exception ex) {
                IJ.error("FLIMKit phasor", ex.getMessage());
            }
        });
        side.add(settings);
        var remove = new JButton("Remove last");
        remove.addActionListener(e -> {
            if (!cursors.isEmpty())
                cursors.remove(cursors.size() - 1);
            refresh();
        });
        side.add(remove);
        var create = new JButton("Add to ROI Manager");
        create.addActionListener(e -> {
            try {
                createRois();
            } catch (Exception ex) {
                IJ.error("FLIMKit phasor", ex.getMessage());
            }
        });
        side.add(create);
        root.add(side, BorderLayout.EAST);
        frame.setContentPane(root);
        frame.pack();
        frame.setVisible(true);
        return frame;
    }
}
