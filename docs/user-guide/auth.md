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

Like the rest of GetMan's data, tokens and passwords are saved in the workspace's GetMan state file (see
[Troubleshooting](troubleshooting.md#where-is-my-data-stored)).

- **Export:** when you export a collection that contains secrets, GetMan asks whether to **Leave Out Secrets**
  (the default) or **Include Secrets**. Leaving them out keeps the auth type and username, so whoever imports the
  file only fills in the secret.
- **Planned:** keeping secrets in Eclipse's encrypted secure storage is on the
  [roadmap](https://github.com/skanderturki/getman/blob/main/docs/ROADMAP.md).
