# AI assistants (MCP)

Code in Eclipse with GetMan open, and let your AI coding assistant test your API **through GetMan**. Claude Code,
GitHub Copilot and other assistants that support the Model Context Protocol (MCP) can create requests, send
them and read the responses. Every request the assistant creates is saved in a GetMan collection, so you can
review, edit, re-run and export it afterwards.

Why let the assistant go through GetMan instead of running `curl` itself:

- **Secrets stay hidden.** The assistant writes `{{token}}`; GetMan fills it in when sending and hides secret
  values from everything it returns.
- **The work is kept.** Generated requests become ordinary GetMan requests, not lines in a terminal.
- **Your environments are reused.** The assistant picks Local or Staging instead of guessing URLs and logins.
- **You stay in control.** The assistant cannot change your own requests, and risky calls to production wait for
  your approval in Eclipse.

## Install

The MCP server is an optional part of GetMan. In **Help › Install New Software…**, pick the GetMan update site
and check **GetMan MCP Server (optional)**. If GetMan is already installed, check only that item.

## Turn it on

1. Open **Window › Preferences › GetMan › MCP Server**.
2. Check **Enable the MCP server** and click **Apply and Close**.

The status line on the page shows **Running at http://127.0.0.1:47123/mcp**. The server starts with Eclipse from
then on, and stops when Eclipse closes.

If the status says the port is in use (for example by a second Eclipse with GetMan), choose another port.

## Connect Claude Code

1. In **Preferences › GetMan › MCP Server**, click **Copy Claude Code Setup Command**.
2. Paste the command in a terminal and run it. It looks like this:

   ```
   claude mcp add --transport http --scope user getman http://127.0.0.1:47123/mcp --header "Authorization: Bearer <token>"
   ```

3. Start Claude Code (or run `/mcp` in a running session) and check that **getman** is connected.

`--scope user` makes GetMan available in all your projects. Then just ask, for example: *"Use GetMan to create
requests for the endpoints in OrderController and test them against Local."*

## Connect other assistants

Add an MCP server of type **HTTP** (also called *streamable HTTP*) with:

- **URL:** `http://127.0.0.1:47123/mcp` (**Copy URL** on the preference page)
- **Header:** `Authorization: Bearer <token>` (click **Show** on the preference page to see the token)

For example, in VS Code with GitHub Copilot, add this to `.vscode/mcp.json` or your user `mcp.json`:

```json
{
  "servers": {
    "getman": {
      "type": "http",
      "url": "http://127.0.0.1:47123/mcp",
      "headers": { "Authorization": "Bearer <token>" }
    }
  }
}
```

## What the assistant can do

| Tool | What it does |
|---|---|
| `list_collections` | Lists collections, folders and requests (names, methods, URLs) |
| `get_request` | Reads one request as written, with its `{{variables}}` |
| `list_environments` | Lists environments, which one is selected, which are Production, and their variables. Values of secret variables are never shown. |
| `create_environment` | Creates an environment, such as *Local* with `baseUrl`, from what the assistant finds in your project |
| `set_environment_variables` | Adds variables to an environment, or changes them in environments the assistant created |
| `create_request` | Saves a new request in a collection and folder, creating them if needed, optionally with an expected status |
| `update_request`, `delete_request` | Change or delete a request **the assistant created** |
| `send_request` | Sends a saved request, or an ad-hoc one, and returns the status, headers, time, size and body |
| `import_curl` | Creates a request from a cURL command |
| `run_collection` | Runs a collection or folder and reports which requests passed or failed their expected status |

- **Where requests go:** the assistant names the collection, usually after the project and itself, for example
  *orders-api (Claude)*. New requests appear in the Collections tree right away, marked **AI**.
- **Variables:** requests are sent with the environment selected in the GetMan toolbar, unless the assistant
  names another one. Unknown variables are reported to the assistant instead of being sent.
- **Environments:** if no environment has what it needs, the assistant can read your project (server port,
  API prefix) and create one, for example *Local* with `baseUrl`. It is marked **(AI)** in Manage Environments,
  and selected if none was. For tokens and passwords it adds an empty secret variable and asks you to fill it in.
- **History:** requests the assistant sends appear in the History tab, marked **AI assistant**. Collection runs
  are not added to History.
- **The GetMan view opens by itself** if it was closed when the assistant needs it.

## Build a test suite

Ask, for example: *"Use GetMan to find every API endpoint in this project and build a test suite: one request per
endpoint with its expected status, then run it and tell me what fails."*

The assistant reads your code, creates an environment if needed, saves one request per endpoint with an
**expected status**, and runs the collection. The suite stays in GetMan: you can re-run it yourself at any time with
right-click › [Run Collection…](collections.md#running-a-collection), without the assistant.

## What the assistant cannot do

- **Change your requests.** It can read and send any request, but it can change or delete only requests it
  created. To keep an assistant's request safe from further changes, use **Save As** on it: the copy is yours.
- **Change your environments.** In environments you created, it can only add variables that do not exist yet. It
  cannot change environments marked Production, and it can never set or change the value of a secret variable.
- **See secret values.** Values of secret variables, auth tokens and passwords, and credential headers
  (`Authorization`, `Cookie`, API keys) are replaced with `[secret]` in everything returned, including response
  bodies that echo them. Values shorter than 4 characters are not searched for.
- **Upload local files.** Requests with file uploads (multipart files, binary bodies) cannot be created through
  the MCP server.
- **Send risky requests to production without asking.** See below.

## Approving requests to production

In **Manage Environments**, check **Production** for environments that point at real systems.

When the assistant sends anything other than GET, HEAD or OPTIONS in a Production environment, GetMan shows a
dialog in Eclipse with the method, the URL and the start of the body. When it runs a collection there that contains
such requests, the dialog lists them, and the whole run waits for your answer. The request is sent only if you click
**Allow**. **Deny** tells the assistant the request was refused.

The assistant waits while the dialog is open, so switch to Eclipse when it says it is waiting for approval.

## Security

- **Off by default.** Nothing listens until you enable it.
- **This computer only.** The server listens on `127.0.0.1`; other computers cannot reach it.
- **Token required.** Every call needs the token. **Regenerate** on the preference page makes the old one
  stop working; set up your assistants again afterwards. The token is stored in the workspace preferences.
- **Browsers refused.** Calls from web pages are rejected, so a website you visit cannot use the server.
- **Responses are data.** The assistant is told that response bodies come from external servers and are not
  instructions.

## Troubleshooting

- **The assistant says it cannot connect:** check that Eclipse is running and the preference page says
  *Running*. Check the port and token in the assistant's setup; after **Regenerate**, set it up again.
- **"Could not start on port 47123":** another program uses the port. Choose another port, then update the URL in
  your assistant.
- **The assistant waits for a long time:** look for the approval dialog in Eclipse.
