# Eclipse Marketplace listing

Values for https://marketplace.eclipse.org/node/add/resource

| Field | Value |
|---|---|
| Solution Name | GetMan |
| Solution URL | https://github.com/skanderturki/getman |
| Organization Name | Jahiz Digital Solutions (Jahiz.tn) |
| Eclipse Foundation Member | No |
| Solution Logo | `logo-110x80.png` (this folder) |
| Status | Beta |
| License Type | EPL 2.0 |
| Solution Type | This product requires an existing Eclipse installation to function |
| Support URL | https://github.com/skanderturki/getman/issues |
| Categories | Testing, Web Services, Tools, Web, Network |
| Markets | Tools |
| Tags | rest, http, api, client, postman, json, curl |
| Version number | 0.5.0 |
| Update Site URL | https://skanderturki.github.io/getman-update-site/ |
| Supported Eclipse Releases | 2024-03 (4.31) through 2026-12 (4.42) |
| Supported Platforms | Windows, Mac, Linux/GTK |
| Minimum Java Version | Java 17 |
| Feature ID | `com.getman.feature` — Required |
| Screenshots | Up to 4 (to add) |

## Description

GetMan is a free, lightweight REST/HTTP client inside the Eclipse IDE. No account, no cloud sync, no extra app.

- **Request tabs**: method, URL, query params, headers and body (JSON, text, XML, form).
- **Environments and {{variables}}**: switch between Local, Staging and Production from a dropdown; {{name}} works in URL, params, headers, body and auth.
- **Auth helper**: Bearer token and Basic auth, with secrets masked and left out of exports by default.
- **Postman import**: bring in Postman collections (v2.0/2.1, with folders, auth and variables) and environments.
- **Collections and folders**: save requests with Ctrl+S, organise them in nested folders with drag and drop, and export or import them as `.getman.json`
  files to share with your team.
- **Full response details**: status, time, size, HTTP version, every response header, and the body with JSON
  pretty-printing.
- **Saved automatically**: open tabs and collections survive Eclipse restarts.
- **Copy as cURL and import cURL**: reproduce a request in a terminal, or paste a curl command (e.g. from browser dev tools) to create a request.
- **No dependencies**: uses the JDK's HTTP client and runs on Java 17+.

Open it with Window › Show View › Other… › GetMan › GetMan.
