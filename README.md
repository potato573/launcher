# AURA

A minimal, fast Android home screen. Kotlin on the plain Android framework: no AndroidX,
no Compose, no third-party libraries. minSdk 26 (Android 8.0), targetSdk 35.

## What works today
- **Default launcher** through the HOME intent; on Android 10+ AURA asks for the Home role
  with the system dialog (Settings › Default home app, or the card on the home screen).
- **Home screen**: clock and date (tap to open alarms / calendar), paged favourites grid,
  page dots, translucent dock (up to 5 apps), search pill.
- **App drawer**: swipe up anywhere on home, the sheet follows your finger and a spring
  carries the release velocity. Instant search (accents and case ignored, word prefixes,
  initials like `ytm`, typo-tolerant subsequences); Enter opens the top result,
  or searches the web when nothing matches.
- **Long press** an app: its shortcuts (when AURA is the default launcher), Add to Home /
  Remove, App info, Hide, Uninstall. Long press empty space: Wallpaper, Settings.
- **Hidden apps**: removed from the drawer, still found by search.
- **Settings**: theme (auto/light/dark), accent colour, icon size, home grid columns and rows,
  drawer columns, labels, clock, search bar, dock, keyboard-on-open.
- **Backup / restore** of layout, settings and hidden apps to a JSON file (system file picker,
  no storage permission).
- Work profile apps (badged), live updates on install/uninstall, wallpaper parallax across
  pages, text colour that adapts to light wallpapers. English and Indonesian.

## Design notes
- Motion uses an analytic spring (`anim/Spring.kt`): the same curve at 60, 90 and 120 Hz,
  driven by `Choreographer`, stopping as soon as it rests. On Android 15 the launcher asks for
  the high refresh rate only while something moves.
- Icons are rendered once to hardware bitmaps at their on-screen size and kept in an LRU cache.
- Layout is an immutable model (`model/HomeLayout.kt`) written atomically off the UI thread.

## Build
- `./gradlew testDebugUnitTest` – JVM unit tests and Robolectric smoke tests.
- `./gradlew assembleDebug` – APK in `app/build/outputs/apk/debug/`.
- GitHub Actions builds every pull request: download **AURA-apks** from the run page.

## Use as your launcher
Install the APK, press Home and choose **AURA** › **Always**, or open AURA and tap
**Set as default**.
