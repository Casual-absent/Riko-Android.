# Riko Android port

## Current checkpoint: 0.1.0 — native foundation

The Android application now has its own Gradle project in the repository root and a native Android entry screen in `app/`. The current APK target is an interface foundation only. It does **not** yet call an AI model, record audio, synthesize speech, persist chat history, or render a VRM avatar. The message composer is explicitly marked as preview-only so it cannot be mistaken for a working AI conversation.

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
5. Sync the project, then run the Gradle task `:app:assembleDebug`.

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

- Opens as a native Android Activity.
- Shows a dark chat-style shell and build status.
- Accepts text and displays it locally on screen.
- Clearly warns that messages are not sent to an AI service.
- Requests no microphone, storage, or network permission.
- Does not embed API keys or copy the desktop Python runtime into the APK.

## Implementation order

1. **Foundation (current):** Gradle project, Android Activity, app launch and CodeOnGo build.
2. **Chat and model:** settings for a user-supplied model endpoint, protected credential storage using Android Keystore, bounded conversation context, streaming response handling and local chat history.
3. **Voice input:** Android `AudioRecord` or platform recording APIs with push-to-talk first; cloud speech recognition before considering on-device Whisper.
4. **Voice output:** mobile-compatible TTS endpoint with streaming/chunked playback and audio-focus handling.
5. **Avatar:** package a tested Three.js / three-vrm web bundle locally, load it in a WebView, and bridge only the minimum required playback/expression events.
6. **Device tuning:** cap avatar rendering at 30 FPS, lower render pixel ratio and texture sizes, pause rendering when the Activity is hidden, and measure memory, temperature and battery use on the target handset.

Each stage should remain buildable before the next one is added. API credentials must be entered by the user at runtime and must never be committed to Git.
