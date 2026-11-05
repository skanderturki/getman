# GetMan roadmap

Planned work after 0.3.0, in release order. Each release is shippable on its own: merged to `main`, tagged,
published to the update site, and announced in the GitHub release notes.

| Release | Theme | Why now |
|---|---|---|
| **0.4.0** | Environments and `{{variables}}` | Most-expected feature; makes collections reusable across local, staging and production |
| **0.5.0** | Folders and Postman collection import | Lets Postman users bring their work over; import needs folders and variables |
| **0.6.0** | Request history and preferences (timeouts, redirects, self-signed TLS for local dev) | Small daily-use features, plus removing the `https://localhost` blocker |
| **0.7.0** | Multipart form bodies and file upload | Upload endpoints; completes cURL `-F` import |
| **0.8.0** | Secrets in Eclipse secure storage | Tokens, passwords and secret variables out of the plain-text state file |
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
- Every sent request is recorded with its time, a resolved snapshot and the response summary (status, time,
  size).
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
