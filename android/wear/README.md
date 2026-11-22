# Encore Baton (Wear OS)

A native Wear OS app (Kotlin, Compose for Wear OS, Media3), built alongside the phone app in the same
Android Gradle project. It is not Flutter: round screens, the crown, tiles and complications need the
platform's own toolkit.

## What it does

- **Remote for the phone.** Shows what Encore on the phone is playing, with its cover, and controls it:
  play/pause, skip, heart, volume on the crown, the phone's queue (tap to jump) and the phone's library as
  Android Auto sees it (Home, Playlists, Albums, Radio). Wear OS's built-in media controls can't do the heart,
  the queue or the library. It talks over the Wearable Data Layer; on the phone, `WearRemoteService`
  passes each request to Encore's own media session.
- **Plays on its own** over the watch's Wi-Fi or LTE, straight from your Navidrome / Subsonic server:
  recent albums, playlists, favourites, search. Subsonic token auth (`t = md5(password + salt)`, a fresh
  salt per request). Music plays only to headphones; with none connected it opens Wear OS's output
  switcher. While streaming it asks Wear OS for Wi-Fi or LTE, since the Bluetooth link to the phone is too
  slow for music.
- **Sign-in:** the phone shares its signed-in server through the Data Layer, so a newly paired watch signs
  in by itself (or tap *Use phone's sign-in*). You can also type a server on the watch.
- **Downloads:** keep an album or playlist on the watch for offline listening. WorkManager downloads them
  song by song, resuming a cut-off song with an HTTP `Range` request, under a storage cap you set
  (Settings → Storage cap).
- **On the watch face:** a tile (what's playing with play/pause, or one tap to shuffle a playlist you
  choose), a now-playing complication, and an ongoing activity while music plays.
- **Routing like the phone:** the server's home-network address (`localAddress`) while it answers there,
  else the main address. Setting names match the phone's (`server`, `user`, `pass`, `localAddress`,
  `wifiFormat`, `mobileFormat`, `downloadFormat`, `downloadWifiOnly`, `scrobble`…).

The phone and watch share their message format through `android/wearlink`, a plain Kotlin module.

## Build

From `android/` (the phone's Flutter build must have run once, so Gradle can find Flutter's plugin):

```sh
flutter build apk --debug    # once, from the repo root
./gradlew :wear:assembleDebug :wear:assembleRelease
./gradlew :wearlink:test :wear:testDebugUnitTest
```

- APKs land in `build/wear/outputs/apk/` (the repo's `build/` directory, like the phone's).
- The release build uses the phone's key: `android/key.properties`, or the file named by
  `ENCORE_KEY_PROPERTIES`. Without one it is left unsigned. Phone and watch must be signed with the same
  key for the Data Layer to connect them (two debug builds from one computer also match).
- `applicationId` is the phone's (`dev.safro.encore`); the versionCode is `300000000 + the pubspec build
  number`, so it never collides with the phone's.
- The tests include screenshot tests (Roborazzi + Robolectric). They rewrite the PNGs in `screenshots/`:
  every screen on a round 454 px watch and a square one, awake and in ambient mode.

## Install on a watch

Over Wi-Fi with adb (Wear OS 3 and later). On the watch:

1. Settings → System → About → Versions: tap *Build number* seven times to turn on Developer options.
2. Settings → Developer options: turn on *ADB debugging* and *Wireless debugging*.
3. In *Wireless debugging*, tap *Pair new device*. The watch shows a pairing code and an address with a
   port.

On the computer (same Wi-Fi as the watch):

```sh
adb pair WATCH_IP:PAIRING_PORT          # enter the code the watch shows
adb connect WATCH_IP:PORT               # the address on the Wireless debugging screen (a different port)
adb -s WATCH_IP:PORT install -r build/wear/outputs/apk/debug/wear-debug.apk
```

Install the phone app from the same build (or signed with the same key), sign in on the phone, and open
Encore on the watch: it picks up the phone's server. Turn *Wireless debugging* off again when done; it
uses battery.

To publish on Google Play, upload the watch APK/AAB to the same app as the phone (a Wear OS form factor
release); the separate versionCode range keeps them apart.
