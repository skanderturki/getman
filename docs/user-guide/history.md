# History

Every request you send, and every [collection run](collections.md#running-a-collection), is recorded in the
**History** tab next to Collections.

## What you see

- **Grouping:** entries are grouped by **Today**, **Yesterday** and older dates, newest first.
- **Each entry shows:**
  - the method and the URL that was actually called, with variables filled in;
  - the status (coloured), or *error*;
  - the response time and the time it was sent;
  - **AI assistant** for requests an AI assistant sent through the [MCP server](ai-assistants.md).

## Collection runs

Each run of a collection or folder is **one group** in History, for example
**Run orders-api › Customers  20 passed, 3 failed  Local · 15:28:50**. It is shown with an error icon when a
request failed or got no response.

- **Expand it** to see the result of each request: Passed, Failed, Sent (no expected status), Error or Not sent,
  with its status and details.
- **Double-click the run** (or right-click › **Show Results**) to open its results window. **Run Again** runs the
  same collection or folder again, and the new run gets its own group.
- **Double-click a result** to open that request from its collection.
- **Export:** right-click › **Export…**, or **Export…** in the results window, saves an HTML report, a CSV file or
  a JUnit XML file.
- **AI assistant** marks runs an AI assistant started through the [MCP server](ai-assistants.md).

## Using history

- **Open again:** double-click an entry to open it in a new tab. The request keeps its `{{variables}}`, so it works
  in whichever environment is selected now.
- **Clean up:** right-click an entry or a run to delete it, right-click a day to delete that day's entries, or use
  **Clear History…** (requests and runs).

## What is kept

- **Stored:** the request as you wrote it, the URL called, and the response summary (status, time, size).
- **Not stored:** response bodies, to keep the history small.
- **Where:** history is saved in its own file, next to the main state file (see
  [Troubleshooting](troubleshooting.md#where-is-my-data-stored)).
- **Runs:** for each request of a run, its result, status, time and details; not response bodies. Values of secret
  variables are replaced with `[secret]`. Runs are saved in `runs.json`, next to the history file.
- **How much:** the last 100 requests by default. Change the number, or set it to 0 to turn history off (runs are
  then not kept either), in [Preferences](preferences.md). The last 20 runs of each collection are kept, at most
  100 runs in all.
