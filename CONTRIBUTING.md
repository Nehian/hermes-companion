# Contributing

Thanks for helping. Keep changes small and tested.

- **Server** (`apps/backend`): FastAPI + SQLite. Run `./scripts/test-backend.sh` (needs Docker). Add a test for every behaviour change.
- **App** (`apps/frontend`): Kotlin + Jetpack Compose, plain SQLite, no annotation processors. Build with `./gradlew assembleDebug`. Library versions are pinned to what compiles against compileSdk 36; check `app/build.gradle.kts` and `gradle/libs.versions.toml` before bumping.
- Verify alarm changes on a device or emulator (ring, answer, reboot), not only by compiling.
- Never commit keys, `.env` files, databases or APKs. Contributions are accepted under the Apache License 2.0.
