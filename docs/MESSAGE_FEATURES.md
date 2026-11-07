# Message features: implementation and validation

## Transcription

Message menu → **Transcribe** supports voice notes and video notes. Both Premium and non-Premium accounts first use TDLib `RecognizeSpeech`. Cached results are reused; pending results show partial text and progress. Closing the dialog or destroying its controller stops polling; server polling times out after two minutes.

For non-Premium accounts, quota/Premium/flood-limit errors offer on-device recognition. The first use asks to download multilingual Whisper base (147,951,465 bytes). Later uses run without downloading it again. Model bytes are checked against SHA-256 `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe`, including before reuse. Interrupted downloads use a temporary file and are removed. Audio is not uploaded to the model host.

`MediaExtractor`/`MediaCodec` select and decode the audio track (including the audio in a video note), downmix to mono and resample to 16 kHz. A pinned `whisper.cpp` v1.8.2 source archive builds a separate JNI library, explicitly included in the APK native targets for Android 5+. Only one inference runs at a time; cancellation reaches the native decoder. Audio is limited to ten minutes to bound memory. Device codec support and sufficient free RAM are required. Android 4.x retains server transcription only.

This does not transcribe ordinary video attachments or music/audio file attachments. Local results are displayed in the dialog, rather than injected into Telegram's server-owned recognition result.

## Edit history

Message menu → **Edit history** shows locally observed text/caption versions, newest first, with observation timestamps. The first incoming, successfully sent, or rendered eligible message establishes a baseline. Only subsequent TDLib content updates append revisions, so rendering an old search/reply snapshot cannot create a false edit. Updates outside the currently visible chat are captured when a baseline exists.

The private SQLite database is isolated by account/chat/message. Duplicate text is ignored; deletions remove message history, logout clears account history, and enabling content protection or automatic deletion clears chat history. Secret chats, protected messages, self-destructing messages and messages with automatic deletion are excluded when establishing baselines. Retention is 30 days, with a global cap of 10,000 observations; the viewer shows the newest 100.

Observation dates are explicitly labelled: Telegram does not supply past versions or the original timestamp of an edit that was never observed. Formatting-only edits and edits missed while the app was offline are not reconstructed. Loading an existing message establishes a baseline only when none is stored.

## AI editor

Attachment menu → **AI** offers proofreading via `FixTextWithAi` and styles from TDLib `UpdateTextCompositionStyles` via `ComposeTextWithAi`. Both functions exist in the pinned TDLib submodule. The preview displays the original and the returned text; **Apply** is disabled until a successful result. Dismissing the preview ignores late responses. Application preserves returned text entities and checks the chat, edit context and original formatted draft before replacing text. Stale results cannot overwrite a changed draft. Secret chats are excluded from AI processing; quota errors are shown without changing the draft.

Panels use the existing chat glass background with rounded corners and fade/scale entry; result text fades in. English and Russian resources follow the selected Telegram language. The attachment bar accounts for the extra item in both LTR and RTL layouts.

## Group audio

Group menu → **Voice chat** opens an existing group voice chat, or allows an administrator with `canManageVideoChats` to create one after confirmation. Voice-chat service messages open the same panel.

The implementation connects TDLib `JoinVideoChat` signaling to the existing `tgcalls::GroupInstanceCustomImpl`. A process-level session owns the native instance, requests microphone permission and audio focus, joins muted, tracks participant updates and server mute restrictions, and handles `needRejoin`. Audio continues when the panel is dismissed. A foreground microphone service exposes a leave action in its notification. Leaving, authorization loss, service destruction, or a competing ordinary call stops the engine and restores audio mode/routing. Connection attempts have a 45-second timeout.

**This group audio path has not been validated by a full Android native build or an actual multi-client call.** Standalone encrypted conferences (`InputGroupCallLink`), video, RTMP streams, participant moderation and Bluetooth-specific routing controls are outside this implementation. Speaker/earpiece switching uses Android's audio routing APIs.

## Verification performed

- Existing update tests: 8 passed; existing performance tests: 3 passed.
- New SQLite/resource tests: 8 passed. These execute production SQL against SQLite to check isolation, ordering under clock rollback, history/disk limits, Unicode, deletion and retention boundaries. English/Russian resource keys and format placeholders match.
- Java PCM test passed for seven source rates, duration, waveform accuracy, 16 kHz identity and invalid/oversized inputs.
- New Java components compiled against the pinned generated TDLib classes and an Android API jar, with application boundaries stubbed. This checks component syntax and TDLib/Android API usage, **not full application integration**.
- The actual Whisper JNI bridge was built on ARM64 and recognized the included JFK sample with the verified base model. Native cancellation passed. This checks native inference, **not Android `MediaCodec`, APK packaging, or the UI**.
- XML parsing and `git diff --check` passed.
- Full Gradle compilation was attempted. Java 21 and Gradle now run, but application configuration stops because `local.properties` is absent; SDK/NDK, remaining native dependencies and valid Telegram API credentials are also needed. No APK or device UI verification is claimed.

Re-run portable tests:

```sh
python3 -B -m unittest discover -s tests/messages -p 'test_*.py'
javac -d /tmp/frogram-message-tests app/src/main/java/org/thunderdog/challegram/util/PcmResampler.java tests/messages/PcmResamplerTest.java
java -cp /tmp/frogram-message-tests PcmResamplerTest
```

Optional native smoke test (JDK 21, CMake, a C++ compiler, curl; downloads approximately 156 MB):

```sh
JAVA_HOME=/path/to/jdk tests/messages/run-native-smoke.sh /path/to/test-cache
```

Before release: compile the full APK; check Russian/English and RTL layout on device; exercise Premium/non-Premium quotas and offline voice/video-note decoding; test history after restart/deletion/logout; test AI draft edits during requests; and make a call between two clients covering mute, backgrounding, interruption, reconnection and leave.
