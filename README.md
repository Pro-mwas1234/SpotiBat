<div align="center">
  <img src="website/icon.png" alt="SpotiBat" width="120" style="margin-bottom: 8px;"/>

  <h1>SpotiBat</h1>
  
  <p>
    <a href="https://deepwikia href="https://github.com/Pro-mwas1234/SpotiBat/stargazers">
      <img src="https://img.shields.io/github/stars/Pro-mwas1234/SpotiBat?style=for-the-badge&logo=starship&labelColor=0d0d0d&color=1DB954" alt="stars"/>
    </a>
    &nbsp;
    <a href="https://github.com/Pro-mwas1234/SpotiBat/releases">
      <img src="https://img.shields.io/github/downloads/Pro-mwas1234/SpotiBat/total?style=for-the-badge&logo=download&labelColor=0d0d0d&color=1DB954" alt="downloads"/>
    </a>
    &nbsp;
    <a href="https://github.com/Pro-mwas1234/SpotiBat/releases/latest">
      <img src="https://img.shields.io/github/v/release/Pro-mwas1234/SpotiBat?style=for-the-badge&logo=github&labelColor=0d0d0d&color=1DB954" alt="version"/>
    </a>
    &nbsp;
    <a href="https://github.com/Pro-mwas1234/SpotiBat/forks">
      <img src="https://img.shields.io/github/forks/Pro-mwas1234/SpotiBat?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="forks"/>
    </a>
    &nbsp;
    <a href="https://github.com/Pro-mwas1234/SpotiBat/commits/main">
      <img src="https://img.shields.io/github/last-commit/Pro-mwas1234/SpotiBat?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="last commit"/>
    </a>

  </p>
  <p>
    <a href="https://deepwiki.com/Pro-mwas1234/SpotiBat">
      <img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki" height="28" style="vertical-align: middle;"/>
    </a>
  </p>
  <p>
    a native Android music client — stream your Spotify library, keep the songs you love on the device, and play them with no connection.
  </p>
  <p>
    SpotiBat is a native Jetpack Compose app (no WebView). it keeps the familiar Spotify-style experience and adds true offline playback.
  </p>
</div>


---

## Download

<div align="center">
  <a href="https://github.com/Pro-mwas1234/SpotiBat/releases/latest">
    <img src="https://img.shields.io/github/v/release/Pro-mwas1234/SpotiBat?style=for-the-badge&logo=github&labelColor=0d0d0d&color=1DB954" alt="Download APK"/>
  </a>
</div>

download the `.apk` and install it on your device. you may need to toggle **"Install from unknown sources"** in your Settings.

---

## Features

- **native Android app**, built with Jetpack Compose and Material 3 — no WebView
- browse home with playlists, songs, and your favorite artists
- search for songs, artists, playlists, and podcasts
- view your library's playlists, albums, artists, and podcasts
- play playlist and album tracks with a mini player; expand to the full Now Playing screen with seek controls and lyrics
- **offline downloads**: download a song, album, or playlist and play it with no connection — audio is sourced from YouTube and kept as a plain playable file on the device
  - one-tap **Downloads** shortcut in the profile drawer
  - choose where songs are stored: phone storage or an SD card (moves existing downloads when you switch)
  - lock-screen media card with album art and full transport controls
- podcasts with chapters, playback speed, and episode video
- media notification: play/pause, skip, seek, like/unlike, shuffle, repeat
- Android Auto support
- update checker (auto & manual) against this repo's latest release
- AMOLED-friendly dark mode
- sleep timer
- multiple account profiles

---

## Requirements

- Android 7.0+ (API 24)
- a Spotify account (free or premium)

---

## Quick Start

```bash
git clone https://github.com/Pro-mwas1234/SpotiBat.git
cd SpotiBat
./gradlew installStandardDebug
```

or build without installing:

```bash
./gradlew assembleStandardDebug
```

APK lands at `app/build/outputs/apk/standard/debug/`.

other flavors: `installSpotifyDebug` and `installAppleDebug` (cloned island variants with different package names).

or open Android Studio, select the **app** configuration with the **standardDebug** build variant, and hit **Run** (`Shift + F10`).

---

## Upgrading from SpotiBat 1.x

this app uses `applicationId com.mwask.bat`, the same as SpotiBat 1.2.4, so it installs in place over an existing installation instead of sitting next to it. Android only accepts that upgrade when both facts hold:

- the APK is signed with the **same release keystore** the 1.x builds used (otherwise the install fails with "App not installed"), and
- `versionCode` is higher than the one in the installed build (1.2.4 shipped as `25`; this build is `26`).

the release workflow reads either secret naming, so the old `KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS` and `KEYSTORE_KEY_PASSWORD` secrets keep working, and it attaches the standard build as `SpotiBat-latest.apk`, which is the asset the 1.x in-app updater looks for.

---

## Releases

official builds published by the devs are on the [Releases page](https://github.com/Pro-mwas1234/SpotiBat/releases/latest) — grab the latest one for a tested, stable experience.

automatic builds are produced on every commit/push by the [Build SpotiBat](https://github.com/Pro-mwas1234/SpotiBat/actions/workflows/build.yml) workflow; APKs are attached to the run artifacts. these are bleeding-edge builds straight from the source — they may contain unfinished work, so use them at your own risk.

---

## Contributing

1. fork the repo
2. create your feature branch (`git checkout -b feature/awesome-feature`)
3. commit your changes (`git commit -m 'feat: add awesome feature'`)
4. push to the branch (`git push origin feature/awesome-feature`)
5. open a pull request

---

## About

SpotiBat is a community driven project built with passion by:

- **Main dev:** [Pro-mwas1234](https://github.com/Pro-mwas1234)

---

## License

this project is licensed under the GNU General Public License v3.0 (GPL-3.0) - see the [LICENSE](LICENSE) file for details.

## Disclaimer

this project is not affiliated with, endorsed by, or sponsored by Spotify AB, YouTube, or any other streaming service. all product names, trademarks, and registered trademarks mentioned or referenced are the property of their respective owners. SpotiBat is an independent, community-driven client intended for educational and personal use. users are responsible for complying with the terms of service of any service they connect to. no proprietary assets or credentials are distributed with this project.
