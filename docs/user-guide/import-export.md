# Import and export

## Copy as cURL

Right-click a request tab › **Copy as cURL**, or use the toolbar button. GetMan copies a `curl` command for bash,
zsh, Git Bash or WSL.

- **Filled-in values:** variables are filled in from the selected environment.
- **Auth:** Bearer becomes `-H 'Authorization: Bearer …'` and Basic becomes `-u 'user:pass'`.
- **Form data:** text fields become `--form-string 'name=value'` and files become `-F 'name=@path'`.
- **Binary bodies** become `--data-binary '@path'`.

## Import a cURL command

You have two options:

- **Paste a `curl …` command into a request's URL field.** That tab becomes the imported request. If it is a
  saved request, it stays linked to its collection and shows `*`. Typing never triggers this, only pasting.
- **Import cURL Command…** in the toolbar opens a dialog, prefilled from the clipboard, and creates a new tab.

**Formats it understands:**

- browsers' *Copy as cURL* (bash and Windows cmd);
- API documentation;
- PowerShell (`curl.exe`, backtick line continuations);
- multi-line commands, a leading `$ ` prompt, and a trailing `| jq`.

**Options it maps:**

| cURL | GetMan |
|---|---|
| `-X`, `--request` | Method |
| `-H`, `--header` | Headers (`Authorization: Bearer/Basic …` becomes Auth) |
| `-d`, `--data`, `--data-raw`, `--data-binary`, `--data-urlencode` | Body (Content-Type decides JSON, XML, text or form) |
| `--json` | JSON body with JSON Accept/Content-Type |
| `-F`, `--form`, `--form-string` | Form Data (multipart) fields and files |
| `--data-binary @file`, `-d @file` | Binary File body |
| `-T`, `--upload-file` | Binary File body, sent with PUT |
| `-G`, `--get` | Data moved to the query string |
| `-I`, `--head` | HEAD |
| `-u`, `--user`, `--oauth2-bearer` | Basic or Bearer auth |
| `-b`, `-A`, `-e`, `-r` | Cookie, User-Agent, Referer, Range headers |

Transport options such as `-s`, `-L`, `-k`, timeouts, proxies and output files are ignored. Anything that can't be
represented is listed in a message after the import.

Query strings are kept in the URL exactly as pasted.

## Import from Postman

In the Collections tree, right-click › **Import (GetMan or Postman)…** and pick a file. GetMan detects the file
type itself.

**Postman collections (v2.0 and v2.1)** bring in:

- folders (nested), request names and methods;
- URLs (`:id` path variables are filled in from the file);
- headers, including disabled ones;
- bodies: raw JSON/XML/text, URL-encoded forms, form-data (text and file fields), file bodies and GraphQL;
- Bearer, Basic and API-key auth, including auth inherited from folders and the collection;
- collection variables.

**Postman environments** become GetMan environments and are selected right away. Postman does not export secret
values; GetMan tells you which variables need a value.

**Listed after the import, not converted:**

- pre-request and test scripts;
- OAuth and other unsupported auth types;
- dynamic variables such as `{{$guid}}`;
- file fields without a path.

**Postman v1 files:** you are asked to export them again as v2.1.

## GetMan collection files

- **Export:** right-click a collection › **Export…** writes a `.getman.json` file.
- **Import:** **Import (GetMan or Postman)…** reads it back.
- **More detail:** see [Collections and folders](collections.md#sharing-a-collection) for how secrets and files are
  handled.
