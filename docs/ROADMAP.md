# GetMan roadmap

Status: 0.4.0 to 0.9.2 are released (the user guide was added in 0.7.0, the MCP server for AI coding assistants
in 0.9.0, run results in History and export in 0.9.1, setup guides for Copilot and Codex in 0.9.2). GetMan is available on the
[Eclipse Marketplace](https://marketplace.eclipse.org/content/getman).

Planned work after 0.3.0, in release order. Each release is shippable on its own: merged to `main`, tagged,
published to the update site, and announced in the GitHub release notes.

| Release | Theme | Why now |
|---|---|---|
| **0.4.0** | Environments and `{{variables}}` | Most-expected feature; makes collections reusable across local, staging and production |
| **0.5.0** | Folders and Postman collection import | Lets Postman users bring their work over; import needs folders and variables |
| **0.6.0** | Request history and preferences (timeouts, redirects, self-signed TLS for local dev) | Small daily-use features, plus removing the `https://localhost` blocker |
| **0.7.0** | Multipart form bodies and file upload | Upload endpoints; completes cURL `-F` import |
| **0.8.0** | Secrets in Eclipse secure storage | Tokens, passwords and secret variables out of the plain-text state file |
| **0.9.0** | MCP server for AI coding assistants (optional feature) | Claude Code, Copilot and other MCP clients create and send requests through GetMan; the requests stay in GetMan and secrets never reach the model |
| **0.9.1** | Run results kept in History, export to HTML, CSV and JUnit XML | Test suites are only useful if their results can be found, compared and shared |
| **0.9.2** | Setup guides for GitHub Copilot (Eclipse, VS Code) and Codex | Users of assistants other than Claude Code had no step-by-step setup |
| Later | Response search, save response to file, image preview, cookie jar, scripts and tests | |

Validate the order against demand: open one GitHub issue per release theme and invite 👍 votes.

---

## 0.4.0: Environments and variables

**Goal:** write `{{baseUrl}}/courses` and `Bearer {{token}}` once, and switch between Local, Staging and
Production from a dropdown.

**Core (`com.getman.core`)**
- `model.Environment`: id, name, and a list of `KeyValue` variables (enabled flag reused).
- `WorkspaceState`: `environments` list and `activeEnvironmentId`, persisted in `state.json`. Old files load with
  no environments.
- `variables.VariableResolver`:
  - Replaces `{{name}}` (spaces inside the braces allowed) using the enabled variables of the active environment.
  - Variables may reference other variables, up to a fixed depth, with cycle protection.
  - Unknown names are left as written and reported.
  - `resolve(RequestModel, Map)` returns a resolved copy, covering the URL, param and header keys and values, body
    and auth fields. The stored request keeps its `{{…}}` placeholders.
- `HttpService` and `CurlExporter` stay unaware of variables. Callers pass the resolved copy.

**UI (`com.getman.ui`)**
- An environment dropdown in the view toolbar ("No Environment", then each environment), plus a
  **Manage Environments…** action.
- **Environments dialog:**
  - An environment list on the left, with Add, Rename, Duplicate and Delete.
  - The variables table on the right (the same table widget as Params and Headers).
  - Edits apply when you click OK.
- **Send** and **Copy as cURL** use the resolved request, so what you copy is what was sent.
- **Live hint** under the URL row: "Unknown variables: x, y". It updates while you type and when you switch
  environments.

**Tests**
- Resolver: basic, spaces, nested, cycles, unknown names, disabled variables, auth and body.
- State round trip, and old files without environments.

**Out of scope (later releases)**
- Collection-level variables (0.5, with Postman import).
- Secret variables in secure storage (0.8).
- Environment export and import (0.5, together with Postman environment import).

---

## 0.5.0: Folders and Postman import

**Folders**
- A collection holds a tree: a folder can contain folders and requests.
- Model change: `RequestCollection.items` becomes a list of `Folder | RequestModel`, and requests keep their ids.
- The state file format version moves to 2. Version-1 files load as flat collections.
- Tree UI: New Folder, Rename, Delete, Move to…, and drag and drop to reorder or move.
- Collection export and import include folders.

**Collection variables**
- Each collection gets its own variables.
- Lookup order when resolving: active environment first, then the request's collection.

**Postman import** (File › Import… and Import in the collections tree)
- Postman Collection **v2.1 and v2.0** JSON: folders (`item` nesting), method, URL (raw string, with query kept as
  written), headers, `disabled` flags, and bodies: raw (with language → body type), urlencoded, and formdata text
  fields.
- Auth: bearer and basic map to Auth. Other types, inherited auth, scripts and tests are listed as warnings.
- Collection `variable` values become collection variables.
- Postman environment JSON becomes a GetMan environment.
- Same warning style as cURL import: import what we can, and list what we skipped.
- Tests use small sample files in the test fragment.

---

## 0.6.0: History and preferences

**History**
- Every sent request is recorded with its time, the request as written (with its `{{variables}}`, so it can be
  re-sent in any environment and no resolved secrets are copied), the URL actually called, and the response
  summary (status, time, size).
- The last 100 are kept, in a separate `history.json` so the main state file stays small.
- A **History** tab next to Collections in the left panel, grouped by day.
  - Double-click opens the request in a new tab.
  - Clear History.
- Response bodies aren't stored, only summaries, to keep the file small.

**Preferences page** (Window › Preferences › GetMan)
- Connect and request timeouts.
- Follow redirects.
- Accept self-signed or untrusted certificates for: never / localhost only (default) / all hosts (with a warning).
  Implemented with an `SSLContext` per setting, so `HttpService` rebuilds its client when settings change.
- History size.

---

## 0.7.0: Multipart and file upload
- New body type **Form Data (multipart)**: rows of text fields or files (file chooser), sent as
  `multipart/form-data` with a generated boundary.
- Binary body: send a file as the raw body.
- cURL import maps `-F name=value` and `-F file=@path`. cURL export emits `-F`.
- Collection exports store file paths, not file contents, and warn about it.

---

## 0.8.0: Secure storage
- Auth tokens and passwords, and variables marked **secret**, are kept in Eclipse secure storage
  (`ISecurePreferences`) instead of `state.json`.
- The state file keeps only a reference.
- Secret variables are masked in the environments table.
- Migration: on first start, existing plain-text secrets are moved into secure storage.
- Exports keep the current rule: secrets are left out unless included explicitly.

---

## 0.9.0: MCP server for AI coding assistants

**Goal:** a developer codes in Eclipse with GetMan open and uses an AI assistant (Claude Code in a terminal,
GitHub Copilot or any other MCP client) alongside it. The assistant creates and sends test requests **through
GetMan**, and every request it creates stays in GetMan, to review, edit, re-run and export afterwards.

**Why GetMan and not plain `curl`**
- **Secrets never reach the model.** The assistant writes `{{token}}`; GetMan fills it in from secure storage
  when sending and redacts secrets from what it returns.
- **The work persists.** Generated requests become ordinary GetMan requests in a collection, not lines in
  terminal scrollback.
- **Environments are reused.** The assistant picks Local, Staging, … instead of guessing URLs and logins.
- **A human stays in the loop.** GetMan runs inside Eclipse, so it can ask for approval in Eclipse before risky
  calls.

**Decisions**
- **The server runs inside the Eclipse plugin**, over HTTP on localhost. It is available while Eclipse is open,
  which matches the target scenario (Eclipse for coding, the assistant alongside). A standalone server for use
  without Eclipse is out of scope.
- **Built on the official [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)** (2.x, MIT license),
  not a hand-written protocol implementation. Reasons:
  - the MCP spec keeps evolving, and following it becomes a version bump;
  - the SDK is tested against real clients;
  - tools are declared with typed schemas.

  `mcp-core` is already an OSGi bundle (Java 17). Its dependencies (Reactor, Jackson, SLF4J) are also OSGi bundles
  under Apache 2.0 or MIT, and are listed in `about.html`.
- **Dependencies are contained:**
  - all MCP code lives in a new `com.getman.mcp` bundle;
  - `com.getman.core` and `com.getman.ui` stay dependency-free;
  - it ships as an **optional feature**, "GetMan MCP Server", so users who don't want it don't install it.
- **Transport: a small adapter** from the SDK's server transport interface to the JDK's built-in HTTP server
  (`com.sun.net.httpserver`). The SDK's own HTTP transport needs Jakarta Servlet 6.1+, and the Jetty that ships
  with Eclipse differs between 2024-03 and 2026-12, so we don't rely on it.
- **The assistant cannot change your existing requests.** It can create requests, folders and collections, and
  edit or delete **only requests it created**.
- **Approval in Eclipse.** GET requests are sent freely. Any other method on an environment marked
  **Production** shows an approval dialog in Eclipse first ("Claude Code wants to send DELETE … Allow / Deny").
- **Test suites (added during 0.9).** Each request can have an **expected status**, and **Run Collection** sends a
  collection or folder and reports which requests passed or failed. The assistant can build such a suite from the
  project's code and run it (`run_collection`). Richer assertions and a runner that works without Eclipse (for
  example in CI) are possible later releases.
- **Environments created by the assistant (added during 0.9).** The assistant can create environments (marked AI)
  with values found in the project, such as `baseUrl`, and add variables to the user's environments, but never
  change existing ones, Production environments or secret values.
- **Save All (added during 0.9):** `Ctrl+Shift+S` and a toolbar button save every changed tab.

**Step 1: spike (about half a day; nothing ships)**
- Load the SDK bundles in Eclipse through Tycho, start a minimal server through the JDK HTTP adapter, and connect
  Claude Code with `claude mcp add --transport http`. Call one tool.
- Check:
  - the bundles resolve on Eclipse 2024-03 and 2026-12 (p2 dry-run installs);
  - the size of the added jars;
  - that `com.sun.net.httpserver` is visible to the bundle.
- **If the spike fails:** fall back to a hand-written JSON-RPC implementation over the same JDK HTTP server,
  which is small for the tools below.
- **Result (done):** the plan holds. The SDK bundles load in Eclipse, Claude Code connected and called tools, the
  feature resolves on 2024-03 and 2026-09, and the SDK adds about 5.8 MB in 12 bundles (only in the optional
  feature). The SDK's ServiceLoader lookup does not work under OSGi, so GetMan creates its JSON mapper and schema
  validator directly; missing package imports only show up at run time, so the protocol test runs the real server.

**Tools exposed to the assistant**

| Tool | What it does |
|---|---|
| `list_collections` | Collections, folders and request names and ids |
| `get_request` | One request as written, with `{{variables}}` and no secret values |
| `list_environments` | Environment names, which one is active, and variable **names** only (never values of secret variables) |
| `create_environment` / `set_environment_variables` | Create environments and add variables (only change environments the assistant created; never Production or secret values) |
| `create_request` | Create a request in a collection and folder, creating them if needed (default collection: one per project or task, named by the assistant, e.g. "orders-api (Claude)") |
| `update_request` / `delete_request` | Only for requests the assistant created |
| `send_request` | Send a saved request, or an ad-hoc one, in a chosen environment. Returns status, headers, time, size and the body (capped size, secrets redacted, marked as untrusted data) |
| `import_curl` | Create a request from a cURL command |
| `run_collection` | Run a collection or folder and report which requests passed or failed their expected status |

**Security**
- **Off by default:** enabled in Preferences.
- **Localhost only:** listens on `127.0.0.1`.
- **Token required:** every call needs a random token (regenerate in Preferences).
- **Browsers rejected:** requests with an `Origin` header are refused, so a web page can't reach the server.
- **Secret redaction:**
  - values of secret variables, tokens and passwords are redacted from everything returned;
  - this includes `Authorization` headers and error messages.
- **Body cap:** response bodies are size-capped, so a large or hostile response can't flood the assistant.

**UI (`com.getman.ui`, with hooks the optional bundle plugs into)**
- **Preferences › GetMan › MCP Server:**
  - enable;
  - port;
  - token (show, copy, regenerate);
  - **Copy Claude Code setup command**;
  - short setup notes for other MCP clients.
- **Environments dialog:** a **Production** checkbox per environment (drives the approval dialog).
- **Live updates:** requests the assistant creates appear in the Collections tree immediately. Changes are applied
  on the UI thread and saved like the user's own edits.
- **History marking:** requests sent through MCP are marked as sent by an AI assistant in History.

**Docs**
- A new user-guide page, "Using GetMan with Claude Code and other AI assistants": setup, what the assistant can
  and cannot do, security model, approval dialog.
- README and Marketplace listing: mention the optional MCP feature.

**Tests**
- Core and tool logic (no Eclipse needed):
  - tool input validation;
  - the "only its own requests" rule;
  - secret redaction in results;
  - the Production approval decision.
- A protocol test with the SDK's client against the server: initialize, list tools, create and send a request
  against a local test HTTP server.
- Manual check with Claude Code before release.

**Out of scope (possible later releases)**
- Richer assertions (body, headers, time), variable extraction and chaining, and a command-line runner for CI.
- Letting the assistant read past runs (for example "what broke since the last run?").

## 0.9.1: Run results

**Goal:** when you run a test suite, its results are kept and can be shared.

- **Runs in History:** each finished run of a collection or folder (by you or by an AI assistant) is kept in the
  History tab as **one group**: the run's title, summary, environment and time, with one child per request
  (result, status, details). Double-click opens the results window again, with **Run Again**; double-click a
  result opens the request.
- **Export:** **Export…** in the results window and in the run's right-click menu writes an **HTML report**, a
  **CSV** file or a **JUnit XML** file.
- **What is kept:** no response bodies; values of secret variables are masked. The last 20 runs per collection,
  at most 100 in all, in `runs.json` next to `history.json`. Turning history off also stops keeping runs.
- OpenAPI import (`import_openapi`), to create a request per endpoint.
- A standalone MCP server that works without Eclipse running.

## 0.9.2: Setup guides for other assistants

**Goal:** connecting an assistant other than Claude Code is as easy to follow as the Claude Code button.

- User guide, *AI assistants (MCP)*: step-by-step setup for **GitHub Copilot in Eclipse** (Copilot's MCP preferences,
  `requestInit.headers`), **GitHub Copilot in VS Code** (`mcp.json`) and **Codex** (CLI and IDE extension,
  `~/.codex/config.toml`, with the token in the file or in an environment variable), plus how to remove GetMan
  from Claude Code.
- No change to GetMan's behaviour.
