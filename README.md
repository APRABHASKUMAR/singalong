# Singalong 🎤

**Know every word at the show.**

Going to a concert? Type the artist's name and Singalong works out the songs they're likely to play, in order, and saves the lyrics on your phone. At the venue it works with no signal, so you can sing along to every song.

- Predicts the setlist from the artist's recent shows on [setlist.fm](https://www.setlist.fm)
- Downloads the lyrics for every song from [LRCLIB](https://lrclib.net), a free open lyrics library
- Saves everything on your phone for offline use
- Free, with no ads and no account with us

> Android only (Android 8 or newer).

---

## 📲 Install (2 minutes)

1. **On your Android phone**, tap this link to download the app:
   **[⬇️ Download Singalong.apk](https://github.com/APRABHASKUMAR/singalong/releases/latest/download/Singalong.apk)**
2. Open the downloaded file, from your notifications or the **Downloads** folder.
3. If your phone says *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings**, turn on **Allow from this source**, then go back and tap **Install**.
4. If Google Play Protect warns that the app is from an *unknown developer*, tap **More details**, then **Install anyway**. This shows up because the app isn't on the Play Store.

Singalong now appears on your home screen with a blue speech-bubble icon.

---

## 🔑 One-time setup: get a free setlist.fm key

Singalong reads setlists from setlist.fm, which asks every user for a free key of their own. It takes about a minute:

1. Open Singalong and tap the **One-time setup** box, or the ⚙️ gear in the top-right corner.
2. Tap **Get a free key on setlist.fm ›**.
3. Log in to setlist.fm, or create a free account.
4. Fill in the short form. Anything simple works, for example:
   - Application name: `Singalong`
   - Description: `Personal app to learn lyrics before concerts`
5. Copy the **API key** it gives you, paste it into Singalong and tap **Save key**.

You only need to do this once.

---

## 🎶 How to use it

1. Type the **artist's name**, for example `Coldplay`. You don't need exact spelling, capitals or punctuation (`guns n roses` works).
2. *(Optional)* Type the **tour name** if you know it, for example `Music of the Spheres`. Part of the name is enough.
3. Tap **Find setlist & lyrics**.
4. Wait about a minute while the lyrics download. Keep the app open.
5. Tap the concert under **Saved concerts** to open it. Tap any song to see its lyrics.

**At the concert:** open the saved concert. It works in airplane mode. The screen stays on while it's open, and **A− / A+** change the text size.

### Location: completely optional

On first launch, Singalong asks whether it can use your location. This only helps it pick the tour that's coming to your city. **It's fine to say no.** Without location, it uses the artist's latest shows, and you can tap **Type city ›** to enter your city by hand.

### If a song has no lyrics

Tap **Retry** to try the missing songs again. If a song still has none, tap **Search web ›**, copy the lyrics from any site, then tap **Add lyrics ›** and paste them in.

---

## ❓ FAQ

**How accurate is the setlist?**
It's a prediction based on the latest shows of the same tour. Artists on tour usually play nearly the same set every night. Songs marked with fewer shows, like `3/6 shows`, are less certain. Songs played only now and then are listed separately under **Sometimes played**.

**Does it cost anything?** No. The app, setlist.fm keys and LRCLIB are all free.

**What data leaves my phone?**
- To setlist.fm: the artist, the tour and your country code.
- To LRCLIB: the song titles.
- Nothing else. There are no analytics or ads, and your saved concerts stay on your phone.

**How do I delete a concert?** Tap the 🗑️ next to it under **Saved concerts**.

**How do I update the app?** Download the latest `Singalong.apk` from the link above and install it over the old one. Your saved concerts are kept.

---

## 🛠️ For developers

Kotlin + Jetpack Compose, minSdk 26.

- Build a debug APK: `gradlew assembleDebug`. You need Android Studio's JDK, and the Android SDK set in `local.properties`.
- Run the tests: `gradlew testDebugUnitTest`.
- Release builds are signed only when a `signing/keystore.properties` file exists. That folder is never committed.

Setlist data © [setlist.fm](https://www.setlist.fm). Lyrics from [LRCLIB](https://lrclib.net).
