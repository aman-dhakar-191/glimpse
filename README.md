# Glimpse

A widget-first Android app for two people to share real-time messages, photos, and reactions
straight from the home screen. See [`ANDROID_WIDGET_SPEC.md`](ANDROID_WIDGET_SPEC.md) for the
full technical specification.

**Status:** project scaffolding in progress. This commit establishes the Gradle/CI build
pipeline with a minimal placeholder screen; auth, the widget guide, and the Current Message
widget land in follow-up commits.

## Project structure

```
app/
├── src/main/kotlin/com/glimpse/app/   Kotlin sources (MVVM + Clean Architecture)
├── src/main/res/                      Android resources, incl. RemoteViews widget layouts
└── build.gradle.kts                   App module Gradle config
firebase/
└── database.rules.json                Firebase Realtime Database security rules
```

## Building locally

```bash
cp app/google-services.json.example app/google-services.json  # or your real Firebase config
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Firebase setup

1. Create a Firebase project with Realtime Database, Authentication (Google provider), Storage,
   and Cloud Messaging enabled.
2. Download `google-services.json` from the Firebase console and place it at `app/google-services.json`
   (this file is gitignored — never commit real credentials).
3. Deploy `firebase/database.rules.json` as your Realtime Database security rules.
4. Under `shared/settings`, set `allowedUsers` to the UIDs of the two people using the app.

## CI

`.github/workflows/build.yml` builds a debug APK on every push/PR to `main`. To build against a
real Firebase project in CI, add a repo secret named `GOOGLE_SERVICES_JSON` containing the
base64-encoded contents of your `google-services.json`:

```bash
base64 -w0 app/google-services.json | pbcopy   # or xclip/clip depending on OS
```

Without that secret, CI builds against the checked-in placeholder config so the pipeline stays
green for any fork or fresh clone.
