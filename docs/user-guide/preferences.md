# Preferences

Open **Window › Preferences › GetMan**. On macOS it is **Eclipse › Settings… › GetMan**. Changes apply to the next
request you send; no restart is needed.

| Setting | Default | Meaning |
|---|---|---|
| Connect timeout (seconds) | 10 | How long to wait for the connection to be established |
| Request timeout (seconds) | 60 | How long to wait for the whole response |
| Follow redirects (3xx) | On | Follow `Location` headers automatically |
| HTTPS certificates | Self-signed on localhost | See below |
| History: keep the last N requests | 100 | 0 turns history off |

## HTTPS certificates

| Choice | Behaviour |
|---|---|
| **Only trusted certificates** | Like a browser: the certificate must be trusted by Java and match the host name |
| **Also accept self-signed certificates on localhost** (default) | Same as above, except that any certificate is accepted for `localhost`, `*.localhost`, `127.x.x.x` and `::1` |
| **Accept any certificate (insecure)** | No certificate checks at all. Use only for testing internal servers you control. |

The default lets local development servers on `https://localhost` work without extra setup, while every other host
is still fully checked.

If a request fails with *"TLS certificate not trusted"*, the message points you to this setting.

The MCP server for AI assistants has its own page, **GetMan › MCP Server**, when the optional
*GetMan MCP Server* is installed. See [AI assistants (MCP)](ai-assistants.md).

Preferences are stored per workspace.
