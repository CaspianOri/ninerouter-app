# 9Router

An Android app to manage 9Router LLM gateway providers and chat with models —
API keys, provider list, model picker, and a chat client, all from your phone.

## Tech stack

- Kotlin, Jetpack Compose (Material3)
- DataStore Preferences for settings persistence
- kotlinx.serialization for JSON models
- OkHttp for HTTP transport
- JUnit4 + Robolectric + kotlinx-coroutines-test for unit tests

## Building

Requirements: JDK 17, Android SDK (platform 34, build-tools 34.0.0).

```bash
./gradlew assembleDebug        # APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

GitHub Actions (`.github/workflows/android.yml`) is the authoritative build:
every push to `main` builds the debug APK, runs unit tests, and uploads the
APK as the `ninerouter-app-debug` artifact.

## Project structure

```
app/src/main/java/id/ninerouter/app/
└── MainActivity.kt            # entry point (placeholder screen; chat UI comes next)
```

## License

MIT — see [LICENSE](LICENSE).
