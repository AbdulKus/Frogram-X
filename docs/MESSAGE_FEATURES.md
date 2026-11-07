# Message features: implementation and validation

## Transcription

Voice notes have a transcription button before the play control. It shows progress and expands the note to display the text. Tap it again to collapse the text. Video notes use the **Transcribe** item in the message menu. Both Premium and non-Premium accounts first use TDLib `RecognizeSpeech` or reuse a cached result. Server recognition has a two-minute deadline.

When Telegram recognition is unavailable, the app uses multilingual Whisper Small Q5.1 (190,085,487 bytes) with beam search. A compact prompt offers the download when the model is missing. Model bytes are checked against SHA-256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`, including before reuse. Interrupted downloads use a temporary file. Local inference uses the downloaded audio on the device.

`MediaExtractor`/`MediaCodec` select and decode the audio track (including the audio in a video note), downmix to mono and resample to 16 kHz. A pinned `whisper.cpp` v1.8.2 source archive builds a separate JNI library, explicitly included in the APK native targets for Android 5+. Only one inference runs at a time; cancellation reaches the native decoder. Audio is limited to ten minutes to bound memory. Device codec support and sufficient free RAM are required. Android 4.x retains server transcription only.

Transcription is available for voice and video notes. Local results are kept in memory for the active chat controller.

## Edit history

Message menu → **Edit history** appears before **Delete** when at least two local versions are available. Each version has its own card. The first incoming, successfully sent, or rendered eligible message establishes a baseline. Only subsequent TDLib content updates append revisions, so rendering an old search/reply snapshot cannot create a false edit. Updates outside the currently visible chat are captured when a baseline exists.

The private SQLite database is isolated by account/chat/message. Duplicate text is ignored; deletions remove message history, logout clears account history, and enabling content protection or automatic deletion clears chat history. Secret chats, protected messages, self-destructing messages and messages with automatic deletion are excluded when establishing baselines. Retention is 30 days, with a global cap of 10,000 observations; the viewer shows the newest 100.

The dates on the cards are local observation times. Formatting-only edits and edits missed while the app was offline are not reconstructed. Loading an existing message establishes a baseline only when none is stored.

## AI editor

Select part of the draft and open the formatting keyboard to use **AI**. It offers proofreading via `FixTextWithAi` and styles from TDLib `UpdateTextCompositionStyles` via `ComposeTextWithAi`. The preview compares the selection and the returned text. **Apply** replaces the original selection and preserves the returned entities. The app checks the chat, edit context and original draft before applying the result. Secret chats are excluded from AI processing.

Panels use the chat glass drawable on a single rounded surface. English and Russian resources follow the selected Telegram language.

## Group audio

Group menu → **Voice chat** opens an existing group voice chat, or allows an administrator with `canManageVideoChats` to create one after confirmation. Voice-chat service messages open the same panel.

The implementation connects TDLib `JoinVideoChat` signaling to `tgcalls::GroupInstanceCustomImpl`. The group audio adapter reports that Android hardware mute is unavailable and delegates routing to the Android audio layer. A process-level session requests microphone permission and audio focus, joins muted, tracks participants and server mute restrictions, and handles `needRejoin`. Audio continues when the panel is dismissed. A foreground microphone service exposes a leave action. Leaving, authorization loss, service destruction, or a competing ordinary call stops the engine and restores audio routing. Connection attempts have a 45-second timeout.

The group engine passed startup, RTC offer generation, mute/unmute and shutdown on a physical Android device. End-to-end Telegram signaling and audio between two clients still require a live account test. Speaker and earpiece switching uses Android's audio routing APIs.

## Verification performed

- The update, performance, SQLite and localization suites passed 20 tests. The PCM test covered seven input rates, waveform accuracy, 16 kHz identity and invalid inputs.
- The full ARM64 debug APK built and was installed under a separate test package. The Android test decoded a Russian Opus file with `MediaCodec` and recognized it with Whisper Small Q5.1.
- The host JNI smoke test recognized the included JFK sample and verified cancellation. On 12 held-out Russian clips from FLEURS, the previous configuration made 51 word errors across 219 reference words. The new configuration made 22.

Re-run portable tests:

```sh
python3 -B -m unittest discover -s tests/messages -p 'test_*.py'
javac -d /tmp/frogram-message-tests app/src/main/java/org/thunderdog/challegram/util/PcmResampler.java tests/messages/PcmResamplerTest.java
java -cp /tmp/frogram-message-tests PcmResamplerTest
```

Optional native smoke test (JDK, CMake, a C++ compiler, curl, approximately 200 MB of downloads).

```sh
JAVA_HOME=/path/to/jdk tests/messages/run-native-smoke.sh /path/to/test-cache
```

Remaining device checks include the chat layouts, Telegram recognition with a real account, AI preview and apply, and an audio call between two clients.
