# Authorization

Each request has an **Auth** section between Params and Headers. Its title shows the current choice, for example
*Auth (Bearer)*.

## Types

| Type | What you enter | What is sent |
|---|---|---|
| No Auth | n/a | Nothing |
| Bearer Token | A token | `Authorization: Bearer <token>`, with stray spaces trimmed |
| Basic Auth | Username and password | `Authorization: Basic <base64 of username:password>`, encoded as UTF-8 (RFC 7617) |

- **Masked secrets:** tokens and passwords are hidden as you type. Tick **Show** to reveal them.
- **Switching types keeps your input:** going from Basic to Bearer and back doesn't erase what you typed. Only the
  selected type is sent.
- **Variables:** auth fields may use them. A common setup is `{{token}}` defined in each
  [environment](environments.md).

## Which Authorization header wins

If you add a header named `Authorization` yourself in the **Headers** tab (and it is enabled), it is sent instead
of the Auth section. This also applies to [Copy as cURL](import-export.md).

## Where credentials are stored

Tokens and passwords are kept in **Eclipse secure storage**, which is encrypted. On Windows it uses the
operating system's data protection, and on macOS the keychain. GetMan's own files (`state.json`, `history.json`)
only hold a reference to each secret, never its value. Usernames are not secret and stay in the state file. See
[Troubleshooting](troubleshooting.md#where-is-my-data-stored).

- **Upgrading:** secrets saved by GetMan 0.7 or older (in plain text) move into secure storage automatically the
  next time GetMan saves.
- **If secure storage is unavailable**, for example on Linux after you cancel the master-password prompt, GetMan
  keeps working and saves secrets in the state file as before. It warns you once per session.
- **Export:** when you export a collection that contains secrets, GetMan asks whether to **Leave Out Secrets**
  (the default) or **Include Secrets**. Leaving them out keeps the auth type and username, so whoever imports the
  file only fills in the secret. Exported files never refer to your secure storage, so they work on any machine.

To keep a token out of individual requests altogether, put it in an environment variable marked **Secret** and use
`{{token}}` in the Auth section (see [Environments and variables](environments.md#secret-variables)).
