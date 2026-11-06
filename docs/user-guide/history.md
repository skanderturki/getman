# History

Every request you send is recorded in the **History** tab next to Collections.

## What you see

- **Grouping:** entries are grouped by **Today**, **Yesterday** and older dates, newest first.
- **Each entry shows:**
  - the method and the URL that was actually called, with variables filled in;
  - the status (coloured), or *error*;
  - the response time and the time it was sent;
  - **AI assistant** for requests an AI assistant sent through the [MCP server](ai-assistants.md).

## Using history

- **Open again:** double-click an entry to open it in a new tab. The request keeps its `{{variables}}`, so it works
  in whichever environment is selected now.
- **Clean up:** right-click an entry to delete it, right-click a day to delete that day's entries, or use
  **Clear History…**.

## What is kept

- **Stored:** the request as you wrote it, the URL called, and the response summary (status, time, size).
- **Not stored:** response bodies, to keep the history small.
- **Where:** history is saved in its own file, next to the main state file (see
  [Troubleshooting](troubleshooting.md#where-is-my-data-stored)).
- **How much:** the last 100 requests by default. Change the number, or set it to 0 to turn history off, in
  [Preferences](preferences.md).
