# Security Policy

This file is part of the template. Rewrite it for your own add-on, or delete it if you would rather not publish a policy.

Below is a starting point, adapted from [FLIMKit's own SECURITY.md](https://github.com/FLIMKit/FLIMKit/blob/main/SECURITY.md).

---

## What an add-on is

A FLIMKit add-on is ordinary Python, imported into the running FLIMKit process, with your account's access to your files and your network. There is no sandbox. Installing one is the same trust decision as installing a Fiji plugin or a pytest plugin.

That cuts both ways for this repository. Anyone running this add-on is trusting whoever maintains it, so a vulnerability here is worth reporting properly rather than filing in public.

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting from the Security tab, or email `<your address>` with the add-on's name in the subject line.

Please include:

- What the problem is and what an attacker gets out of it.
- The add-on version, the FLIMKit version, your OS, and your Python version.
- Steps to reproduce. If a malformed input file triggers it, a minimal file or a generator script is far more useful than a description.
- Whether you have already disclosed it anywhere else.

Say what you can realistically promise about response times. A single-maintainer academic project acknowledging within 7 days and assessing within 30 is honest; a 24-hour SLA is not.

## Scope

In scope for an add-on of this shape:

- Code execution, memory corruption, or path traversal triggered by parsing a file the add-on reads.
- Code execution or file overwrite triggered by anything the add-on writes to `~/.flimkit/`, including its own config section.
- Insecure handling of credentials or tokens, if the add-on talks to a network service.

Out of scope:

- Crashes or unhandled exceptions with no path to code execution or data loss. Those are ordinary bugs, and FLIMKit will roll back the add-on's registrations and stay running. Open an issue.
- Anything in FLIMKit itself rather than in this add-on. Report that to [FLIMKit](https://github.com/FLIMKit/FLIMKit/security/advisories/new).
- Anything requiring an attacker who already has your user account on the machine.
