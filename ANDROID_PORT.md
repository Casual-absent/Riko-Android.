# Riko Android port

## Current checkpoint: 0.3.0 — provider presets (device build verification pending)

The 0.1.0 Android foundation and four persistent appearance themes were installed and tested on the target Redmi phone. The 0.2.0 chat client was subsequently built and used successfully with OpenRouter; the user confirmed a live response using `inclusionai/ling-3.2-flash:free`. This 0.3.0 revision adds explicit OpenAI, OpenRouter, and Custom OpenAI-compatible provider presets. The OpenRouter preset fills the endpoint and the tested model, while both fields remain editable. The 0.3.0 source is committed to `android-port` and still needs a fresh CodeOnGo build and on-device selector test. Voice input/output and the VRM avatar are not connected yet.

The original Python desktop implementation remains in the repository and is not modified by the Android application.

## Why this is a separate Android app

The upstream loop is:

1. `riko/asr.py` records audio through `sounddevice` and transcribes it with Faster-Whisper or Groq.
2. `riko/llm.py` sends conversation history to OpenAI's Responses API and writes `chat_history.json`.
3. `riko/tts.py` calls Fish Audio or a separately running GPT-SoVITS HTTP server.
4. `riko/viewer.py` serves the avatar and audio from a Python localhost HTTP server.
5. `client/index.html` uses Three.js and `@pixiv/three-vrm` to render the VRM and animate its mouth from audio loudness.

Those desktop-specific Python dependencies and the Python-hosted browser loop are not an Android runtime. The Android port will replace each boundary with Android APIs or mobile-compatible network services while retaining the original feature flow and character configuration concepts.

## Build environment

The project is pinned to this conservative toolchain for the first Android build:

| Component | Version |
|---|---:|
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| JDK to run Gradle | 17 or newer supported by Gradle 8.13 |
| Android compile SDK | 36 |
| Android target SDK | 36 |
| Android minimum SDK | 26 (Android 8.0) |
| App language for this foundation | Java 17 |

Android Gradle Plugin 8.13 requires Gradle 8.13 and JDK 17, and supports Android API levels through 36.1. This project targets API 36. It deliberately avoids Kotlin, Compose and third-party Android libraries in this first checkpoint, reducing moving parts while the build is established in CodeOnGo.

## Build in CodeOnGo

1. Clone this repository and select the `android-port` branch.
2. Open/import the **repository root** — the folder containing `settings.gradle.kts`.
3. Make sure CodeOnGo is using JDK 17 or 21 and Gradle 8.13.
4. Install Android SDK Platform 36 and Android SDK Build-Tools 36.0.0 if they are not already installed.
5. On the AndroidIDE/CodeOnGo environment used for this port, the Maven-distributed Linux AAPT2 executable failed to launch (`Syntax error` / `Exec format error`). The working device SDK binary was `\u0024HOME/android-sdk/build-tools/36.0.0/aapt2`. Keep this local-only line in `gradle.properties` for that environment (do not hard-code another device's path into the repository):

   ```properties
   android.aapt2FromMavenOverride=/data/data/com.itsaky.androidide/files/home/android-sdk/build-tools/36.0.0/aapt2
   ```
6. Sync the project, then run the Gradle task `:app:assembleDebug`.

From the repository root, the equivalent terminal command is:

```bash
gradle --no-daemon :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This repository does not yet include Gradle Wrapper scripts. Use CodeOnGo's installed Gradle 8.13 for this first build. A wrapper can be generated and committed from the project environment once that build is confirmed to work there.

A GitHub Actions workflow also assembles the debug APK on pushes and pull requests targeting `android-port`, providing an independent cloud build check.

## Current Android behavior

- Opens as a native Android Activity and preserves the Light, Dark, Red, and Green appearance themes across launches.
- The `API` header control offers OpenAI, OpenRouter, and Custom OpenAI-compatible presets. The OpenRouter preset uses `https://openrouter.ai/api/v1/chat/completions` and the tested starter model `inclusionai/ling-3.2-flash:free`; both endpoint and model remain editable. The API key and system prompt are separately configurable.
- Defaults are `https://api.openai.com/v1/chat/completions` and `gpt-4.1-mini`. The default personality prompt is carried over from the original `character_config.yaml`.
- Sends requests on a background executor and renders Chat Completions server-sent-event text deltas into the reply bubble.
- API keys are encrypted using AES/GCM with a non-exportable AES key in Android Keystore. Only ciphertext and the IV are stored in app-private preferences. The key is never committed to Git or written to app logs.
- Conversation turns are stored in a private SQLite database only after a successful model reply. The latest 24 messages (up to 12 complete turns) are used as context and restored in the chat UI. Failed requests remove the temporary bubbles and put the unsent text back in the composer.
- Requests require HTTPS and the normal Android `INTERNET` permission. Cleartext traffic remains disabled.
- Streaming replies are capped at 32,000 characters; connection and read timeouts are 20 and 120 seconds respectively.
- The Android client currently targets the OpenAI-compatible **Chat Completions** SSE format. It does not implement the desktop Responses API, tool/function calling, image inputs, audio, or provider-specific non-Bearer authentication.
- Voice input/output and the 3D VRM avatar remain unimplemented.

## Implementation order

1. **Foundation (verified on device):** Gradle project, Android Activity, app launch and CodeOnGo build.
2. **Chat and model (verified by user on device):** configurable OpenAI-compatible Chat Completions URL and model, API key encryption through Android Keystore, bounded SQLite conversation history, streaming response handling, and visible connection/error states. A live OpenRouter request using the Ling 3.2 Flash free model succeeded.
3. **Provider presets (0.3.0 implementation committed; fresh device build pending):** OpenAI, OpenRouter, and Custom OpenAI-compatible presets; provider selection fills endpoint and starter model, while retaining editable fields and secure key storage.
4. **Voice input:** Android `AudioRecord` or platform recording APIs with push-to-talk first; cloud speech recognition before considering on-device Whisper.
5. **Voice output:** mobile-compatible TTS endpoint with streaming/chunked playback and audio-focus handling.
6. **Avatar:** package a tested Three.js / three-vrm web bundle locally, load it in a WebView, and bridge only the minimum required playback/expression events.
7. **Device tuning:** cap avatar rendering at 30 FPS, lower render pixel ratio and texture sizes, pause rendering when the Activity is hidden, and measure memory, temperature and battery use on the target handset.

Each stage should remain buildable before the next one is added. API credentials must be entered by the user at runtime and must never be committed to Git.
