<p align="center"><img src="docs/logo.svg" width="128" alt="Encore logo: a record whose label is a lowercase e"></p>

<h1 align="center">Encore</h1>

<p align="center">
  A music player for your own music server, for Android phones and Wear OS watches.<br>
  Dark and minimal, made for <a href="https://github.com/d-safro/conductor">Conductor</a>, and at home with Navidrome or any Subsonic server.
</p>

<p align="center">
  <a href="../../releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/d-safro/encore?label=release"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-3ddc84">
  <img alt="Wear OS" src="https://img.shields.io/badge/Wear%20OS-Encore%20Baton-4285f4">
  <a href="https://github.com/d-safro/encore-desktop"><img alt="Desktop app" src="https://img.shields.io/badge/desktop-Linux%20%7C%20Windows-555"></a>
</p>

## What it does

- **Plays your library** from Conductor, Navidrome or any Subsonic / OpenSubsonic server, and the music files already on your phone.
- **Works offline:** download songs, albums and playlists with their covers and lyrics; resumes downloads after a restart; plays over a USB cable to the server's computer.
- **Feels like the big streaming apps:** Home shelves, Made for you, mixes (Repertoire), radio, smart shuffle, Automix crossfades, synced lyrics with translation (Libretto), a year in review (Curtain Call).
- **Finds music you don't have yet** (with Conductor): search, artists and album pages show it (⬇); playing it
  fetches it into the Temporary playlist for a while, and hearting it keeps it for good.
- **Sounds even:** songs play at the same loudness (ReplayGain, by track or by album); with Conductor also the songs
  whose files don't say how loud they are, which the server measures itself.
- **Together:** listen with friends (Ensemble), edit playlists together (Chorus), control your other devices, cast to speakers and TVs.
- **Dark by default**, with a light theme, Material You colours, and full screen-reader support.

The full list is in [docs/FEATURES.md](docs/FEATURES.md).

## Install

### Phone
Download an APK from the [latest release](../../releases/latest):

| File | For |
|---|---|
| `encore-<version>-arm64-v8a.apk` | almost every phone from the last ~8 years (**pick this if unsure**) |
| `encore-<version>-armeabi-v7a.apk` | older 32-bit phones |
| `encore-<version>-x86_64.apk` | emulators, Chromebooks |
| `encore-<version>-universal.apk` | any device (bigger download) |

Open it and allow installing from your browser or file manager (Android 8.0 or newer). Every release is signed with
the same key, so new versions install over old ones and keep your data. Encore checks for new versions about twice
a day (you can turn this off, or use Settings › Check now); Update downloads the new version, checks it against the
release's `SHA256SUMS.txt` and hands it to Android's installer.

Sign in with your server's address (e.g. `https://music.example.com`), user name and password. The password stays in
Android's encrypted storage. Add more servers in Settings; each keeps its own downloads, queue and pins.

### Watch: Encore Baton
`encore-baton-<version>-wear.apk` from the same release is the Wear OS app. It is a remote for the phone (heart, queue
and library included), and also plays on its own over Wi-Fi or LTE to Bluetooth headphones, with downloads, a tile
and a watch-face complication. Install it on the watch with `adb` over Wi-Fi; the steps are in
[android/wear/README.md](android/wear/README.md). It signs in with the phone's server.

### Desktop
The Linux and Windows app lives in its own repository: **[encore-desktop](https://github.com/d-safro/encore-desktop)**.

## Which server?

Encore plays from any Subsonic / OpenSubsonic server, such as [Navidrome](https://www.navidrome.org). Browsing,
playback, downloads, lyrics, playlists and favourites all work there.

[**Conductor**](https://github.com/d-safro/conductor) is the server made for Encore (1.0 or newer). It adds:
- music you don't have yet, and getting it;
- Made for you and your mixes, without any AI to set up;
- sound levelling for songs without loudness tags;
- concerts near you;
- listening stats and the year in review;
- Ensemble and Chorus;
- podcasts;
- devices that control each other;
- song ID;
- Automix analysis.

Without it, those buttons and rows simply don't appear.

## Building

Needs Flutter 3.50+ and the Android SDK (platform 36+).

```bash
flutter build apk --release --split-per-abi
```

The APKs are in `build/app/outputs/flutter-apk/`. `tools/release/upload.sh v<version> <files>` attaches them to
the GitHub release (made as a draft if it isn't there yet) and keeps the release's `SHA256SUMS.txt` whole.
Release builds are signed with the key described in `android/key.properties`, or the file named by the
`ENCORE_KEY_PROPERTIES` environment variable:

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Neither file is in git. Without one, release builds are signed with the debug key.

## Layout

```
lib/
  main.dart                   start-up: encore_core's runEncore with the phone's hooks
  version.dart                the app's version (keep it in step with pubspec.yaml)
  phone/host.dart             the hooks: Android's installer and download notification, the home-screen widget
  phone/usb_link.dart, usb_card.dart   music over a USB cable
  phone/wear_link.dart        a paired Wear OS watch signs in to the same server (Wearable Data Layer)
android/app/src/main/kotlin   audio-device channel, home-screen widget, keep-running-while-downloading service
android/wear                  Encore Baton, the Wear OS app
android/wearlink              what the phone and the watch share over the Data Layer
tools/encore-usb.sh           run on the server's computer: downloads over a USB cable (adb reverse)
tools/release/upload.sh       attaches files to a release and keeps its SHA256SUMS.txt whole
```

Everything else (screens, player, server client, downloads, settings) is in encore_core.

## Changing the shared code (encore_core)
Everything both apps share (the server client, playback, downloads, settings, the shared screens and widgets)
is in [encore-core](https://github.com/d-safro/encore-core), the `encore_core` package. This app's
`pubspec.yaml` takes it from git at a tag:

```yaml
  encore_core:
    git:
      url: https://github.com/d-safro/encore-core.git
      ref: v1.6.1
```

CI and release builds use that tag. To work on the shared code next to this app, clone both side by side
(`../encore-core`) and add a `pubspec_overrides.yaml` here (it's in `.gitignore`):

```yaml
dependency_overrides:
  encore_core:
    path: ../encore-core
```

Then `flutter pub get`, and changes in `../encore-core` show up at once (hot reload included). To ship them:

1. In encore-core: commit, run `flutter analyze` and `flutter test`, push, then tag a new version
   (`git tag v1.1.0 && git push origin v1.1.0`; also bump `version:` in its `pubspec.yaml`).
2. Here: set `ref:` to the new tag, then move `pubspec_overrides.yaml` aside and run `flutter pub get`, so
   `pubspec.lock` records the git version (not your local folder), and commit `pubspec.yaml` and `pubspec.lock`.
3. Bump the other app to the same tag when it should get the change too.

encore_core never imports either app. Where shared code needs something only one app has, it asks the app
through `EncoreHost` (encore_core's `lib/core/host.dart`), which this app fills in at start-up.

## Credits

Font: [Inter](https://rsms.me/inter/) by Rasmus Andersson, SIL Open Font License 1.1 (`assets/fonts/OFL.txt`).
Lyrics: [LRCLIB](https://lrclib.net), NetEase Cloud Music, [lyrics.ovh](https://lyrics.ovh).
Covers, artist pages and share links: iTunes Search API, Deezer API, [Odesli / song.link](https://odesli.co),
Wikipedia. Radio stations: [Radio Browser](https://www.radio-browser.info).
