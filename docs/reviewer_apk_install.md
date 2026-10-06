# Installing Cognisense Review (for expert reviewers)

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

**Cognisense Review** is the version of the app for the expert panel.

- It opens directly in **Expert Review**: each task with a short description, a practice block, a shortened scored block and a plain summary of what was recorded.
- It has **no session mode, no participant entry and no dashboard**, so it cannot be used to test children.
- It needs **Android 8.0 or newer**.
- No iPhone? Use the browser preview link instead. It runs the same task logic in any browser, but its timing is not representative.

## Install (about 2 minutes)

1. **Download** the file `cognisense-review-<version>.apk` from the link you were sent, on the Android phone itself.
2. **Open** the downloaded file (from the notification, or the *Files / Downloads* app).
3. **Allow installs from this source.** Android blocks apps from outside the Play Store by default. It will offer a *Settings* button. Turn on *Allow from this source* for the app you are installing from (your browser or *Files*), then go back and tap **Install**.
   - On Android 8–14, this permission belongs to that one app only. You can turn it off again afterwards: *Settings → Apps → [browser or Files] → Install unknown apps*.
4. **Open** *Cognisense Review*.

## What the warnings mean

| Warning | Meaning |
|---|---|
| "For your security, your phone is not allowed to install unknown apps from this source" | Standard for any app not from the Play Store. It says nothing about this app's content. |
| Google Play Protect: "Unrecognised app" / "App not scanned" | The app has not been submitted to Google Play. Choose *Install anyway* (sometimes under *More details*). |
| Anything mentioning **permissions** | This app should request **none**. If you see a request for internet, camera, microphone, contacts or location, **stop and do not install**: it is not the genuine file. |

## Check that your file is genuine (optional)

Each release lists a SHA-256 checksum next to the APK. The checksum is a fingerprint: if it matches, the file is exactly the one that was built.

- **On a computer:**
  - Windows: `certutil -hashfile cognisense-review-<version>.apk SHA256`
  - macOS/Linux: `shasum -a 256 cognisense-review-<version>.apk`
- **Then compare** the result with the published value.
- **After installing**, *Settings → Apps → Cognisense Review → Permissions* should show **no permissions**.

## Data and removal

- Everything you do stays on the phone. The app has no internet permission, so it physically cannot send data anywhere.
- Uninstalling (*Settings → Apps → Cognisense Review → Uninstall*) deletes the app and everything it recorded.

## Status of this build

This is a research prototype under expert review. Its task parameters are proposals for you to rate, and its Urdu text is an unvalidated draft. Reaction times shown in the summaries are software-timed and have not yet been validated against hardware timing.
