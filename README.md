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
├── database.rules.json                Firebase Realtime Database security rules
└── storage.rules                      Firebase Storage security rules (photo uploads)
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
3. Deploy `firebase/database.rules.json` as your Realtime Database security rules. `shared/settings`
   is locked to admin/console writes only — the allowlist is the authorization boundary for
   everything under `shared/*`, so clients must not be able to modify it themselves.
4. Under `shared/settings`, set `allowedUsers` as a **map keyed by UID** (not an array — Realtime
   Database security rules match child keys, and arrays are stored as numeric indices, so an array
   would silently fail the `.child(auth.uid).exists()` check in the rules and lock everyone out):
   ```json
   "allowedUsers": {
     "uid1": true,
     "uid2": true
   }
   ```
5. Deploy `firebase/storage.rules` as your Storage security rules (Storage → Rules tab). Note:
   Storage rules can't reference Realtime Database data, so they can't check `allowedUsers` the
   way the database rules do — access there is scoped to "signed in" (any authenticated Firebase
   user) plus per-UID write paths, not specifically the two allowed people. Fine for a private
   personal project, but worth knowing.

## CI

`.github/workflows/build.yml` builds a debug APK on every push/PR to `main` as the merge gate.
On push to `main` it additionally builds a **signed release APK** and publishes it as a GitHub
Release, if release-signing secrets are present (skipped otherwise, so the pipeline stays green
without them).

### Firebase secret

Add a repo secret named `GOOGLE_SERVICES_JSON` containing the base64-encoded contents of your
real `google-services.json`:

```bash
base64 -w0 app/google-services.json | pbcopy   # or xclip/clip depending on OS
```

Without it, CI builds against the checked-in placeholder config.

### Release signing secrets

Add these four repo secrets to get signed release APKs published as GitHub Releases on every
merge to `main`:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | base64-encoded contents of the release `.jks` keystore |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | key password (same as keystore password for PKCS12 keystores) |

The same keystore must also have its SHA-1/SHA-256 fingerprint registered on the Firebase
Android app (Project settings → Your apps → Add fingerprint) — Google Sign-In checks the
signing certificate of whatever APK is installed, so a release APK signed with a keystore whose
fingerprint isn't registered will fail to sign in.

Without these secrets, CI still runs `assembleDebug` as the merge gate but skips the release
build and GitHub Release step entirely.
