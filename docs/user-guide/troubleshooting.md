# Troubleshooting and FAQ

## Installing takes minutes, or Eclipse does not start after installing

**Cause:** the install wizard's option *"Contact all update sites during install to find required software"* is on
by default. With it on, Eclipse may download and upgrade unrelated parts of itself while installing GetMan. That
download is slow, and a partial upgrade can stop Eclipse from starting.

**Fix:** GetMan needs nothing beyond what Eclipse already has, so **untick that option** in the install wizard.
If Eclipse no longer starts, re-extract a fresh copy of Eclipse and install GetMan again with the option off.

## The GetMan view is not in Show View

- **Check the install:** GetMan should be listed under **Help › About Eclipse IDE › Installation Details**.
- **Restart:** if it is listed, restart Eclipse once.
- **Look in the right place:** the view is under **Window › Show View › Other… › GetMan › GetMan**.

## "Invalid URL" or "Unknown variable"

The request uses a `{{variable}}` that is not defined.

- **Check the environment:** look at the dropdown in the GetMan toolbar.
- **Check the definitions:** look in **Manage Environments…** or the collection's **Variables…**.
- **Read the hint:** the red line under the URL lists the missing names. See
  [Environments and variables](environments.md#unknown-variables).

## "TLS certificate not trusted"

The server's certificate is not trusted by Java, or does not match the host name.

- **Local development server** (localhost): keep the default certificate setting, or pick *Also accept
  self-signed certificates on localhost* in [Preferences](preferences.md).
- **Internal test server** you control: either import its certificate into Java's trust store, or temporarily pick
  *Accept any certificate*.

## "Could not connect" or "Connection timed out"

- **Is the server reachable?** Check that it is running, and that the host and port are right.
- **Proxy:** if you are behind a proxy, note that GetMan does not use Eclipse's proxy settings yet.
- **Slow servers:** increase the timeouts in [Preferences](preferences.md).

## A file upload fails with "was not found"

The file path in the form-data table or the binary body doesn't exist on this computer. This is common after
importing a collection from someone else. Pick the file again with **Choose File…** or **Browse…**.

## Where is my data stored?

Everything is stored locally, per workspace, in the GetMan plugin's state folder:

```
<workspace>/.metadata/.plugins/com.getman.ui/
├── state.json      open tabs, collections, environments
└── history.json    request history
```

The files are plain JSON and are saved shortly after every change. If `state.json` is ever damaged, GetMan starts
empty and keeps the damaged file as `state.json.corrupt-<date>`, so nothing is silently lost.

Tokens and passwords are stored in `state.json` in plain text. Don't share that file. To share a collection, use
**Export…**, which leaves secrets out by default.

## How do I move my requests to another workspace or computer?

- **Collections:** export each collection (right-click › **Export…**) and import it in the other workspace.
- **Everything at once:** copy `state.json` to the same folder in the other workspace while Eclipse is closed.

## Reporting a problem or asking for a feature

Open an issue at [github.com/skanderturki/getman/issues](https://github.com/skanderturki/getman/issues). Include
your Eclipse version, operating system and GetMan version (**Help › About Eclipse IDE › Installation Details**).
