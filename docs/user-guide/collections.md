# Collections and folders

Collections keep the requests you want to reuse. They appear in the **Collections** tab on the left of the view
as a tree of collections, folders and requests.

## Saving a request

- Press **`Ctrl+S`** in a request tab, or click **Save** in the toolbar.
  - The first time, you choose a collection (or create one) and name the request.
  - After that, `Ctrl+S` just updates the saved copy.
- **Unsaved changes:** a tab whose request differs from its saved copy shows a `*` before its name.
- **Save As:** right-click the tab › **Save As…** (also in the view menu) saves a separate copy, in the same or
  another collection.
- **Save All:** **`Ctrl+Shift+S`**, **Save All** in the toolbar, the view menu or the tab's right-click menu, saves
  every open tab that has unsaved changes and is already in a collection. Tabs that are in no collection yet are
  left alone; save them with `Ctrl+S`.
- **Open a saved request:** double-click it in the tree. If it is already open, you are taken to its tab.

## Organising

Right-click in the tree:

| On | Actions |
|---|---|
| A collection | Run Collection…, New Request, New Folder…, Variables…, Rename… (F2), Export…, Delete (Del) |
| A folder | Run Folder…, New Request, New Folder…, Rename… (F2), Move Up/Down, Move To…, Delete (Del) |
| A request | Open, Rename… (F2), Duplicate, Move Up/Down, Move To…, Delete (Del) |
| Empty space | New Collection…, Import (GetMan or Postman)… |

- **Drag and drop:** drag requests and folders to reorder them, or to move them between folders and collections.
  - Drop **onto** a folder or collection to move the item inside it.
  - Drop **between** two items to place it there.
- **Move To…** does the same from a list, without the mouse.
- **Counts:** collections and folders show how many requests they contain.
- **AI:** requests created by an AI assistant through the [MCP server](ai-assistants.md) are marked **AI**. The
  assistant can change or delete only these.

## Running a collection

Right-click a collection › **Run Collection…** (or a folder › **Run Folder…**) to send all its requests, one after
the other, from top to bottom.

- **Pass or fail:** a request with an **expected status** (its [Checks tab](requests.md#checks)) is reported as
  **Passed** or **Failed**. Requests without one are only sent, and shown as **Sent**.
- **Errors:** requests that get no response (connection refused, timeout), or that use
  [unknown variables](environments.md#unknown-variables), are shown as **Error**. Unknown variables are reported
  and the request is not sent.
- **Variables:** the selected environment and the collection's variables are used, as when you send by hand.
- **Saved requests:** the run sends the saved requests. If open tabs of the collection have unsaved changes, you
  can **Save All and Run**.
- **Production:** if the selected environment is marked Production and the run contains POST, PUT, PATCH or DELETE
  requests, you are asked to confirm first.
- **The results window** lists each request with its result, status, expected status, time and details. It stays
  open while you work. **Stop** stops the run, **Run Again** repeats it, and double-clicking a request opens it.
- Runs are not added to History.

An [AI assistant](ai-assistants.md) can create a whole suite of requests with expected statuses from your
project's code, so you can re-run it here at any time.

## Deleting

- **What goes:** deleting a request, folder or collection removes it from the tree. You are asked to confirm.
- **Open tabs stay:** tabs of deleted requests stay open as unsaved requests, so nothing you are working on is
  lost.

## Collection variables

Right-click a collection › **Variables…** to give it its own variables, such as `baseUrl`. They apply to every
request in that collection. If the selected environment defines the same name, the environment's value wins. See
[Environments and variables](environments.md).

## Sharing a collection

- **Export:** right-click a collection › **Export…** writes a `.getman.json` file. You can commit it to Git or send
  it to a colleague, who adds it with **Import (GetMan or Postman)…**.
  - **Secrets:** if requests store tokens or passwords, you choose whether to include them (by default they are
    left out).
  - **Files:** requests that upload files keep their file **paths**. The files themselves are not part of the
    export.
- **Import:** imported collections get new internal ids, so importing the same file twice gives two independent
  copies.
