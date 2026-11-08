# Requests and responses

Each request has its own tab. Create one with **+** in the toolbar. Double-click a tab to rename it, and
right-click it for **Save to Collection**, **Save As…**, **Save All**, **Rename…**, **Duplicate**, **Copy as cURL**, **Close** and
**Close Others**.

## Method and URL

Pick the method (GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS) and type the URL.

- If you leave out the scheme, `http://` is added.
- Any query string you type in the URL is kept exactly as written.
- The URL may use [variables](environments.md), for example `{{baseUrl}}/users`.
- **Pasting a cURL command** into the URL field turns the tab into that request. See
  [Import and export](import-export.md).

## Params

The **Params** table holds query parameters. Each row has a checkbox to switch it on or off without deleting it.

- **Editing:** click a cell to edit it. `Enter` or `Tab` confirms and `Esc` cancels. Typing into the empty last row
  adds a new parameter.
- **Encoding:** enabled parameters are URL-encoded and appended to the URL, after any query string that is
  already there.
- **Tab title:** the tab shows how many parameters are active, e.g. *Params (2)*.

## Headers

The **Headers** table works the same way.

- **Defaults:** GetMan adds `Content-Type` for you, based on the body type. A `Content-Type` header you add
  yourself takes precedence, except for multipart bodies, which need GetMan's own header (it carries the
  boundary).
- **Authorization:** use the [Auth](auth.md) section, or add an `Authorization` header here yourself (yours wins).

## Body

Choose a body type in the **Body** section:

| Body type | What is sent | Default Content-Type |
|---|---|---|
| None | No body | n/a |
| JSON | The text you type; **Format JSON** pretty-prints it and reports syntax errors with line and column | `application/json` |
| Text | The text you type | `text/plain` |
| XML | The text you type | `application/xml` |
| Form URL-encoded | The text you type, e.g. `name=Ada&age=36` | `application/x-www-form-urlencoded` |
| Form Data (multipart) | Text fields and file uploads (see below) | `multipart/form-data; boundary=…` |
| Binary File | The content of one file, unchanged | `application/octet-stream` |

Text bodies are sent as UTF-8.

### Form data and file uploads

With **Form Data (multipart)** the body editor becomes a table:

- **Key:** the field name.
- **Value:** the text for a text field, or the file path for a file field.
- **Type:** *Text* or *File*. Click the Type cell to switch a row between the two.
- **Add File…:** adds a file field. The key defaults to the file name.
- **Choose File…:** picks the file for the selected row.

Files are streamed from disk when you send, so large files are fine. A missing or unreadable file is reported in
the response panel instead of being sent.

### Binary file

With **Binary File**, pick a file with **Browse…**, or type its path. The file is sent as the whole request body.
Set a `Content-Type` header if the server expects something more specific than `application/octet-stream`, for
example `image/png`.

File paths may use variables, for example `{{dataDir}}/photo.jpg`. Collections and exports store the **path**, not
the file itself.

## Sending

- **Ways to send:** **Send**, `Ctrl+Enter` anywhere in the tab (`Cmd+Enter` on macOS), or `Enter` in the URL field.
- **Cancel:** while a request is running, the button becomes **Cancel**.
- **Variables:** they are filled in from the selected environment and the request's collection just before
  sending. A red line under the URL warns about [unknown variables](environments.md#unknown-variables).

## Checks

The **Checks** tab holds the request's **expected status**: one or more status codes or classes separated by
commas, for example `201`, `200, 204` or `2xx`. Leave it empty for no check.

- After each send, the response panel shows **✓ expected 201** in green, or **✗ expected 201** in red.
- [Running the collection](collections.md#running-a-collection) reports the request as **Passed** or **Failed**.
- The tab's title shows the expected status, for example **Checks (201)**.

## The response panel

- **Status line:** the status code and reason, coloured green for 2xx, blue for 3xx, orange for 4xx and red for
  5xx and errors. Then the time taken, the size and the HTTP version (HTTP/1.1 or HTTP/2).
- **Body tab:** the response body. Tick **Pretty** to format JSON. **Copy Body** copies the whole body, even when
  only the first 2 million characters are displayed for very large responses.
- **Headers tab:** every response header. Right-click to copy a header or all of them.
- **Errors** such as "connection refused", "unknown host", timeouts or certificate problems appear in place of
  the status, with a short explanation.

Redirects are followed by default. You can change that, and the timeouts, in [Preferences](preferences.md).
