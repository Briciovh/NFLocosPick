# Release Checklist & AGP 9 Compatibility Notes — NFLocosPick

Mirrors CLAUDE.md's "Release Checklist" and "AGP 9 / Dependency Compatibility Notes" sections. Source of truth is CLAUDE.md; update both together.

## Release Checklist

- **Play App Signing re-signs the app with its own certificate — that certificate's SHA-1/SHA-256 must be registered in Firebase.** The upload/local keystore (`keystore.properties`) is only used to sign the AAB you upload; Google Play then re-signs it for distribution with a separate management key. Google Sign-In validates the installed app's certificate against the fingerprints registered on the Firebase Android OAuth client — if only the upload key's SHA-1 is registered, Sign-In breaks on every production install even though it works fine locally.
  - Before (or right after) the **first** production publish, get the "App signing key certificate" SHA-1 and SHA-256 from Play Console → app → Protegido con Play → Firma de apps → Descargar certificados, and register both with `firebase apps:android:sha:create <appId> <hash> --project nflocospicks`. Verify with `firebase apps:android:sha:list <appId> --project nflocospicks`.
  - After adding a fingerprint, refresh the local `app/google-services.json` via `firebase apps:sdkconfig ANDROID <appId> --project nflocospicks -o app/google-services.json.new && mv -f app/google-services.json.new app/google-services.json` so local/CI builds stay in sync (the file is gitignored, so this only affects your machine).
  - This is a one-time step per app — Play App Signing's certificate doesn't change between releases, so once it's registered it stays fixed.

## AGP 9 / Dependency Compatibility Notes

- **Hilt requires ≥ 2.59** with AGP 9.x (versions ≤ 2.58 use the removed `BaseExtension` API). Hilt 2.59 also requires Gradle ≥ 9.1.
- **KSP on Kotlin 2.2.x + AGP 9** needs `android.disallowKotlinSourceSets=false` in `gradle.properties` because KSP adds sources via the old `kotlin.sourceSets` DSL. This flag can be removed when the project upgrades to Kotlin 2.3.x (where KSP ≥ 2.3.6 handles it natively).
- **Firebase `-ktx` artifacts were merged** into their base counterparts as of BOM 33+. Use `firebase-auth` and `firebase-firestore` (without the `-ktx` suffix).
