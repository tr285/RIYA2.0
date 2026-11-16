# RIYA — Personal AI Assistant for Android

Native Android rewrite of `tr285/RIYA` built with Kotlin, Jetpack Compose, Material 3, Firebase Authentication (Google Sign-In via Jetpack Credential Manager), Cloud Firestore Enterprise, Firebase App Check, and Firebase AI Logic (`gemini-3.8-flash`).

## Key Capabilities

- **Account & Privacy Isolation**: Google Sign-In gate using Credential Manager (`GetSignInWithGoogleOption` + silent `GetGoogleIdOption`), scoping all conversations, memories, tasks, reminders, notes, permission rules, and audit logs strictly under `/users/{uid}/...` via hardened Firestore security rules.
- **Conversation & Voice Mode**: Streaming Gemini responses with markdown rendering, Copy & Regenerate actions, Android `SpeechRecognizer` (continuous Voice Mode & Push-to-Talk with runtime `RECORD_AUDIO` permission flow), `TextToSpeech` voice output & interrupt barge-in, and a live 24-bar Canvas audio waveform visualizer.
- **Persistent Personal Memories**: Categorized memory storage (`general`, `user_profile`, `preferences`, `personal_instructions`, `important_facts`, `work`) with search, category chips, Remember modal, Forget confirmation, and Auto-Memory Recall toggle.
- **Tasks, Productivity & Autonomous Planner**: Daily productivity overview, Personal Tasks, Reminders with recurrence, Personal Notes with pinning, 8 Built-in AI Skills, and a Gemini structured-JSON Autonomous Multi-Step Planner with permission gates and Execution History.
- **RIYA Studio (Images, Audio & Arcade)**: Text-to-image creation and Photo Picker photo-to-image editing powered by `gemini-nano-banana-2.1`, AAC voice memo recording (`MediaRecorder`) with multimodal Gemini transcription & quick Send-to-Chat / Save-Note actions, and **Pulse Core** — a 60fps Jetpack Compose `Canvas` orbital deflector 2D arcade game with wave progression and per-user high scores.
- **Gemini Model Tier Switcher**: Instant switching in Chat and Settings between `gemini-3.8-flash` (Balanced), `gemini-3.5-flash-lite` (Fast), and `gemini-4-argon` (Deep Reasoning, Blaze), persisted per-user in Firestore.
- **Trust, Permissions & Audit Log**: Capability policy editor (`allow`, `confirm`, `deny`) with `one_time`, `session`, `time_limited`, and explicitly acknowledged `persistent` lifetimes, paired with an append-only Security Audit Log.
- **System Status & Diagnostics**: Live Android hardware, RAM, storage, authentication, App Check, STT/TTS, AI latency telemetry, and Firestore workspace diagnostics.
