package io.github.flimkit.fiji;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class BridgeClient {

    public static final int PROTOCOL_VERSION = 1;
    public static final String PLUGIN_VERSION = "0.2.0";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;
    private final String token;

    public BridgeClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public String baseUrl() {
        return baseUrl;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofMinutes(10));
    }

    private String text(HttpRequest built, String what)
            throws IOException, InterruptedException {
        var response = client.send(built,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new IOException(what + " returned " + response.statusCode()
                    + ": " + response.body());
        return response.body();
    }

    public String status() throws IOException, InterruptedException {
        return text(request("/v1/status").GET().build(), "GET status");
    }

    public String datasets() throws IOException, InterruptedException {
        return text(request("/v1/datasets").GET().build(), "GET datasets");
    }

    public String openDataset(String path) throws IOException, InterruptedException {
        var body = "{\"path\": " + com.google.gson.JsonParser.parseString(
                new com.google.gson.Gson().toJson(path)) + "}";
        return text(request("/v1/datasets")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), "POST datasets");
    }

    public Image image(String imageId) throws IOException, InterruptedException {
        var response = client.send(
                request("/v1/images/" + imageId + ".tif").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200)
            throw new IOException("GET " + imageId + " returned " + response.statusCode());
        return new Image(response.body(),
                response.headers().firstValue("X-FLIMKit-Value-Unit").orElse(""));
    }

    public String fetchRois() throws IOException, InterruptedException {
        return text(request("/v1/rois").GET().build(), "GET ROIs");
    }

    public String sendRois(String geojson) throws IOException, InterruptedException {
        return text(request("/v1/rois")
                .header("Content-Type", "application/geo+json")
                .POST(HttpRequest.BodyPublishers.ofString(geojson, StandardCharsets.UTF_8))
                .build(), "POST ROIs");
    }

    public String fitDefaults() throws IOException, InterruptedException {
        return text(request("/v1/fit/defaults").GET().build(), "GET fit defaults");
    }

    public String fitRois(String datasetId, String body)
            throws IOException, InterruptedException {
        return text(request("/v1/datasets/" + datasetId + "/fit/roi")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), "POST fit/roi");
    }

    public String planeList(String datasetId) throws IOException, InterruptedException {
        return text(request("/v1/datasets/" + datasetId + "/planes").GET().build(),
                "GET planes");
    }

    public Image plane(String datasetId, String name)
            throws IOException, InterruptedException {
        var response = client.send(
                request("/v1/datasets/" + datasetId + "/planes/" + name + ".tif")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200)
            throw new IOException("GET plane " + name + " returned "
                    + response.statusCode());
        return new Image(response.body(),
                response.headers().firstValue("X-FLIMKit-Value-Unit").orElse(""));
    }

    public byte[] planes(String datasetId, String names)
            throws IOException, InterruptedException {
        var response = client.send(
                request("/v1/datasets/" + datasetId + "/planes/stack.tif?planes=" + names)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200)
            throw new IOException("GET planes returned " + response.statusCode());
        return response.body();
    }

    public String fitPixels(String datasetId, String body)
            throws IOException, InterruptedException {
        return text(request("/v1/datasets/" + datasetId + "/fit/pixels")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), "POST fit/pixels");
    }

    public String jobStatus(String jobId) throws IOException, InterruptedException {
        return text(request("/v1/jobs/" + jobId).GET().build(), "GET job");
    }

    public String jobResult(String jobId) throws IOException, InterruptedException {
        return text(request("/v1/jobs/" + jobId + "?result").GET().build(),
                "GET job result");
    }

    public void cancelJob(String jobId) throws IOException, InterruptedException {
        client.send(request("/v1/jobs/" + jobId).DELETE().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public String phasorSummary(String datasetId, String options)
            throws IOException, InterruptedException {
        return text(request("/v1/datasets/" + datasetId + "/phasor"
                + (options == null || options.isEmpty() ? "" : "?" + options))
                .GET().build(), "GET phasor");
    }

    public String phasorPoints(String datasetId, int bins, String options)
            throws IOException, InterruptedException {
        return text(request("/v1/datasets/" + datasetId + "/phasor/points?bins=" + bins
                + (options == null || options.isEmpty() ? "" : "&" + options))
                .GET().build(), "GET phasor points");
    }

    public String phasorMask(String datasetId, String body)
            throws IOException, InterruptedException {
        return text(request("/v1/datasets/" + datasetId + "/phasor/mask")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), "POST phasor mask");
    }

    public String phasorSettings() throws IOException, InterruptedException {
        return text(request("/v1/phasor/settings").GET().build(), "GET phasor settings");
    }

    public String pipeline(String body) throws IOException, InterruptedException {
        return text(request("/v1/pipeline")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), "POST pipeline");
    }

    public String pipelineDefaults() throws IOException, InterruptedException {
        return text(request("/v1/pipeline/defaults").GET().build(),
                "GET pipeline defaults");
    }

    public record Image(byte[] tiff, String unit) {}
}
