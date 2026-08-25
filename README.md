# FLIMKit Fiji bridge

[![DOI](https://zenodo.org/badge/1334686126.svg)](https://doi.org/10.5281/zenodo.21951612)

Direct image and ROI exchange between [FLIMKit](https://github.com/FLIMKit/FLIMKit) and [Fiji](https://fiji.sc/).

Available from the **FLIMKit-Bridge** Fiji update site.

## Installing

Two things, and the plugin does nothing without both.

The server, which the QuPath extension shares:

```bash
pip install flimkit-bridge
```

The plugin, from the update site:

1. `Help > Update...`, then `Manage update sites`.
2. Tick **FLIMKit-Bridge**. If it is not in the list yet, `Add unlisted site` with
   name `FLIMKit-Bridge` and URL `https://sites.imagej.net/FLIMKit-Bridge/`.
3. `Apply changes`, then restart Fiji.

The updater keeps it current, so this is the route to prefer. Failing that, drop
`flimkit-fiji-bridge-<version>.jar` from the
[latest release](https://github.com/FLIMKit/flimkit-fiji-bridge/releases) into your Fiji
`plugins/jars/` directory by hand. That is where the update site installs it, so the two
routes do not leave you with two copies.

Fiji 2.16 or newer, with its bundled JDK 21.

## What it does

Nine commands appear under `Plugins > FLIMKit`:

| Command | Purpose |
|---|---|
| Connect | Pair with a running FLIMKit or headless `flimkit-bridge` |
| Open FLIM file... | Open `.ptu`, `.sdt`, `.photons` and the other formats FLIMKit reads |
| Fetch FLIMKit images | Pull the current intensity and lifetime images, with their units |
| Fetch ROIs from FLIMKit | Load the FLIMKit Regions table into Fiji's ROI Manager |
| Send ROIs to FLIMKit | Push the ROI Manager contents back as GeoJSON |
| Fit per-pixel lifetimes... | Run a per-pixel fit and return the maps |
| Fit ROI decays... | Fit the decay summed over each ROI |
| Phasor plot... | Open an interactive phasor window |
| Stitch and fit a mosaic... | Stitch a multi-position acquisition and fit it |

`File > Open` also handles the FLIM formats directly.

Communication stays on `127.0.0.1`. Image and ROI endpoints require the generated bearer
token. The status endpoint is unauthenticated and reports only the protocol name and
version. The server refuses non-loopback binding. Image reads use a 10-second timeout.
ROI imports wait for FLIMKit to finish, because the UI-thread mutation cannot be
cancelled safely; this prevents a timeout from reporting failure while an import may
still complete.

Pairing is through `~/.flimkit/bridge.json`, written when FLIMKit starts or when
`flimkit-bridge` is run headless. In FLIMKit, `Tools > FLIMKit Bridge...` starts the
server and shows its address and pairing token. There is one such button rather than
one per client.

## Where the server lives

This add-on no longer carries a server, and it is not a Python package. It uses [flimkit-bridge](https://github.com/FLIMKit/flimkit-bridge), the same one the QuPath extension talks to, which answers the three endpoints the Fiji client uses and nineteen more besides.

```bash
pip install flimkit-bridge
```

That is the only thing to install on the Python side. What this repository holds is the Fiji client: the plugin jar under `plugin/`, and the
groovy transport scripts the tests drive.

Pairing is through `~/.flimkit/bridge.json`, written when FLIMKit starts or when `flimkit-bridge` is run headless. There is one `Tools > FLIMKit Bridge...` button in FLIMKit now rather than one per client.

## Requirements

- Fiji 2.16 or newer, with its bundled JDK 21.
- Python 3.12 or newer, matching FLIMKit's requirement.
- A FLIMKit build containing the public image and ROI bindings merged in [FLIMKit PR #52](https://github.com/FLIMKit/FLIMKit/pull/52).
- `pytest`, NumPy, and tifffile for the bridge tests.

The jar is compiled to Java 21 bytecode and carries no native libraries, so one build runs
on Linux, macOS and Windows alike. Java 21 is a hard floor: an older Fiji fails to load
the plugin with `UnsupportedClassVersionError`, and the message does not say to upgrade
Fiji. `BridgeClient` uses `java.net.http.HttpClient`, which is Java 11 and newer.

Paths and text are platform-neutral throughout: the discovery file is resolved with
`Paths.get(System.getProperty("user.home"), ".flimkit")`, and every read and write names
`StandardCharsets.UTF_8` rather than inheriting the platform default.

The plugin jar has been verified on macOS ARM64. Linux and Windows are untested, so
report anything that looks platform-specific.

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
python -m pip install 'flimkit-bridge @ git+https://github.com/FLIMKit/flimkit-bridge'
python -m pip install pytest numpy
```

Run all tests against the current ARM64 Fiji launcher:

```bash
FIJI_PATH='/Applications/Fiji.app/Contents/MacOS/fiji-macos-arm64' \
python -m pytest -q
```

The live Fiji test is skipped unless `FIJI_PATH` is set.

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

If the plugin does not appear under `Plugins > FLIMKit`, or the launcher reports:

```text
UnsupportedClassVersionError
```

the Fiji installation is running a JDK older than 21. Install a current Fiji release with
its bundled JDK and use its current platform launcher.

```text
Module javafx.base not found
```

is the same cause. Both can happen while Fiji itself starts, before plugin code can
display an error.

## What the plugin talks to

`flimkit-bridge` answers these; the plugin uses all of them:

| Path | Purpose |
|---|---|
| `/v1/status` | Report protocol name and version, unauthenticated |
| `/v1/datasets`, `/v1/datasets/...` | List and open datasets |
| `/v1/images/...` | Fetch intensity and lifetime images as `float32` TIFF |
| `/v1/rois` | Export and import the Regions table as GeoJSON |
| `/v1/fit/defaults` | Fetch fit settings to prefill the dialogs |
| `/v1/jobs/...` | Poll long-running fits |
| `/v1/phasor/settings` | Phasor window configuration |
| `/v1/pipeline`, `/v1/pipeline/defaults` | Stitch-and-fit a mosaic |

Image IDs use an explicit allowlist. URL values are never passed to `getattr`. Each TIFF
response includes `X-FLIMKit-Value-Unit`; Fiji stores that value in the image
calibration.

The image scope is fitted lifetime and photon-count intensity only. Raw per-pixel decay
histograms are not transferred. A raw-decay binding would need a separate data and
metadata contract.

The plugin source is:

```text
plugin/src/main/java/io/github/flimkit/fiji/
```

The groovy scripts under `fiji/` and `plugin/` are transport-level checks the Python
tests drive, not the user-facing plugin.

The server is not in this repository. It is [flimkit-bridge](https://github.com/FLIMKit/flimkit-bridge).

## Run without Fiji

To run the Python and packaging checks while skipping the live Fiji process:

```bash
python -m pytest -q
```

The live test skips unless `FIJI_PATH` is set.

## Current limits

The bridge does not yet:

- perform image registration;
- define the final production protocol.

Registration will remain a Fiji-side operation. The Fiji interface rejects mismatched
image dimensions rather than silently rescaling ROI coordinates.

## Development

The plugin:

```bash
cd plugin
mvn -B verify          # 26 tests
```

The transport tests:

```bash
python -m pip install pytest numpy
python -m pytest -q    # add FIJI_PATH to include the live Fiji test
```

A tag matching `v*` builds the jar and attaches it to a GitHub release.

Please add a test for each behavior change. Keep module imports side-effect free so FLIMKit can inspect the add-on on headless systems.

## Acknowledgement

Development was assisted by OpenAI's GPT-5.6 Sol through Hermes Agent by Nous Research, under Zhen Yuan Yeo's direction and review. The human contributors remain responsible for the implementation and scientific interpretation.

## License

MIT. See `LICENSE.md`.
