# Getting started

## Requirements

- Eclipse IDE 2024-03 (4.31) or newer, any package (for Java, Enterprise Java, C/C++, …)
- Java 17 or newer (the Java that ships with current Eclipse packages is fine)
- Windows, macOS or Linux

## Install

1. In Eclipse, open **Help › Install New Software…**.
2. Click **Add…** and enter the update site:

   ```
   https://skanderturki.github.io/getman-update-site/
   ```

3. Tick **GetMan REST Client** and click **Next**.
4. **Untick "Contact all update sites during install to find required software"** to keep the install fast.
   GetMan needs nothing beyond what Eclipse already has (see [Troubleshooting](troubleshooting.md)).
5. Accept the license (EPL 2.0), finish the wizard and restart Eclipse when asked.

To install without internet access, download `getman-<version>-update-site.zip` from the
[latest release](https://github.com/skanderturki/getman/releases/latest) and use **Add… › Archive…** in step 2.

To update later, use **Help › Check for Updates**.

## Open the GetMan view

**Window › Show View › Other… › GetMan › GetMan**. The view opens like any other Eclipse view, so you can drag it
wherever you like, maximise it with a double-click on its tab, or move it to a second monitor.

The view has three parts:

- **On the left:** the **Collections** and **History** tabs.
- **On the right:** one tab per open request. Each tab has the request editor at the top and the response at the
  bottom.
- **In the toolbar:** the environment dropdown, New Request, Save, Import cURL, Duplicate, Copy as cURL and New
  Collection. The view menu (▾) has a few more actions, including this **User Guide**.

## Send your first request

1. Type a URL in the URL field, for example `https://httpbin.org/get`.
2. Press `Ctrl+Enter` or click **Send**.
3. The response appears below: status, time, size, HTTP version, the body (pretty-printed when it is JSON) and,
   in the **Headers** tab, every response header.

## Next steps

- [Save the request to a collection](collections.md) with `Ctrl+S` so you can find it again.
- [Use variables](environments.md) so the same request works on localhost, staging and production.
- [Import from Postman or cURL](import-export.md) to bring your existing requests over.

Your open tabs are saved automatically and come back when you restart Eclipse.
