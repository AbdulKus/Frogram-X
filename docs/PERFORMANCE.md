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


## Hardware emulator audit, 2026-11-15

The test host is an Intel i3-9100 with UHD Graphics 630 and 8 GB RAM. The
Android 11 / API 30 x86_64 AVD runs at 1080 × 2340, density 440, 60 Hz, with
KVM and `-gpu host`. A headless Intel Xorg display supplies real OpenGL; both
`glxinfo` and SurfaceFlinger identify UHD Graphics 630. The unrelated second
emulator is stopped. Builds and profiling are run separately to avoid host
memory pressure. These are emulator measurements, not a physical-device FPS
certification.

The baseline is Frogram build 171, commit
`56d4110a4e30c68edaf8e759c75f8b5629f8902d`. Source debug comparisons use the
same x64 native libraries, graphics backend and account. For the final release
comparison, the published build 171 R8 Java/resources are repackaged with the
exact same x64 native libraries as the candidate (all 12 library byte streams
verified equal). Only its manifest version counter and local test signature
are adjusted to permit account-preserving updates. Its Java ABI flavor label
is still arm64; rendering does not branch on that label. The measured navigation candidate is
0.29.2.1970-x64, built with R8, resource shrinking, vital lint and a verified
compiled ART baseline profile. Local test APKs use a local debug certificate;
the normal release signing key is not changed.

### Changes and correctness checks

- Navigation foregrounds record their content once into a `Picture`. On API
  29+ hardware canvases, header layers use a composited `RenderNode`. Composer
  controls use bitmap textures to avoid the cost of many small GPU layers.
  Only the small header blur halo is rasterized. Older APIs and software
  canvases retain a bounded bitmap fallback. Blur buffers are reused when dimensions
  match. This removes software header rasterization and header texture uploads
  from transition preparation.
- Transition captures respect ancestor visibility. A composer inside a hidden
  parent no longer contributes detached-looking controls to channel transitions.
- Hidden glass surfaces retain their dirty flag without invalidating their host
  or walking changed-row coordinates. They refresh when visible again. Pre-draw
  invalidation for changed visible samples is retained so Android re-records
  their cached display lists.
- Custom emoji overlays use TextView's total padding, including compound
  padding and vertical gravity. This aligns bot keyboard icons with the labels.
  Draw bounds are restored even if drawing fails.
- Bot reply keyboards use one rounded glass surface with side/bottom clearance
  and rounded buttons. Cell layout uses the measured wrapper width and local
  coordinates, including when markup replaces existing rows.
- Custom emoji receivers attach only while their view and window are visible.
  Keyboard frame listeners follow window attachment and are removed on
  destruction. Retained keyboard views can close and reopen normally.

An Android instrumentation fixture injects local test markup and a local custom
emoji image; it never presses bot command buttons or sends messages. The old
receiver state was `visible=true, hidden=true, reopened=true`. The final build
passes `visible=true, hidden=false, reopened=true`. The light/dark keyboard
appearance and icon placement were inspected on the hardware renderer.

A separate fixture renders navigation snapshots at opacity 1, 0.7, 0.4 and 0.1
on hardware and software canvases. Comparing the same content region with the
old implementation gave mean RGB differences of 0.058/255 (hardware) and
0.022/255 (software); no missing text or opaque rectangular alpha artifacts
were observed. This covers the tested shapes/text and opacity levels, not every
possible Android drawing primitive.

The eight `tests/performance` regressions execute extracted production methods in
JVM fixtures, including hardware API gates, software replay and bounded
legacy allocation, centered/compound padding, exceptional draw cleanup,
hidden backdrop invalidation, receiver attach/hide/reopen and observer teardown.
All pass. The four standalone `tests/graphics` programs also pass, covering
blur/sampling, navigation geometry and chat viewport behavior.

### Measurement protocol

Before each scenario, reset the main list to the top. Locate the selected
chat by matching its avatar before every open, aborting if it cannot be found.
Save a warm-up screenshot to verify the target; fixed row coordinates are not
safe because incoming messages reorder the list. After warming the target
chat, collect three rounds of five open/back cycles.
For scrolling, collect three rounds of three up/down swipe pairs (450 ms per
swipe, with settling between gestures). Capture `gfx`, `view`, `sched` and
`freq` using `atrace -a org.frogram.messenger`; reset `dumpsys gfxinfo` before
each round and save `framestats` afterwards. Keep chat contents, scroll position,
resolution, animation scale and renderer consistent. Stop Gradle before runs.

Trace section durations include scheduling delays. Report per-run percentiles
and their median, separately from frame deadline misses and bitmap uploads.
For navigation UI-thread frame timings, include frames intersecting the window
from 20 ms before to 450 ms after each navigation-start marker; idle frames
otherwise distort the distribution.
Android 11 emulator `gfxinfo` can report high input-latency jank even when frame
deadline misses are much rarer; its overall janky-frame percentage must not be
presented as physical-device FPS.


### Final release results

The following are the avatar-verified runs. Earlier runs that used stale row
coordinates after an incoming message reordered the list are excluded. All
percentiles below are the median of three per-run percentiles, in milliseconds.
These are observed timings from this emulator sample, without statistical
confidence intervals.

| Transition metric | Build 171 baseline | Final 1970 |
| --- | ---: | ---: |
| Group foreground capture, p95 | 5.93 | 3.10 |
| Channel foreground capture, p95 | 3.37 | 2.85 |
| Group active UI frame, p95 | 28.33 | 26.57 |
| Channel active UI frame, p95 | 23.02 | 21.58 |
| Group slow bitmap uploads, total over 15 open/back cycles | 21 | 15 |
| Channel slow bitmap uploads, total over 15 open/back cycles | 17 | 5 |

The reduction in capture work is clearer than the overall frame improvement.
Steady scrolling does not use the foreground snapshot path. The scrolling
results do **not** establish a consistent overall frame-rate gain:

| Scenario, three rounds | Baseline deadline misses / rendered frames | Final deadline misses / rendered frames | Baseline UI frame p95 | Final UI frame p95 |
| --- | ---: | ---: | ---: | ---: |
| Group open/back | 105 / 1001 | 102 / 996 | 28.33 | 26.57 |
| Channel open/back | 62 / 1021 | 57 / 1015 | 23.02 | 21.58 |
| Chat-list scrolling | 16 / 1658 | 18 / 1609 | 13.40 | 16.19 |
| Channel-history scrolling | 32 / 1331 | 44 / 1315 | 15.54 | 16.61 |

Transition UI frame timings use the active navigation windows described above;
scrolling timings cover the complete gesture/settling sequence. Counts come
from `gfxinfo` and include the complete scenario. Content, caches and emulator
scheduling introduce variation. History viewport positions can also evolve as
browsing continues. Do not interpret the small scrolling differences as a
proven causal improvement or regression. Neither build rendered any frames in
the five-second settled chat-list idle check.

History readiness and navigation duration remain unchanged. The fixes remove
avoidable rendering and lifecycle work; they do not prove perfectly smooth
animation or parity with the original client. Physical-device frame-timeline
measurements remain necessary for that claim.


### Official Telegram X reference

The reference APK is official
[Telegram X 0.29.1.1817](https://github.com/TGX-Android/Telegram-X/releases/tag/v0.29.1.1817).
It contains ARM64/ARMv7 libraries, and Android reports its installed ABI as
arm64-v8a. Frogram runs native x86_64. Attempts to install an ARM64 Frogram test
repack on this AVD were rejected during ABI derivation, so an architecture-matched
comparison was not obtained. Both apps retain their authorized test sessions.

The original was also tested with avatar-verified selection of the same group
and channel, list scrolling and channel-history scrolling. Whole-scenario
observations follow; these are not a claim of device FPS parity. The original
has different chrome, native code and caches, and its navigation does not emit
Frogram's active-window markers. Idle/animated frame counts also differ.

| Original Telegram X scenario | Deadline misses / rendered frames | Whole-scenario UI frame p95, ms |
| --- | ---: | ---: |
| Group open/back | 76 / 2347 | 19.08 |
| Channel open/back | 54 / 1191 | 19.09 |
| Chat-list scrolling | 24 / 1428 | 7.78 |
| Channel-history scrolling | 32 / 1340 | 7.81 |

The original still has lower observed UI-thread cost in the scrolling reference.
A supplementary ART sampling trace of Frogram history scrolling identifies
renderer synchronization and software text replay among the remaining active
costs. Sampling perturbs execution; it is used for diagnosis rather than frame
rate reporting.
The measured navigation APK is `Frogram-X-0.29.2.1970-x64.apk`, SHA-256
`03ec3be3dae50396648d4f984820d7e3610cb1c48cce8a58d2ebe839252607a5`.
It is signed with the local test certificate and installed on the Intel-backed
AVD. The emulator is retained in the transient user service
`frogram-test-emulator`; this does not enable boot-time autostart. Account data
was preserved throughout updates and emulator restarts.


### Anchored filter menu, build 1971

The filter surface previously lived at the top of `pagerWrap`, whose negative
floating-header margin moved its first rows above and behind the header. Touch
handling separately reconstructed offsets, and the release path accidentally
used the X offset for Y. It could also apply the row from the last move rather
than the actual release position.

The replacement is a rounded glass surface above the main content, placed from
the tab's actual transformed bounds. `ViewCoordinateMapper` shares the complete
ancestor layout/scroll/matrix conversion between placement and pointer mapping.
The menu respects the floating header/player, preferred side, viewport margins
and bottom inset. Its height is limited to available space, with a scrollable
row list and edge-hold scrolling on short screens.

Releasing a held gesture performs the row under that release point. Padding,
outside release and cancellation do not select a filter. Rows also support
ordinary accessible clicks. Closing/detaching clears the pressed row and pending
scroll callbacks; the backdrop is reused during the short reveal animation.
Bottom-tab gestures now cross the top edge of the tab, rather than treating
positions inside it as already outside.

The coordinate regression covers nested translations, scrolling, animated
scale/pivots, mirrored/rotated views, singular transforms and different roots.
The standalone placement test covers four densities, edge clamps, bottom tabs,
side flipping and short viewports. Android tests in light/dark themes verify
placement below the header, release into a different row without an extra move,
selection during reveal, cancellation, padding/outside release and row clicks.
A 2340 × 900 viewport also passed edge-hold scrolling. The original display size,
filter and theme are restored afterwards. The navigation measurements above
belong to build 1970; build 1971 adds this menu repair without changing those
navigation/sampling algorithms.


The final build 1971 passed the same real gesture checks in its R8 release APK,
using standard UI controls rather than controller reflection: selection of
Groups, release into Channels without another move, cancellation and restoration
of the original filter. Release signing, vital lint and the compiled ART profile
checks pass. Final artifact SHA-256:
`05e0a0d4ba050fad38d31cc492ae5f08255757cce0e54e9f8f8706514b02d8cf`.
The installed test artifact is `Frogram-X-0.29.2.1971-x64.apk`.


### Stationary filter holds and content width (build 1972)

The folder tabs still routed a stationary long press to the older folder actions;
only crossing the tab edge opened the filter. Tabs now offer the hold to their
slide-off handler before falling back to ordinary long-click actions. An accepted
hold owns the gesture until release/cancellation, including movement inside the
tab. Releasing over the source tab leaves the filter open for a normal row tap;
dragging into the menu still selects the row at the final release position.

The persistent popup consumes outside touches and closes before Back, tab
switches and search. Its visibility updates Android's Back callback availability.
Folder appearance changes immediately refresh tab placement, content insets and
the menu anchor, including a switch between top and bottom tabs.

The width now comes from the widest visible row plus the surface padding, capped
by the viewport and the existing maximum. An exact measured surface width also
fills the single ScrollView and its rows; the old minimum-only measurement left
an empty strip beside the row backgrounds. Placement still uses the transformed
tab center with edge clamping. The source-extracted JVM regression exercises hold
priority/fallback, gesture ownership, cleanup, event recycling and measurement
at four densities, including hidden rows, long translations and narrow screens.

A theme callback during folder onboarding also exposed an unrelated null preview
before that page was created. It now skips the missing preview; its normal
construction applies the current theme when ready.


The final debug APK passed Android gesture checks with icon-only folder tabs:
a stationary hold opens before any move, release over the tab keeps it open,
a subsequent tap selects the visible row, outside tap consumes the touch, Back
closes the menu first, release into another row uses that final position, and
cancellation preserves the filter. Every visible row fills the surface and the
popup center follows its tab (subject to edge clamping). The same hold/tap checks
passed with bottom tabs, with the popup above the tab. The test also dispatched a
theme callback to a folder-onboarding page before its preview existed.


Both debug and R8 release APKs also passed the earlier drag-menu regression. In
the R8 release, standard UI controls (without controller reflection) verified
stationary hold, full row width, tap after release, Back dismissal and original
filter restoration. All nine performance regressions, eight update tests and
11 message tests pass. Release vital lint, APK signature and compiled ART profile
verification pass. Final local x64 artifact SHA-256:
`a4352e90acdef9d796a84bb0c26b26bd9b4aefa7444c008c48471b05f85b02c6`.
The installed test artifact is `Frogram-X-0.29.2.1972-x64.apk`; this follow-up does
not change the navigation/sampling algorithms measured above.


### Stock hold-to-select dismissal (build 1973)

Build 1972 incorrectly kept the filter open after lifting over the tab. The menu
now always closes on release or cancellation, matching the requested stock
interaction. Holding still opens it before any movement; dragging to a row and
releasing selects that final row and closes the menu. Lifting over the tab or
outside the menu closes it without selection. This supersedes the persistent
popup behavior described for build 1972; content-sized width and transformed
anchor centering are unchanged.

The source-extracted regression now executes MainController's actual finish
handler for release over the tab, selected-row release, cancellation, hidden menu
and missing menu, including cleanup of the gesture target.


The Android UI check passed on build 1973: stationary hold opened the menu;
lifting over the tab closed it and preserved the filter; dragged selection used
the final release point and closed it; cancellation and outside release closed
without selection. The original filter was restored. All nine performance
regressions and the debug APK build pass.
