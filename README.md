# Profit-Loss Trading App

Android app built with Kotlin and Jetpack Compose.

## Features
- Colorful dashboard and app logo/splash screen
- Mobile number + SMS OTP sign-in using Firebase Authentication
- Per-account trade storage and Firestore cloud restore/backup
- Manual profit, loss, and editable per-trade brokerage (default ₹87)
- Automatic daily trade numbering and net calculations
- Daily, weekly, and all-time reports
- Local on-device persistence; edit and delete trades

## Firebase setup required before using login

1. Open the existing Firebase project **BILLING STOCK MANAGEMENT** (or a dedicated Firebase project).
2. Add an Android app with package name `com.amit.profitloss`.
3. Download its `google-services.json` and place it at `app/google-services.json` in this repository.
4. In Firebase Console > Authentication > Sign-in method, enable **Phone**.
5. Add your app's SHA-1 and SHA-256 signing certificate fingerprints in the Firebase Android app settings. The debug fingerprints can be found by running `gradle signingReport` in Android Studio/Gradle.
6. In Firebase Console > Firestore Database, create a database and publish the rules from `firestore.rules`.
7. Phone authentication SMS requires the Firebase Blaze (pay-as-you-go) plan; SMS charges may apply. Set billing alerts and usage limits where available.

Firestore stores each user's trade data under `users/{FirebaseAuth UID}`; the rules allow that signed-in user to access only their own document. Do not publish permissive Firestore rules.

Without `app/google-services.json`, the app intentionally shows a Firebase setup message rather than allowing login.

## Build
Open this repository as a Gradle project in Android Studio, sync Gradle, then build the `app` configuration.
