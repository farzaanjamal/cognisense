# Building the app without Android Studio (GitHub Actions)

You do not need Android Studio or a powerful computer. GitHub builds the app on its own servers for free and gives you an APK to download. Any Android phone (8.0 or newer) can then install it from that download; no cable and no developer settings are needed on the phone.

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

## One-time setup

1. Create a free account at github.com.
2. Create a new repository (for example `cognisense`).
   - **Private** keeps the code private.
   - **Public** is needed if you later want the free GitHub Pages link for reviewers on a free account.
3. Upload the project. The easiest way is **GitHub Desktop** (desktop.github.com):
   1. Unzip `cognisense.zip`.
   2. In GitHub Desktop: *File → Add local repository* → choose the `cognisense` folder. Accept "create a repository" if asked.
   3. *Publish repository*.

   **Do not use the website's drag-and-drop upload.** It skips the hidden `.github` folder, which contains the build instructions. Without it, nothing builds.

## Every time you want an APK

1. Push your changes (GitHub Desktop: *Commit*, then *Push*).
2. On github.com, open the repository → **Actions** tab → the latest **build** run.
3. Wait for it to finish, usually a few minutes:
   - **Green tick:** everything passed.
   - **Red cross:** open the failed step and copy the complete error text into a message to me.
4. At the bottom of the run page, under **Artifacts**, download `cognisense-debug-apks`. Unzip it. It contains two apps:
   - `app-standard-debug.apk`: the full app (Session mode, dashboard).
   - `app-reviewer-debug.apk`: Expert Review only, the version for reviewers.
5. Send the APK to any Android phone (email, WhatsApp, Drive) and open it there. Installing it is explained in `docs/reviewer_apk_install.md`.

## What the cloud build checks

| Check | What it proves |
|---|---|
| Core tests | The task logic, config, sequences and scoring behave as specified (simulation) |
| Content-validity script tests | `analysis/cvi.py` computes the published worked values |
| Browser preview rebuild | The committed preview was built from the current code and config |
| Browser end-to-end test | All five tasks run to completion in Chrome; no network requests; no errors |
| Android build, both variants | The app compiles and packages with the real Android tools |
| Manifest check | The installed app requests no permissions at all |

**What it cannot check:** whether the app behaves correctly on a real phone, and anything about timing. Those need one run on a real Android device (`android/README.md`, smoke test).

## The first build will probably fail

The Android code has been compiled against the Android API but never built with Gradle. The first cloud run is the first real build. Errors at that point are expected and usually quick to fix: send the error text.
