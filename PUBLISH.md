# Publishing PrivAIBrowser to GitHub

The repo `smcorner/PrivAIBrowser` already exists (empty, public). Upload these files to it, then:

1. **Actions** tab -> *Build & Release Android APK* runs automatically on push to `main`.
   Wait for the green tick (about 4 minutes). It publishes release `v1.0.1` with `PrivAIBrowser.apk`.
2. **Settings -> Pages** -> Source: *Deploy from a branch* -> Branch `main`, folder `/docs` -> **Save**.
3. Open `https://smcorner.github.io/PrivAIBrowser/` on your phone and tap **Download Latest APK**.

## Uploading from a computer (fastest)

```
git clone https://github.com/smcorner/PrivAIBrowser.git
cd PrivAIBrowser
# copy every file and folder from this archive into this folder, including .github
git add -A
git commit -m "Initial commit: PrivAIBrowser Android app"
git push origin main
```

No terminal? On the repo page: **Add file -> Upload files**, then drag in all the files
and folders (`.github` included, it is hidden on macOS: press Cmd+Shift+. to show it),
then **Commit changes**.

## What is in here

- `app/` - Android source (Kotlin, WebView browser, AI panel, injected JS, layouts, icons)
- `.github/workflows/build-apk.yml` - CI that builds the APK and publishes a release
- `docs/index.html` - the download page served by GitHub Pages
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` - Gradle/AGP 8.4.2, Kotlin 1.9.24
