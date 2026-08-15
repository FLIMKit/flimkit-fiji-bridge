# FLIMKit Fiji bridge

[![DOI](https://zenodo.org/badge/1334686126.svg)](https://doi.org/10.5281/zenodo.21951612)

Direct image and ROI exchange between [FLIMKit](https://github.com/FLIMKit/FLIMKit) and [Fiji](https://fiji.sc/).

## Current status

This add-on connects the bridge server to FLIMKit's public image and ROI bindings on `main`.

From FLIMKit, `Tools > Fiji Bridge...` now starts an authenticated loopback server and shows its address and pairing token. The server can:

1. fetch copies of the current fitted intensity and lifetime images as `float32` TIFF, with pixel-value units (`photons` or `ns`);
2. export the current FLIMKit Regions table as GeoJSON;
3. import a Fiji GeoJSON `FeatureCollection` into the current FLIMKit Regions table.

Communication stays on `127.0.0.1`. Image and ROI endpoints require the generated bearer token. The status endpoint is unauthenticated and reports only the protocol name and version. The server refuses non-loopback binding. Image reads use a 10-second timeout. ROI imports wait for FLIMKit to finish because the UI-thread mutation cannot be cancelled safely; this prevents a timeout from reporting failure while an import may still complete. The current Fiji script remains a headless transport check; a normal Fiji ROI Manager interface is still future work.

## Requirements

- Python 3.12 or newer, matching FLIMKit's requirement.
- A FLIMKit build containing the public image and ROI bindings merged in [FLIMKit PR #52](https://github.com/FLIMKit/FLIMKit/pull/52).
- A working Fiji installation.
- `pytest`, NumPy, and tifffile for the bridge tests.

Use a recent Fiji download with its bundled JDK. The bridge tests have been verified with bundled JDK 21 Fiji installations on Linux x86-64 and macOS ARM64.

The Fiji bridge client uses `java.net.HttpURLConnection`, which is available on Java 8. It does not use the Java 11-only `java.net.http.HttpClient` API. An old Fiji installation may still fail before the bridge script starts if Fiji's own JAR files require a newer Java runtime.

## Test the bridge on macOS ARM64

Clone the repository and create a clean environment:

```bash
git clone https://github.com/FLIMKit/flimkit-fiji-bridge.git
cd flimkit-fiji-bridge

python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install --no-deps \
  'flimkit @ git+https://github.com/FLIMKit/FLIMKit.git@main'
python -m pip install -e '.[test]'
```

Run all tests against the current ARM64 Fiji launcher:

```bash
FIJI_PATH='/Applications/Fiji.app/Contents/MacOS/fiji-macos-arm64' \
python -m pytest -q
```

Expected result:

```text
29 passed
```

The test is headless, so Fiji does not open a visible image window. Success means a real Fiji process fetched both TIFF images, checked their values, and sent the GeoJSON ROI back to Python.

## Other Fiji locations

Set `FIJI_PATH` to the launcher used by your installation.

Recent macOS ARM64 Fiji:

```text
/Applications/Fiji.app/Contents/MacOS/fiji-macos-arm64
```

Linux installation used during development:

```text
/home/zhenyuan/Applications/Fiji.app/fiji
```

The older macOS launcher below may select a legacy Java runtime and is not recommended:

```text
/Applications/Fiji.app/Contents/MacOS/ImageJ-macosx
```

## Old Fiji troubleshooting

If the old launcher reports an error such as:

```text
UnsupportedClassVersionError
```

or:

```text
Module javafx.base not found
```

install a current Fiji release with its bundled JDK and use its current platform launcher. These failures can happen while Fiji itself starts, before bridge code can display an error.

If the bridge script starts with a Java runtime older than Java 8, it stops with:

```text
Fiji Bridge requires Java 8 or newer. Please download a current Fiji release with its bundled JDK.
```

## What the current bridge runs

The Python side exposes five local endpoints:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v1/status` | Report protocol version |
| `GET` | `/v1/images/intensity.tif` | Return the current FLIMKit intensity image |
| `GET` | `/v1/images/lifetime.tif` | Return the current FLIMKit lifetime image |
| `GET` | `/v1/rois` | Export the current FLIMKit ROIs as GeoJSON |
| `POST` | `/v1/rois` | Import a GeoJSON `FeatureCollection` into FLIMKit |

Image IDs use an explicit allowlist. URL values are never passed to `getattr`. Each TIFF response includes `X-FLIMKit-Value-Unit`; Fiji stores that value in the image calibration. The production data source calls `get_current_images(app)`, `export_rois_geojson(app)`, and `import_rois_geojson(app, payload)` from `flimkit.plugins`.

The image scope is fitted lifetime and photon-count intensity only. Raw per-pixel decay histograms are not transferred. A raw-decay binding would need a separate data and metadata contract.

The Fiji script is:

```text
fiji/FijiBridge.groovy
```

The Python bridge server is:

```text
flimkit_fiji_bridge/server.py
```

## Run without Fiji

To run the Python and packaging checks while skipping the live Fiji process:

```bash
python -m pytest -q
```

The live test skips unless `FIJI_PATH` is set.

## Current limits

The bridge does not yet:

- use Fiji's ROI Manager;
- provide normal Fiji buttons for fetching images and sending or receiving ROIs;
- perform image registration;
- define the final production protocol.

Registration will remain a Fiji-side operation. The planned Fiji interface will reject mismatched image dimensions rather than silently rescale ROI coordinates.

## Next implementation steps

1. Turn the current Fiji script into a small command with a normal user interface.
2. Add bidirectional Fiji ROI Manager conversion.
3. Test registered ROI transfer with Fiji's existing registration tools.

## Development

```bash
python -m pip install -e '.[test]'
python -m pytest -q
```

Please add a test for each behavior change. Keep module imports side-effect free so FLIMKit can inspect the add-on on headless systems.

## Acknowledgement

Development was assisted by OpenAI's GPT-5.6 Sol through Hermes Agent by Nous Research, under Zhen Yuan Yeo's direction and review. The human contributors remain responsible for the implementation and scientific interpretation.

## License

MIT. See `LICENSE.md`.
