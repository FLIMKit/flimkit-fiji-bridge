# flimkit-plugin-template

A starting point for a [FLIMKit](https://github.com/FLIMKit/FLIMKit) add-on. Click "Use this template" and you have a working, installable, tested add-on that registers one menu entry. Replace that entry with what you actually want to build.

If you would rather read a worked example of every hook before starting, that is [flimkit-demo-plugin](https://github.com/FLIMKit/flimkit-demo-plugin). This repository is deliberately the smaller thing.

Requires FLIMKit 0.10.0 or newer, the release that added the add-on system.

## Rename these five things

Nothing here breaks if you skip the renaming, but you will end up with an add-on called `plugin_template`.

1. The package directory `flimkit_plugin_template/`.
2. `name`, `authors`, and the `[project.entry-points.'flimkit.plugins']` line in `pyproject.toml`. The entry point name is what FLIMKit shows in `Help > Plugins...` and what disables the add-on in the config.
3. `PLUGIN_NAME` and the `id=` of the tool in `flimkit_plugin_template/__init__.py`. Every `id` has to be unique across every add-on loaded, so prefix them with something specific to you.
4. The import and the expected names in `tests/test_plugin.py`.
5. The copyright line in `LICENSE.md`.

## What is in the box

| File | What it does |
|---|---|
| `flimkit_plugin_template/__init__.py` | Declares `FLIMKIT_PLUGIN_API = 1` and registers one `@tool`, which opens a window and counts its own opens into `~/.flimkit/config.json` |
| `pyproject.toml` | Packaging and the `flimkit.plugins` entry point, which is how an installed add-on is found |
| `tests/test_plugin.py` | Asserts the API version matches, the tool registered, and the entry point is declared |
| `.github/workflows/test.yml` | Installs FLIMKit and the add-on and runs those tests on every push |

The workflow installs FLIMKit with `--no-deps`, because `flimkit.plugins` is pure standard library and pulling the full scientific stack in to check that an entry point resolves would take minutes rather than seconds. Add the dependencies your own add-on needs to `pyproject.toml` and they will be installed normally.

## Working on it

```bash
pip install -e '.[test]'
pytest -q
```

Then start FLIMKit and look under `Tools`. `Help > Plugins...` lists what loaded, what failed, and turns individual add-ons off.

An add-on installed with `pip` loads without any further permission, since installing it was already a deliberate act. A loose `.py` file dropped in `~/.flimkit/plugins/` does not, until you turn that folder on in `File > Preferences > Plugins`.

## The other hooks

`@tool` is one of four. The full set, all importable from `flimkit.plugins`:

| Hook | Registers |
|---|---|
| `@tool(id, label, menu, order)` | A menu entry, called with the live GUI object whose `app.root` is the Tk parent |
| `@file_format(id, label, exts, modality)` | A reader class for a file extension, usable everywhere a path is accepted |
| `@format_sniffer(tier, order)` | Content detection for files whose extension is ambiguous or absent |
| `@phasor_filter(id, label)` | A filter selectable wherever `gaussian` and `median` are |

`plugin_config(name)` gives you a private section of the FLIMKit config to persist settings in.

Keep tkinter imports inside the function body so the module still imports on a headless machine, and keep module-level code free of side effects. If your add-on raises while loading, FLIMKit rolls back everything it registered and carries on running.

The v1 hooks are frozen: new hooks get added, these keep their arguments and their meaning. The [Plugins section of the FLIMKit documentation](https://github.com/FLIMKit/FLIMKit/wiki/Plugins) is the reference.

## Licence

MIT, same as FLIMKit. Change it if you want something else, it is your add-on.
