# Chat navigation performance

The maintained Telegram X base is `0583baf9104ed9b4423f671c58d38dc99f153b7a`
(2026-07-23). The upstream review on 2026-10-15 compared it with official
`275879f6` (2026-10-14). This change imports the relevant independent pieces:

- [bf670928](https://github.com/TGX-Android/Telegram-X/commit/bf6709283238eba9ae4cd812b015a516309c05f2):
  retain the static pinned/preview list so unchanged comment previews reuse the
  existing adapter contents, and update the retained preview's forced title.
  Clearing a static list also bypasses the dynamic-list identity fast path;
  changing the quote or locally picked media invalidates the retained preview.
- [d132af88](https://github.com/TGX-Android/Telegram-X/commit/d132af883b8408760f1ff716021fa20ff86f392a):
  the latest upstream baseline profile. `scripts/update-baseline-profile.py`
  imports it into AGP's standard `app/src/main/baseline-prof.txt` location and adds
  eleven explicit Frogram navigation methods, including the traced creation
  and opening implementations. R8 rewrites the rules for the release
  DEX; unmatched upstream methods are omitted. ProfileInstaller is enabled for
  the Marshmallow/latest flavors so APK installations also receive the profile.

To refresh the profile, fetch an official revision into the local repository and
run `python -B scripts/update-baseline-profile.py --revision <commit>`. No account
snapshot is imported. Release CI checks the compiled profile and metadata inside
the final APK with `scripts/verify-baseline-profile.py`.

Profile installation and compilation are separate steps. Normal APK installs
stage the profile after launch; Android schedules compilation during background
optimization. Merely shipping a profile is not evidence of a measured reduction
in navigation latency. See the official [manual measurement guide](https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure).

## Measuring the delay before the slide

Release builds enable `profileable android:shell="true"` on Android 10+ so an
authorized ADB shell can collect app sections without a debuggable build.
System traces with `atrace_apps: "org.frogram.messenger"` now include these
non-identifying sections:

- `Frogram.chat.click`: opening from a chat-list row.
- `Frogram.chat.prepare`: binding arguments and obtaining the messages view.
- `Frogram.chat.createViews`: first creation of the messages view hierarchy.
- `Frogram.navigation.start`: preparing and starting the page animator.
- `Frogram.glass.foreground` and `Frogram.glass.backdrop`: foreground snapshot
  and backdrop preparation.

Correlate them with input dispatch, the first animation frame, UI-thread scheduling
and frame timelines. The gap between view preparation and navigation start can
be the existing asynchronous history/draft-reply readiness gate. Keep this
separate from time spent executing on the UI thread. Sections record static
names only and perform no logging or capture outside an active system trace.
The slide duration and readiness timeout are unchanged by these markers.

## History readiness

Physical-device traces across different chats showed 150–520 ms between the
click and navigation start, while click handling itself took 3–14 ms. Keep this
separate from the frame cost of the slide and from the small cached test chat.

The loader's existing ready signal is now retained for the current initial load:
a signal received before the navigation runnable is registered must not be lost.
New initial/forced anchor loads reset it. Pending draft replies still block the
transition until their existing completion callback or timeout.

A partial local page can also release an existing wait when it fills the full
recycler height at the history-end anchor. Loading continues normally. Short
pages, unknown viewport geometry, unread/highlight targets and restored scroll
positions retain the original completion policy. This does not shorten the
500 ms fallback or alter the slide duration.

Additional trace points distinguish `Frogram.chat.initialMessages`,
`Frogram.chat.localViewportReady`, `Frogram.chat.historyReady`,
`Frogram.chat.awaitHistory`, `Frogram.chat.awaitDraft` and
`Frogram.chat.readyForNavigation`. They contain no chat identifiers.

The physical-device sample used to validate build 150 did not hit either the
early-signal race or the filled-local-page shortcut. Initial messages generally
became available at navigation start; some loads reached the existing timeout.
These conditional correctness fixes do not explain away the remaining history
preparation delay. Waiting for ready history remains the selected behavior.

## Software text replay in glass samples

An ART sampling trace on build 150 attributed about 493 ms of 676 ms sampled
software `drawTextRun` time to chat-list rows replayed by glass capture. These
are sampling wall durations, not exact CPU measurements or frame-rate figures.

`Text.draw` already clips titles and other opted-in text to their text area.
When that intersection is empty on a software canvas, it now stops before
background, spoiler and glyph rendering. It still updates quote and touch
coordinates and clears coordinate-dependent caches before returning, and restores
the saved canvas. Visible text, hardware recording and text without this clip
retain their existing rendering path. No glyph bounds are approximated.

The JVM regression executes the production clipping prelude. A native Android
comparison against build 150 also passed 480 pixel-equality cases across LTR/RTL,
five densities, transforms, partial/empty clips and multilingual italic text;
hardware behavior and coordinate bookkeeping were checked separately. In the
emulator's isolated fully-clipped-text benchmark, the median for 1,000 draws
was 80.75 ms before and 0.49 ms after. This isolates avoidable work, not the
overall chat transition; device frame measurements are still needed for that.
