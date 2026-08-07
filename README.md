<div align="center">
  <img src="art/bgwelcome.png" alt="Spotilol" style="width: 100%; max-width: 900px; margin-bottom: 20px; box-shadow: 0 8px 32px rgba(0,0,0,0.5);">
</div>

<h1 align="center">Spotilol</h1>

<p align="center">
  <a href="https://github.com/lyssadev/Spotilol/stargazers">
    <img src="https://img.shields.io/github/stars/lyssadev/Spotilol?style=for-the-badge&logo=starship&labelColor=0d0d0d&color=1DB954" alt="stars"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/releases">
    <img src="https://img.shields.io/github/downloads/lyssadev/Spotilol/total?style=for-the-badge&logo=download&labelColor=0d0d0d&color=1DB954" alt="downloads"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/releases/latest">
    <img src="https://img.shields.io/github/v/release/lyssadev/Spotilol?style=for-the-badge&logo=github&labelColor=0d0d0d&color=1DB954" alt="version"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/forks">
    <img src="https://img.shields.io/github/forks/lyssadev/Spotilol?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="forks"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/commits/main">
    <img src="https://img.shields.io/github/last-commit/lyssadev/Spotilol?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="last commit"/>
  </a>
  &nbsp;
  <a href="https://deepwiki.com/lyssadev/Spotilol">
    <img src="https://deepwiki.com/badge.svg" alt="DeepWiki" style="height: 28px;"/>
  </a>
</p>

<p align="center">
  a lil Android app that wraps Spotify's web player with built-in adblocking.
</p>

<p align="center">
  it's a fork of <strong>Spotifuck</strong> by <strong>deviato</strong>, ported from smali to clean Kotlin. all free, all open-source.
</p>

<p align="center">
  runs in two modes: <strong>normal</strong> (no certificate, works out of the box - the default) or <strong>proxy MITM</strong> (local proxy with a custom CA cert) so Spotify doesn't clock you're on a WebView. everything else passes through untouched.
</p>

<p align="center">
  📖 documented at <a href="https://deepwiki.com/lyssadev/Spotilol">deepwiki.com/lyssadev/Spotilol</a>
</p>

---

## Preview

<div align="center">
  <img src="art/spotilol_ss1.jpg" alt="screenshot 1" width="30%" style="max-width: 250px; margin: 4px; border-radius: 12px;" />
  <img src="art/spotilol_ss2.jpg" alt="screenshot 2" width="30%" style="max-width: 250px; margin: 4px; border-radius: 12px;" />
  <img src="art/spotilol_ss3.jpg" alt="screenshot 3" width="30%" style="max-width: 250px; margin: 4px; border-radius: 12px;" />
</div>

---

## Download

grab the latest APK from the [releases page](https://github.com/lyssadev/spotilol/releases/latest).

download the `.apk` file and install it on your device. you may need to toggle **"Install from unknown sources"** in your Settings.

---

## Features

- blocks audio ads 🚫
- works with or without a certificate: **normal mode** (default) & **proxy MITM**
- media notification with play/pause, skip, seek, like/unlike
- works with lock screen, Bluetooth, Wear OS
- autoplay modes: off, once at start, or permanent
- mobile-friendly CSS/JS layout tweaks
- AMOLED dark mode (pure black)
- keeps screen on while you're vibing
- browse your library through Spotify's API
- update checker (auto & manual)

---

## Requirements

- Android 8.0+ (API 26)
- a Spotify account (free or premium)
- Google Chrome / WebView (comes with your phone)

---

## Quick Start

install the APK, open it, done. Spotilol runs in **normal mode** by default - no certificate, no setup, no "Certificate Required" screen. it just works out of the box.

### Switching to Proxy MITM Mode

want the full fingerprint treatment? flip the mode in **Settings → Connection Mode → "MITM Proxy (Certificate)"**. the app restarts and walks you through the cert install.

### The Certificate Thing (proxy mode only)

Spotilol generates a local CA cert so Spotify doesn't know you're in a WebView. it lives on your device, stays on your device.

1. open Spotilol in proxy mode — you'll see the **"Certificate Required"** screen
2. tap **"Export .pem"** to save it to your Downloads
3. go to **Settings > Security > Encryption & Credentials > Install a certificate > CA certificate**
4. find `spotilol_ca.pem` in your Downloads and tap it
5. it'll warn you about network monitoring — tap **"Install anyway"**
6. come back to Spotilol and tap **"Check"**. if it worked, you're in.

> **Note:** if you ever clear your device's credential storage (like after a factory reset), you'll have to do this again.

---

## Build It Yourself

```bash
git clone https://github.com/lyssadev/Spotilol
cd Spotilol
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Google Services

this project uses Firebase (analytics, crash reporting, performance). to build, you need:

1. create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. register an Android app with package name `com.project.lol`
3. download the `google-services.json` and place it in `app/`

---

## Contributing

contributions are welcome. open issues, throw PRs, suggest stuff — free for all.

---

## Credits

Spotilol exists because deviato did the reverse-engineering work on Spotifuck. this ports the core logic from smali to Kotlin with extra features and maintenance.

**Open-sourced by lyssadev <3 deviato**