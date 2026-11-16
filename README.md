# Concert Lyrics

An Android app to learn the words before a gig. Type an artist and it:

1. works out which tour is coming to you (from your location or a tour name you type),
2. predicts the setlist in order from that tour's latest shows on [setlist.fm](https://www.setlist.fm),
3. downloads lyrics for every song from [LRCLIB](https://lrclib.net),
4. saves it all on the phone so it works with no signal at the venue.

If you don't share your location, it uses the artist's latest shows, or you can type your city.

## Setup

- Get a free setlist.fm API key at https://www.setlist.fm/settings/api and paste it in the app's Settings.
- Build: `gradlew assembleDebug` (Android Studio's JDK, Android SDK in `local.properties`).
- Tests: `gradlew testDebugUnitTest`.

Kotlin + Jetpack Compose, minSdk 26.
