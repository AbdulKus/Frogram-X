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

- Floating header cards have 8dp side margins and 3dp vertical edge clearance.
  Controls are centred in the visible card: chat/list shells include their 4.5dp
  overflow inset; reserved settings/profile headers keep a zero inset. Do not
  carry the overflow offset into a header that reserves space above its rows.
- Menus have an 8dp gap from the actual visible header/player and 8dp screen
  clearance. Anchor to rendered geometry, not a second sum of status-bar offsets.
- Use `glassText`, `glassTextSecondary`, `glassIcon`, `glassActive`,
  `glassPlaceholder`. Do not choose unrelated black/grey/accent values per widget.
- Light themes use the accent-derived contrast-adjusted palette. Night Blue keeps
  its current active/inactive roles. Only neutral black themes reverse those
  folder roles. Badge fill follows its own tab's animated icon/text colour;
  badge digits contrast with the fill and inactive folders retain the filled badge.
- The status scrim is neutral white/black, never accent-tinted, and ends 12dp below
  the status inset with a smooth fade. Android owns the actual status icon colour.

## Settings and profiles

- A glass main block precedes the settings rows. Ordinary subpages share the
  same shell; each retains its own actions, scrolling and editable content.
- The expanded profile/settings avatar stays inside the rounded main block.
  Keep the stock continuous avatar-to-compact-header motion, image viewer origin,
  title positioning, search/media transitions and hit targets.
- Use white text/icons only over the expanded photograph, blending continuously
  from glass roles as the photograph expands. A missing avatar must remain legible.
- Without a photo, keep the round placeholder and centre its information group
  below the controls. Keep the reserved layout height so setting/removing a photo
  does not move the following rows abruptly.
- A player enters below the main card. Clip it below that card during expansion
  so it cannot cut off the avatar or cover the title. Fade its controls in only
  over the final 20% of its reveal, avoiding a thin fragment of a card/controls
  while the photograph still occupies the header. Hidden controls reject touches.
- Avoid replaying the entire settings list just to shade a reserved header area.
  Reuse the neutral glass surface and invalidate on actual geometry/theme changes.
  Disable sampling for inactive pages and release closed-menu backdrops.

## Required visual checks

Check light, neutral black and Night Blue themes, short and long/RTL labels, icons
with counters, missing and real avatars, long titles, portrait and landscape.
For tabs, inspect down/early expansion/mid expansion/release, including cancellation
and rapid switches. For settings/profile, inspect expanded, intermediate and
collapsed avatar/header states and back navigation; static endpoint screenshots
alone are insufficient. Check keyboard/search/media controls and status icons.
Preserve authenticated sessions and existing folders. Remove only owned QA data.
