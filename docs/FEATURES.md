# Everything Encore does

The full list, for the phone and the desktop app alike (they share all of this through
[encore-core](https://github.com/d-safro/encore-core)). Some rows need [Conductor](https://github.com/d-safro/conductor)
as the server; with plain Navidrome they just don't appear.

**Home and library**
- Home: greeting, quick pills (shuffle all, liked songs, made for you, continue from another device),
  "Jump back in", recently added, most played, favourites, something different, top rated, your playlists
  (choose and order the sections).
- Library: Liked songs pinned on top like a playlist, plus your own pinned playlists and albums (long-press → Pin).
- Library tabs: playlists, albums (grid or list, sort orders, endless scrolling), artists, songs, liked songs,
  genres; folder browsing; internet radio stations from the server.
- Artist pages like Spotify's: fans, their top 10 (yours or ⬇), the whole discography (albums, singles & EPs,
  compilations; ⬇ for what you don't have), a bio (your server's, else Wikipedia), similar and sound-alike artists.
- Album, playlist and genre pages; share links (song.link) that open in any music app.
- Search across the server; recent searches remember the songs, albums and artists you opened.
- Concerts near you (a row on Home, and Concerts): shows of the artists you play most and follow, near the town
  you set under Following › Shows, checked once a week by your server (Ticketmaster with your key there, else
  MusicBrainz). Tickets, Add to calendar, and a notification when a new one is announced.
- Your listening: minutes, plays, top artists / songs / albums and when you listen, by week, month, year or
  all time (needs Conductor).
- Your year (or a month) in review: a story of cards (top artists, songs, albums and genres, the first song,
  your longest streak, your hour, songs you found and kept) coloured from each one's art, ending with a picture to
  share, 9:16 or square (saved to Downloads on the desktop). From Stats, and a notification on 1 December.
- Your mixes (a row on Home, made by your server as real playlists, so every app has them): Discover Weekly
  (Mondays, 30 songs you don't have, fetched for a week, heart to keep), Release Radar (Fridays, new releases
  from artists you follow and play most), Daily Mix 1–6 (your library around your top artists, some songs new to
  you) and the Daylist (what you play at this time of day). Each can be turned off in Settings.
- Smart playlists (Navidrome's own, so every app sees them): one-tap presets like "Forgotten favourites" and
  "On repeat", or your own rules.
- Folders: your real folders on disk (with Conductor), else Navidrome's artist / album view.

**Playback**
- Background playback, media notification, lock screen, Bluetooth / headset buttons, home-screen widget,
  Android Auto (Home / Playlists / Albums / Radio tabs, smart shuffle button, voice search, resume). Android
  Auto hides apps not installed from the Play Store until you allow "Unknown sources" in its developer settings
  (tap the version in Android Auto's settings 10 times).
- Exact play / pause / position reported to the server (OpenSubsonic playbackReport), so "now playing" in other
  apps and Discord-status tools is right.
- Gapless queue: play now / next / last, drag to reorder, swipe to remove, save as playlist; restored after a
  restart and kept on the server (continue in another app or device).
- Shuffle, repeat, speed and pitch, skip silence, sleep timer (minutes or end of song).
- Crossfade (up to 12 s); albums played in order stay seamless unless you choose otherwise.
- DJ (Settings › Playback, off at first): before every song, every 3rd or 5th, a line about the next one,
  written by your server's AI (Ollama, from what your library knows: title, album, year, your plays) and read by
  its voice (Piper), spoken over the song's start while the music dips, in your phone's language or the one you
  pick (13 languages). Only while AI is on at the server.
- Automix (Settings › Playback): the next song comes in where this one starts to fade, blending for 4 bars at its
  tempo (2–12 s); silence at the end of a song and at the start of the next is cut. Your server measures each
  song (mix-analyzer, the songs in your queue first); songs it hasn't measured use the crossfade.
  Edit transitions (a playlist's menu): Smart, Fade or Cut for each song into the next, kept for that playlist
  and synced to your other devices.
  When two songs' tempos are within 6%, the next one comes in at the first one's tempo (pitch kept), beat on
  beat, and eases back to its own during the blend. The queue shows each song's BPM.
- Cast to Chromecast / Google Cast speakers and TVs on your Wi-Fi. The phone stays the remote and passes
  the music along, so it works even when the speaker can't reach your server (e.g. a Tailscale-only server).
- UPnP / DLNA speakers, TVs and AV receivers show in the same list ("Play on"), found on the Wi-Fi and played
  through the same relay, with their volume in the sheet. Generic UPnP: Sonos-specific behaviour isn't handled.
- ReplayGain (track / album / automatic, pre-amp, peak protection), 5-band equalizer with presets.
- What's playing? (Search): Encore listens for 10 s, with the microphone on the phone or to what the computer
  plays on Linux, and your server recognises it among your own songs (song-id: fingerprints of your library,
  offline, nothing sent anywhere). Finds are kept under "Identified before".
- Synced lyrics (tap a line to jump), plain lyrics; online from LRCLIB, NetEase and lyrics.ovh (order and
  sources in Settings), and "Wrong lyrics?" to pick another match.
- Lyric cards: hold a line in the lyrics for a picture of it (up to four lines) with the cover and its colours,
  to share, or to save on the desktop.
- Translate lyrics (a chip over the words): a muted second line under each line, in your phone's language or
  the one in Settings; Romanize for Japanese, Korean, Chinese, Cyrillic and other alphabets. Done by your server's
  own AI (music-lookup + Ollama, only while AI is on), cached per song: nothing leaves home.
- Audiobooks (sign in to Audiobookshelf under Settings › Audiobooks; an Audiobooks tab appears): Continue
  listening, every book, chapters; played by Encore's own player (speed, sleep timer, lock screen) with your place
  kept on Audiobookshelf, so its apps carry on from there. "Download for offline" keeps a book on the device
  (it plays from there, also with no connection).
- Internet radio: find stations in the Radio Browser directory or paste an address; shows the song playing.
- Full-screen player coloured from the album art; swipe the cover to skip, double-tap to heart.

**Sound and devices**
- One panel for output device (switch to Bluetooth / speakers with Android's output picker), system volume,
  speed, pitch, equalizer, loudness, skip silence, and shortcuts to Android's mono-audio / left-right balance
  and Bluetooth settings.

**Podcasts**
- A Podcasts tab: follow shows (search Apple's podcast directory or paste a feed), see new episodes on Home,
  resume where you stopped (kept on the server, so it carries over to other apps), mark played, show notes,
  download episodes to the phone (or save them on the server). A notification when a show you follow has a new
  episode (checked about hourly). Needs Conductor (music-lookup's podcast support).

**Jam (like Spotify Jam)**
- Start a Jam and share the link or QR code: friends search your library (and songs you don't have, which
  download first) and add, reorder, remove or skip from their phone's browser. Needs Conductor and a
  public address for the Jam page (Tailscale Funnel or similar).

**Song radio and discovery**
- Smart shuffle (like Spotify's): tap shuffle twice. It shuffles the list and mixes in a song that fits about
  every three songs, marked ✦; keep one with ⊕ (saved to that playlist or your liked songs) or drop it with ⊖
  and it won't be suggested for that list again. Shuffle buttons on pages remember which kind you used last.
- Song radio from any song: the song, then songs that sound like it, and it keeps going as you listen.
- "New to you": songs in your library you've never played that match your taste.
- Discovery rows and "More like this, not in your library": songs you don't have, picked from what you play;
  preview them or tap to fetch them.

**AI recommendations** (needs AudioMuse-AI, see below)
- Made for you (from your listening "fingerprint"), moods, describe a sound in words, find songs about a topic
  from lyrics, song alchemy (add and subtract songs or moods), and a path from one song to another.

**Downloads (like Spotify's download switch)**
- Switch on download for an album, playlist, your liked songs, or the whole library (with a size estimate);
  new songs added to them download too. Or download single songs.
- Download quality like Spotify's: Low, Normal, High (the default, about 72 MB an hour) or Very high, made
  smaller as Opus on your server, or Lossless (the original files). Changing it offers to shrink songs already
  downloaded; each keeps playing from the phone until its smaller copy has arrived. Wi-Fi only option;
  downloaded copies play first and work offline.
- No duplicates: the same recording that's in your library twice is stored once.
- Downloads never start over: a song that stops part-way (no signal, the app closed, the phone restarted)
  carries on from the byte it reached once the app is open and the server can be reached, and nothing you
  asked for is forgotten. Downloads keep going in the background, with a quiet "Downloading" notification.
- Over a USB cable: plug the phone (USB debugging on) into the computer that runs your server and run
  `tools/encore-usb.sh` there. Everything (browsing, playing, downloads) then goes through the cable, without
  Wi-Fi, internet or a VPN; Settings › Downloads › Copy all fetches the whole library. Unplug any time: it
  carries on over the network.
- Address on your home network (Settings › Server, optional): on the same Wi-Fi as your server, Encore goes
  straight there instead of through a VPN or the internet, and uses the main address everywhere else.
- Offline mode: the whole library index is kept on the phone (refreshed every 12 hours) for browsing and
  search; plays made offline are scrobbled later. When the server can't be reached (Tailscale off, the server
  asleep) the app notices within seconds and keeps working with what's on the phone.

**Covers**
- Missing album art is found online (iTunes, then Deezer), and artist pictures from Deezer. Can be switched off.

**Your stuff**
- Music on this device (Library › Songs › On this device; On this computer in the desktop sidebar): music files
  that aren't in your server's library. On the phone, Android's own list of audio files (asks for "Music and
  audio" the first time); on the desktop, the folders in Settings › Desktop (~/Music at first), scanned and
  kept up to date. Their tags and covers are read on the device. They show in search, play offline, go in the
  queue, and in playlists kept on this device (with server songs too). They stay private: never uploaded,
  scrobbled, or shown as now playing. Open a file with Encore from a file manager (Android: "Open with";
  desktop: double-click, `encore song.flac`, or drop files on the window).
- Hearts, 1–5 star ratings, playlists (create, add, rename, reorder, remove, delete), scrobbling.
- Import a playlist from another app (Library › Playlists › + › Import from another app): a CSV export
  (Exportify, TuneMyMusic, Soundiiz…), Spotify's "Download your data" JSON, an M3U or a text file of "Artist -
  Title" lines. Each song is looked for on your server; ones you don't have can be got, then the playlist is made.
- Don't suggest this song / Don't suggest <artist> (a song's menu): left out of autoplay, smart shuffle, radio,
  instant mixes and the weekly mixes, still played when you pick them; undo in Settings › Not suggested. Synced.
- Invite to edit (a playlist's menu): a link like Jam's lets friends add songs from your library to that playlist
  on a web page, and take out the ones they added; it works until you stop it (or 30 days).
- Private session (Settings, or the devices sheet in the player): for up to 6 hours nothing you play is
  scrobbled, counted in stats, shown as "now playing" (Discord) or learned by autoplay. A lock in the player shows it.
- Add a whole album to a playlist (album page, long-press or right-click on an album): asks before adding songs
  the playlist already has, and a toast offers Undo.
- Follow artists (Follow on their page; Library → Artists → Followed artists). Follows are kept by the server's
  extras for your account, so the phone and the desktop share them. New releases (albums, EPs, singles, also
  upcoming ones and ones you don't have, as ⬇) from MusicBrainz and Deezer show on Home and under Following,
  checked once a day, with an optional notification on release day.
- Upcoming shows on artist pages and under Following → Shows, with a "near me" box (a city you type, no
  location permission). MusicBrainz events without setup; add a free Ticketmaster key for more shows and
  ticket links.

**Look**
- Screen readers (TalkBack): every button is labelled; a song row reads as one item ("title, artist, length,
  playing, downloaded, liked") with Play next / Like / More as actions; the cover's swipes and double-tap are
  actions too, and the seek bar says "1:12 of 3:40". Reduce animations (Appearance, or the phone's own "Remove
  animations") stops page slides, colour fades and smooth scrolling.
- Choose the app icon: Encore's, a line style that matches icon packs like Arcticons, or the original.
- Phones, tablets and landscape: a side rail and a side-by-side player on wide screens.
- Inter type, near-black surfaces, colour from your wallpaper (Material You), the playing album's art, or a
  colour you pick; pure black option for OLED.
