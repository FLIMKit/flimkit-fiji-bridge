# Contributing

This file is part of the template. Rewrite it for your own add-on, or delete it if you are not taking contributions.

Below is a starting point, adapted from [FLIMKit's own CONTRIBUTING.md](https://github.com/FLIMKit/FLIMKit/blob/main/CONTRIBUTING.md).

---

Thanks for your interest in this add-on. Bug reports and pull requests are welcome.

This add-on is maintained by the FLIMKit contributors. It is MIT licensed, so contributions are accepted under the same terms.

## Reporting bugs

Open an issue and include:

- What you ran, as a GUI action or a minimal Python snippet.
- The full traceback, not just the last line.
- Your OS, Python version, FLIMKit version, and the version of this add-on.
- What `Help > Plugins...` says about the add-on: whether it loaded, and what the failure was if it did not.

FLIM files are often large and sometimes unpublished, so do not attach data you cannot share. A header dump or the file's shape and metadata is usually enough to diagnose a reader problem.

## Development setup

```bash
pip install -e '.[test]'
pytest -q
```

The tests check that the add-on registers and that its entry point resolves. Add a test for any bug you fix or hook you add.

FLIMKit itself is needed to import the add-on. Install it from source, or with `pip install --no-deps 'flimkit @ git+https://github.com/FLIMKit/FLIMKit'` if you only need the plugin machinery rather than the full analysis stack.

## Coding style

Match FLIMKit, since anyone reading both should not have to switch:

- 4 spaces, single quotes, no alignment padding around `=`.
- Comments kept minimal. Prefer clear names over explanatory comments.
- Keep the module importable and side-effect free. GUI and heavy optional imports go inside the function that needs them, so the add-on still imports on a headless machine.

## Pull requests

- Branch from `main` with a descriptive branch name.
- Keep the pull request focused on one change.
- Make sure the tests pass.
- Describe what you changed and how you verified it. If you could not verify part of it, say so explicitly rather than implying it was tested.
