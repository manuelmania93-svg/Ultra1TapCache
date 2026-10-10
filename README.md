# Ultra1TapCache

An experimental, ad-free Android cache cleaner. Original implementation with the supplied app icon. Android 11+; intended first test: Android 15. German UI, German and English Android Settings labels supported.

## What it does

- Reads installed apps and cache sizes through Android StorageStatsManager after the user grants Usage Access.
- Lets you select apps, including an optional system-app list.
- Starts a finite accessibility session from an explicit button press. It opens each selected app's Settings page, looks for exact storage/cache labels, then clicks only Clear cache.
- Turbo shortens waits; it does not bypass Android or guarantee speed.
- Shows a STOP accessibility overlay throughout the session, with a 12-second timeout per app. No overlay means no cleaning.
- Reports measured cache reduction separately from clicks and unconfirmed/skipped apps. A successful click alone is not proof of cleaned bytes.

No ads, tracking, network permission, scheduled background cleaning, root or privileged cache-deletion API. No app data, uninstall, force-stop or generic dialog-confirmation clicks.

## Install and test

Download the debug APK artifact from the Actions build. APK sideloading requires allowing installation from the download app. Android may require App info > menu > Allow restricted settings before enabling an accessibility service installed from a file. Enable Usage Access, scan, enable the accessibility service, then begin with one nonessential app. The exact OEM flow needs a real phone test. Unknown Settings packages, unrecognized labels, generic confirmation dialogs, and unexpected pages fail closed. Nubia/Red Magic support is not verified yet.

A rebuilt GitHub Actions debug APK can have a different signing key. Uninstall the previous experimental build before installing a new one if Android reports a signature conflict. That resets this app's settings only.

## Limits

Cache is useful temporary data; Android manages it itself and cleared cache can grow again immediately. No performance or battery improvement is promised. Unknown cache values are shown as not measurable, never silently treated as zero. Measured reduction is a before/after observation, not proof that every byte changed because of this app. Stop and service-disconnect invalidate in-flight session callbacks.

## Build

JDK 17, SDK 35, Gradle 8.9, Android Gradle Plugin 8.7.3:

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Debug only, not a Play Store release. Accessibility automation and installed-package visibility need a separate policy review before any store submission. No release key created or changed.
