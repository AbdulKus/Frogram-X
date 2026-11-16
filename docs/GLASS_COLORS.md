# Foreground colours on glass

The floating interface uses five shared roles derived from the theme's filling,
header, link accent and text colours. Light themes previously mixed plain black
text, ordinary grey icons and several unrelated active colours on the same glass.

| Role | Use |
| --- | --- |
| `glassText` | Titles and editable text |
| `glassTextSecondary` | Subtitles, hints and previews |
| `glassIcon` | Ordinary actions and their labels; same tone as secondary text |
| `glassActive` | Selected tabs, selected filter rows and active formatting |
| `glassPlaceholder` | Input hints and placeholder paint |

Light text tones blend the theme's neutral text with its accent. The active role
starts from the accent. Each is adjusted towards black/white only as far as needed
to reach 4.5:1 contrast. The calculation uses the WCAG sRGB transfer function and
relative luminance, including its 0.04045 breakpoint and 0.2126/0.7152/0.0722
weights. This also exceeds the 3:1 requirement for enabled graphical controls.

The light glass veil is 78% so a black photo underneath cannot undermine the
foreground contrast. The reference surface additionally reserves 15% for inner
selected/pressed fills; formatting and selected menu/tab fills use that budget.
The model covers enabled, settled content; opacity animations naturally pass
through intermediate contrast. Dark themes use their existing semantic text,
secondary, icon and accent roles.

The role resolver works through normal and forced theme text lookups. It caches
an immutable palette by its actual input colours, so editing a custom theme in
place refreshes it and ordinary drawing does not repeat contrast calculations.
New colour IDs are appended to the theme schema; existing IDs retain their order.

Applied to floating chat/list headers, tabs/counters, filter menu, composer text
and controls, composer action buttons, reply/pinned previews, compose FAB/menu,
scroll/mention/reaction buttons, floating audio player, attachment tabs,
recording controls, bot keyboard labels/repaintable icons and text formatting.
Avatar colours, destructive/status colours and bot-defined semantic button fills
retain their separate meaning.

`GlassPaletteTest` checks the WCAG reference values and 1000 accents against
black/white/RGB media and selected fills. `test_glass_theme.py` runs the real
resolver and themed-text method against mutable and forced themes, checks cache
reuse and verifies ordinary non-glass colour lookups are unchanged.

References:

- [WCAG 2.2 contrast minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
- [WCAG 2.2 non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)
- [Material colour roles](https://m3.material.io/styles/color/roles)


Android verification covered Blue, White Black, Red, Orange, Green, Pink, Cyan
and Classic. AndroidX's independent contrast calculation reported a minimum of
4.5002:1 against the conservative reference surface. The filter menu's displayed
text matched its normal/selected roles. Composer and bot-keyboard text updated
through live theme changes; formatting was rendered in Blue and Green. Original
theme and day/night preferences were restored; no bot command was sent. The
emulator used the Intel UHD Graphics 630 host OpenGL renderer.


Repaintable custom emoji in TextView-based bot buttons now inherit the current
label colour instead of forcing `ColorId.text`. Non-repaintable emoji keep their
own colours. Composer placeholder title/subtitle rendering uses the placeholder
role as well as its paint/hint state.


Both system-bar update paths share the floating-header foreground rule. This
prevents navigation-bar colour updates from restoring white status icons over
light glass in coloured themes such as Pink.


The status scrim is neutral white/black according to the theme. It covers the
entire status inset before fading over another 28dp, including during navigation.
On Android 11+, WindowInsetsController exclusively owns icon appearance; legacy
LIGHT_* flags no longer overwrite it. Focus/resume reapplies the requested status
visibility and icon tone. Android 16 emulator verification passed 18 notification
shade/quick-settings cycles across Blue/Pink/Night, Home/resume (including automatic
day/night restoration), and fullscreen recovery. Android 17 hardware was not
available for this verification.

The standalone archive now uses floating header/player surfaces and the same
foreground roles, with content/search padding extending behind the header.
The main hamburger has no badge over its strokes. Bot keyboard keys use 48dp
minimum height, medium-weight labels, 10dp inner corners, a 6% accent fill and
an 8% accent ripple inside one shared glass panel.

Folder counters derive their tone from their own tab's animated selection.
Inactive and muted numbers match the tab label; the selected unmuted badge uses
that tab's accent with the theme filling as its contrasting foreground. The
shared selection calculation also handles jumps over intermediate tabs.
`test_folder_counter_colors.py` executes production colour/selection methods.
Android verification created and removed one owned test folder, checked all four
tab styles on Blue/Pink/Night/Black White/Black, and checked archive shell/insets
on those themes. Existing folders and chats were retained.
