# Frogram X glass design code

This is the reference for new and existing floating UI. Shared implementation:
`GlassStyle`, `GlassMenuStyle`, `GlassRippleDrawable`, `GlassPageHeader`, and the
`glass*` theme roles. Colour derivation and contrast are documented in
[GLASS_COLORS.md](GLASS_COLORS.md).

## Shapes and feedback

- Folder selection and folder press feedback have capsule ends. The radius is
  half the current visible height, never a fixed small corner radius. Wide folder tabs remain capsules after the label opens.
- Icon feedback is a centred 44dp circle within the original touch target.
  Keep touch targets at least 48dp. Ripple masks may not spill outside the button.
- A tab can reserve its expanded layout width while showing only an icon/counter.
  The mask follows the **visible** width and RTL edge on every animation frame.
  Cropping an expanded capsule creates a square end and is not acceptable.
- Header, page/profile cards and menus use 26dp corners, capped at half their
  visible height. Multirow/pinned headers retain this card radius as they grow.
  These are different from capsule tabs and circles; do not substitute a universal radius for all shapes.
- Menu rows use 16dp corners, 48dp height, 15sp medium labels and 14% accent press
  fill. Selected filter rows have a 12% accent fill. Padding inside the surface is
  6dp; icon/text gap is 16dp; row start/end padding is 16/20dp (mirrored in RTL).

## Spacing and colour

- Floating header cards share one geometry on chat/list/settings/profile pages:
  8dp side margins, top at status inset + 3dp, bottom at the logical header
  bottom + 6dp, and controls shifted down by 4.5dp to centre them in that shell.
  Ordinary settings rows and profile/management cards begin 18dp below the logical header, leaving 12dp below the visible shell.
  Folder settings use a fixed compact header, with a separate Edit name action
  that activates its title field. Keep icon selection and visibility in the body;
  no expanded-avatar spacer, collapsing title or rectangular header backing.
  Folder inclusion/exclusion pages retain a compact title/search action. Selected
  chats/types are removable capsules in a separate rounded body group, followed
  by the type/chat lists. These rows use the shared card insets and colours.
- Menus have an 8dp gap and 8dp screen clearance. Chat menus follow the visible
  header/player; settings/profile overflow menus follow the actual overflow
  button bottom, including when the photograph is expanded. Never anchor those
  menus below the entire expanded photograph.
- Use `glassText`, `glassTextSecondary`, `glassIcon`, `glassActive`,
  `glassPlaceholder`. Do not choose unrelated black/grey/accent values per widget.
- Light themes use the accent-derived contrast-adjusted palette. Night Blue keeps
  its current active/inactive roles. Only neutral black themes reverse those
  folder roles. Badge fill follows its own tab's animated icon/text colour;
  badge digits contrast with the fill and inactive folders retain the filled badge.
- The status scrim is neutral white/black, never accent-tinted, and ends 12dp below
  the status inset with a smooth fade. Android owns the actual status icon colour.
  On Android 11+, connection-state UI never requests LOW_PROFILE: notification
  and connectivity icons remain visible during reconnects and their exit delay.

## Settings and profiles

- A glass main block precedes the settings rows. Ordinary subpages share the
  same shell; each retains its own actions, scrolling and editable content.
- The expanded profile/settings avatar stays inside the rounded main block.
  Keep the stock continuous avatar-to-compact-header motion, image viewer origin,
  search/media transitions and hit targets. Expanded name/status begin 26dp
  from the start edge, aligned with the back-arrow glyph. Compact avatar/text
  keep their usual layout. The expanded-avatar spacer reserves height only:
  never draw a rectangular filling behind the rounded avatar/header.
  Clip photograph scrims to the avatar's current bounds and radius throughout
  its collapse, so they cannot tint a rectangle outside the shrinking photo.
- Use white text/icons only over the expanded photograph, blending continuously
  from glass roles as the photograph expands. A missing avatar must remain legible.
- Without a photo, keep the round placeholder, name and status in the compact
  header. The list only reserves the standard page clearance. Real photos keep
  the expanded spacer and continuous collapse.
  Bot profile actions occupy adjacent slots. Reserve title space for the chat,
  overflow and available edit actions.
- A player enters below the main card, with its reserved page row offset by
  12dp; translate its input coordinates by the same amount. Clip it below that card during expansion
  so it cannot cut off the avatar or cover the title. Fade its controls in only
  over the final 20% of its reveal, avoiding a thin fragment of a card/controls
  while the photograph still occupies the header. Hidden controls reject touches.
- Settings and profile rows scroll behind the floating header to the screen edge.
  Extend the page's painting viewport without moving the original rows or
  changing the logical viewport, clearance, scroll origin, avatar-collapse or media coordinates.
  Keep the additional rows above the viewport attached only as far as the header.
  Open clipping along the scrolling-container path, preserving row/icon clipping.
  Every attached row keeps an explicit clip to its own bounds: allowing list
  overflow must not let a row's background erase its neighbours. Restore its
  original clip on detach/recycle.
  The extended page itself remains clipped to its own viewport. Cached horizontal
  media pages must not escape sideways over the chat during back navigation;
  software backdrop sampling respects that same outer boundary.
  Do not use RecordingCanvas viewport rejection to cull overflow rows: its local
  recording bounds end below the header although HWUI can still display those
  child nodes above the list. Retain software capture rejection for performance.
  On Android 12+, settings lists include header overflow in their physical viewport, with
  equal extra top padding. Android's stretch effect must retain the rows behind
  the header during pull, hold and release. Preserve the original logical row
  origin, scroll extent, saved scroll positions and expanded-header factor.
  Establish the viewport before initial layout and preserve attached row anchors
  when insets change; profiles/chat media keep their own viewport geometry.
- Sample only rows intersecting the small glass capture. The page paints the
  neutral background beneath these rows; the header must not paint an opaque
  rectangle over them. Invalidate on descendant/scroll/theme changes, disable
  sampling for inactive pages and release closed-menu backdrops.
- Profile and settings information/action rows form rounded groups with 8dp side
  margins and 26dp outer corners. Use a quiet accent-derived filling, 14% accent
  press feedback and inset separators at 10% glass text opacity. Profile values
  retain their original 16sp, descriptions 15sp and captions 13sp; icons use
  28dp. Centre icons beside the first two text lines (value and caption for
  short fields, the first two body lines for a long description), rather than
  aligning them to the first line. Use compact 68dp information rows and
  56dp single-line action rows. Grow multiline rows only as their content needs,
  with approximately 16dp top and bottom clearance, one aligned text column
  64dp from the card start and 20dp end padding. Measure text with those same sizes
  and insets. Apply the same groups to settings subpages, pickers and settings
  editors, keeping the original font sizes, destructive-action colours, input
  behaviour, focus/keyboard handling, and control measurements. Clip group
  corners and press feedback together. Ordinary single-line controls use 56dp;
  two-line controls use 68dp. Sliders, session/device rows and editors retain
  their content-specific heights and must not be forced into a text-row height.
  Section titles and help text stay outside the cards. Replace legacy section
  shadows with the shared card surface, retaining their spacing.
  Light themes use a soft neutral 2dp shadow with a 1dp downward offset and a
  thin contour at 9% glass text opacity. Separate each whole group from its
  background without adding borders or shadow seams between joined rows. Dark
  themes retain the quiet existing filling. This separation also applies to
  profile groups. Chat/message lists and modal option sheets keep their layouts.
  Group management uses those same settings cards, including its editable fields
  and action rows; retain the profile header and its own scroll geometry.
  Both the base list and its participant pages preserve their original padding:
  never add standalone settings-page clearance to these synchronized lists.
  Check repeated drags through the pinning boundary and idle stability after release.
  Management tabs have 6dp clearance above and 4dp below from their initial position
  through pinning. Their compact header needs no separate expansion animator.
  Their tab labels replace the repeated list headings (Admins/Restricted/Blocked).
  Profile attachment content starts 4dp closer to its island; keep date labels visible.
  Base and participant lists use one handoff range; reserve the base scroll tail
  independently of member counts, including when switching to empty or short tabs.
  Allowed reactions use independent rounded tiles in the four-column grid, with
  equal 8dp horizontal gaps/outer clearance and 8dp row gaps. Full-width controls
  keep their normal groups. Never combine the legacy grid filling/spacing with
  card decorations; mirror tile insets in RTL and use semantic glass captions.
  Editable settings headers use glass text/hint/icon roles too; never carry
  white legacy header text or placeholder glyphs onto light glass. Tabbed settings
  pages share the floating shell and capsule feedback. Invalidate the list itself
  on theme changes so its contour/shadow display list updates without a scroll.
  A second header row of tabs has the same 12dp side inset as profile media tabs,
  keeping its selection/press capsules inside the rounded glass shell.
  Settings separators keep their original line length, centred horizontally
  within the group, and resolve their fill/stroke from the current theme when
  drawing. Fill only their own bounds; never cache a previous theme filling.
  Settings editor cards use compact floating hints with 12dp internal hint
  travel and 20dp list clearance from the logical header (14dp visible gap).
  Account avatars and session glyphs share the 28dp/64dp icon/text columns;
  light-theme device glyphs use quiet grey with a small accent tint.
  Popular sticker/emoji packs use separate 26dp cards, 8dp side margins and
  12dp gaps; pack preview grid cells and install buttons keep their hit targets.
  Floating confirmation buttons use the header glass and semantic icon colour,
  retaining their original 56dp circle, 4dp outer padding and progress states.
  Software backdrop capture draws the row's stable filling without executing its
  live RippleDrawable, preserving the RenderThread press animation.
- Profile media tabs use a separate glass island, semantic glass colours and
  capsule selection/press feedback. Dates such as Today/Yesterday/month/year
  remain visible below the tabs from the initial view. Reserve space for the
  full tab panel before the first media header, and synchronize nested scrolling
  through the full profile range. Do not mask the date headers or add opaque
  backing. Pin the panel 6dp below the main header; media rows
  continue behind both islands to the top edge. Preserve the existing media
  search/selection actions, avatar collapse and nested-scroll coordinates.
  Hide the old opaque merging bridge and shadow. Exclude glass panels from
  backdrop sampling and ignore their own invalidations to avoid feedback loops.
- Horizontal page navigation uses the same stationary glass-shell morph as
  main-to-chat. Cross-fade the header contents inside the changing rounded shell.
  Expanded avatar headers keep their live hardware foreground during navigation.
  Reversing an Android back gesture must reveal the avatar, name and status while
  the finger is still down, including at zero progress; never wait for cancellation
  to finish or reuse an empty foreground snapshot.
  The page content keeps its normal slide. Each foreground keeps its own
  endpoint expansion during the dissolve; applying the interpolated shell
  height to both foregrounds displaces folder tabs and avatar text. Expanded
  photos must be clipped to the shell on intermediate frames and endpoints.
  Reserved pages also paint their status-area background while sliding, so
  the outgoing page cannot expose black window strips beneath the status fade.

## Required visual checks

Check light, neutral black and Night Blue themes, short and long/RTL labels, icons
with counters, missing and real avatars, long titles, portrait and landscape.
For tabs, inspect down/early expansion/mid expansion/release, including cancellation
and rapid switches. For settings/profile, inspect expanded, intermediate and
collapsed avatar/header states and back navigation; static endpoint screenshots
alone are insufficient. Check keyboard/search/media controls and status icons.
Preserve authenticated sessions and existing folders. Remove only owned QA data.
