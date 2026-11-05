# GetMan

A free, lightweight REST/HTTP client built into the Eclipse IDE — no account, no cloud sync, no extra app.

Open it with **Window › Show View › Other… › GetMan › GetMan**.

## Features (0.1)

- **Request tabs** – method, URL, query params, headers and body (JSON, text, XML, form). Double-click a tab to
  rename it; right-click for Duplicate, Copy as cURL, Close, Close Others.
- **Collections** – a tree on the left of the view. `Ctrl+S` in a request tab saves it to a collection (you pick
  the collection and name the first time; afterwards `Ctrl+S` just updates it). Tabs with unsaved changes show a
  `*`. Double-click a saved request to open it; right-click for Rename (F2), Duplicate, Move Up/Down, Delete
  (Del), Export… and Import… (shareable `.getman.json` files).
- **Saved automatically** – collections and open tabs are stored as JSON in the plugin's state folder
  (`<workspace>/.metadata/.plugins/com.getman.ui/state.json`) and restored when Eclipse restarts.
- **Full response details** – status (colour-coded), time, size, HTTP version, all response headers, and the
  body, with JSON pretty-printing. Copy the body or headers to the clipboard.
- **Keyboard first** – `Ctrl+Enter` (`Cmd+Enter` on macOS) sends from anywhere in the tab; `Enter` in the URL
  field sends too. Click **Cancel** to abort a slow request.
- **Copy as cURL** – share or reproduce a request in a terminal.
- **No dependencies** – uses the JDK's `java.net.http` client; runs on Java 17+.

## Roadmap

1. Folders inside collections, drag and drop
2. Auth helper (Bearer, Basic)
3. Paste a cURL command to create a request
4. Request history
5. Environments with `{{variables}}`
6. Preferences page (timeouts, redirects, trust self-signed certificates for local development)
7. Postman v2.1 collection import

## Project layout

| Path | What it is |
|---|---|
| `bundles/com.getman.core` | UI-free logic: model, HTTP client, JSON, persistence, cURL export |
| `bundles/com.getman.ui` | The Eclipse view (SWT/JFace) |
| `tests/com.getman.core.tests` | JUnit 5 tests for core (test fragment) |
| `features/com.getman.feature` | Installable feature grouping the two bundles |
| `releng/com.getman.site` | Builds the p2 update site |

## Building

Requires JDK 17+ and Maven 3.9+.

```sh
mvn clean verify
```

This compiles everything, runs the tests, and produces the update site in
`releng/com.getman.site/target/repository/` (also zipped as `com.getman.site-*.zip`).

To publish a release, copy the contents of `target/repository/` into the update-site repository
([getman-update-site](https://github.com/skanderturki/getman-update-site)) and push it.

## Developing in Eclipse

1. Use **Eclipse IDE for RCP and RAP Developers** (it includes PDE).
2. **File › Import › General › Existing Projects into Workspace**, select this folder, tick *Search for nested
   projects*, and import all five projects.
3. Run it: right-click `com.getman.ui` › **Run As › Eclipse Application**. A second Eclipse starts with the
   plugin installed; open the GetMan view there.

Alternatively, import as **Existing Maven Projects** if you have m2e with the Tycho connector.

## License

[Eclipse Public License 2.0](LICENSE)
