# Environments and variables

Variables let one request work against several servers. Write `{{baseUrl}}/courses` and `Bearer {{token}}` once,
then switch between Local, Staging and Production from a dropdown.

## Using variables

Write `{{name}}` in any of these places:

- the URL;
- param and header names and values;
- the body, including form-data field names, values and file paths, and the binary file path;
- auth fields: the Bearer token, and the Basic username and password.

Spaces inside the braces are allowed (`{{ name }}`). Names may contain letters, digits, `_`, `-`, `.` and `$`.

Values can use other variables, for example `baseUrl = https://{{host}}:{{port}}`. A variable that refers back to
itself stops expanding instead of looping.

Text that only looks like a variable but isn't one, such as `{{#each items}}` in a template body, is left alone.

## Environments

An environment is a named set of variables.

- **Create and edit:** pick **Manage Environments…** in the environment dropdown at the left of the GetMan toolbar
  (also in the view menu).
  - **Add…**, **Duplicate**, **Rename…** and **Delete** environments.
  - Edit each environment's variables in a table: name, value, and a checkbox to switch a variable off.
  - **Production:** check it for environments that point at real systems. An AI assistant then needs your
    approval to send anything but GET, HEAD or OPTIONS there (see [AI assistants](ai-assistants.md)).
  - **Cancel** discards your changes.
- **Select:** choose an environment in the dropdown. *No Environment* means only collection variables apply.

## Secret variables

Click a variable's **Secret** cell (in Manage Environments or a collection's Variables…) to mark it secret.

- **Masked:** its value shows as `••••••••` and is typed into a password field.
- **Stored safely:** its value is kept in Eclipse secure storage instead of GetMan's state file, like auth tokens
  and passwords (see [Authorization](auth.md#where-credentials-are-stored)).
- **Left out of exports:** secret collection variables are left out of collection exports unless you choose
  **Include Secrets**.

A typical setup: a `token` variable marked secret in each environment, and `Bearer {{token}}` in the Auth section
of your requests.

## Collection variables

Each collection can have its own variables: right-click it › **Variables…**. They apply to that collection's
requests.

When both define a name, **the selected environment wins** over the collection. A typical setup is a default
`baseUrl` on the collection, overridden per environment.

## What is sent

- **Send** and **Copy as cURL** use the filled-in values, so what you copy is what was sent.
- **Saved requests keep their placeholders.** The same collection works in every environment.
- **History** entries also keep their placeholders, plus the URL that was actually called.

## Unknown variables

When a request uses a name that neither the selected environment nor its collection defines, a red line under
the URL says so. For example: *Unknown variable `{{token}}`: not defined in environment "Local" or collection
"API"*. It updates as you type and when you switch environments.

Unknown variables are sent as written (`{{token}}`). In a URL this usually makes the request fail with
"Invalid URL", which is the cue to define the variable.

## Importing environments

A Postman environment file can be imported with **Import (GetMan or Postman)…** in the Collections tree. See
[Import and export](import-export.md).
