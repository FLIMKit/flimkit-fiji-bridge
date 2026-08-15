# FLIMKit Fiji bridge

Direct image and ROI exchange between [FLIMKit](https://github.com/FLIMKit/FLIMKit) and [Fiji](https://fiji.sc/).

## Current status

This repository currently contains a tested direct-communication demo. It is not yet the finished interactive plugin.

The demo proves that Fiji can:

1. fetch synthetic intensity and lifetime images from Python as `float32` TIFF;
2. preserve the image shape and pixel values;
3. send a named polygon ROI back to Python as GeoJSON.

Communication stays on `127.0.0.1` and requires a bearer token. The user does not manually save or reopen the transferred TIFF or GeoJSON data.

## Requirements

- Python 3.12 or newer, matching FLIMKit's requirement.
- FLIMKit 0.10.0 or newer.
- A working Fiji installation.
- `pytest`, NumPy, and tifffile for the demo tests.

Use a recent Fiji download with its bundled JDK. The demo has been verified with bundled JDK 21 Fiji installations on Linux x86-64 and macOS ARM64.

The Fiji demo client uses `java.net.HttpURLConnection`, which is available on Java 8. It does not use the Java 11-only `java.net.http.HttpClient` API. An old Fiji installation may still fail before the bridge script starts if Fiji's own JAR files require a newer Java runtime.

## Try the demo on macOS ARM64

Clone the repository and create a clean environment:

```bash
git clone https://github.com/FLIMKit/flimkit-fiji-bridge.git
cd flimkit-fiji-bridge

git switch demo/direct-communication

python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install --no-deps \
  'flimkit @ git+https://github.com/FLIMKit/FLIMKit'
python -m pip install -e '.[test]'
```

Run all tests against the current ARM64 Fiji launcher:

```bash
FIJI_PATH='/Applications/Fiji.app/Contents/MacOS/fiji-macos-arm64' \
python -m pytest -q
```

Expected result:

```text
12 passed
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

## What the demo runs

The Python side exposes four local endpoints:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v1/status` | Report protocol version |
| `GET` | `/v1/images/intensity.tif` | Return the intensity image |
| `GET` | `/v1/images/lifetime.tif` | Return the lifetime image |
| `POST` | `/v1/rois` | Receive a GeoJSON `FeatureCollection` |

Image IDs are resolved through an explicit dictionary. URL values are never passed to `getattr`.

The Fiji script is:

```text
fiji/FijiBridgeDemo.groovy
```

The Python demo server is:

```text
flimkit_fiji_bridge/demo_server.py
```

## Run without Fiji

To run the Python and packaging checks while skipping the live Fiji process:

```bash
python -m pytest -q
```

The live test skips unless `FIJI_PATH` is set.

## Limits of this demo

The demo does not yet:

- read FLIMKit's current intensity or lifetime images;
- use FLIMKit's real ROI manager;
- use Fiji's ROI Manager;
- open an interactive bridge window;
- perform image registration;
- define the final production protocol.

Registration will remain a Fiji-side operation. The planned bridge will reject mismatched image dimensions rather than silently rescale ROI coordinates.

## Next implementation steps

1. Fix and test FLIMKit's GeoJSON ROI round trip.
2. Add stable FLIMKit plugin bindings for current intensity, lifetime, and ROI data.
3. Replace the demo script with a small Fiji command and normal user interface.
4. Add bidirectional Fiji ROI Manager conversion.
5. Test registered ROI transfer with Fiji's existing registration tools.

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
