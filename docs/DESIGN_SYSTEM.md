# MJ Study design system · v0.1

## Reference and identity

Astryx was visually inspected on 11 November 2026: https://astryx.atmeta.com/ and /components, including its light and dark component gallery. Observed patterns: spacious neutral canvases, compact typographic hierarchy, distinct filled primary and quiet secondary controls, restrained outlines, grouped tools and soft card corners. MJ Study translates these patterns into Android Views + Material 3; it does not embed the web library, copy its logo or reproduce the decorative homepage gradients.

Identity: paper, graphite and a muted green accent. Color belongs to study content, not to every UI control. The PDF remains the visual focus. The first slice applies this system to the new highlight editor; the rest of the upstream reader has not yet been redesigned.

## Color tokens

| Token | Light | Dark | Purpose |
| --- | --- | --- | --- |
| study_surface | #FAFAF8 | #202321 | Editor surface |
| study_ink | #202321 | #F2F4EF | Main text and primary controls |
| study_muted | #626761 | #B6BDB4 | Supporting text |
| study_outline | #CFD3CD | #565E54 | Boundaries, never sole focus indicator |
| study_subtle | #EEF0EC | #2C312D | Grouped surface |
| study_accent | #476C58 | #ADCEB6 | Reserved selected accent |

Tokens live in values/study_colors.xml and values-night/study_colors.xml. No dynamic system recoloring for the new identity. PDF paper preview remains white deliberately and shows multiply blending; theme changes must not mutate saved annotation colors.

### Liquid glass (0.2.0-study)

| Token | Light | Dark | Purpose |
| --- | --- | --- | --- |
| study_glass_fill | #C9FBFBF9 | #BF1C1F1D | Floating toolbar surface |
| study_glass_fill_strong | #EBFBFBF9 | #E61C1F1D | Cards and panels without real blur |
| study_glass_fill_blurred | #99FBFBF9 | #8C1C1F1D | Windows with system backdrop blur |
| study_glass_edge | #CCFFFFFF | #33FFFFFF | Inner specular edge |
| study_glass_outline | #1F202321 | #66000000 | Outer hairline |
| study_glass_sheen | #73FFFFFF | #1FFFFFFF | Top sheen gradient start |
| study_glass_selected | #1C202321 | #29FFFFFF | Selected tool background |
| study_glass_divider | #26202321 | #29FFFFFF | Group dividers, inactive tracks |

Blur, by surface (0.3.0-study):

- Floating pen toolbar (Android 12+): real backdrop blur. `GlassBackdropView` redraws only the page region under the capsule at 1/6 resolution before each frame and blurs it on the GPU with `RenderEffect` (18dp radius). It invalidates only when the sampled pixels change, pauses during pen strokes, and disables itself on any failure. The capsule then uses `study_glass_fill_blurred`; without blur it uses the denser `study_glass_fill`.
- Dialogs, bottom sheets and the highlight editor (Android 12+): `windowBlurBehindEnabled` blurs everything behind the window when the system allows cross-window blur; surfaces use the near-opaque `study_glass_dialog` so text stays legible when it does not.
- Actions menu: window background blur plus translucent glass when cross-window blur is enabled.
- Popup menus, snackbars, the full-screen side menu and the selection card: dense frosted glass (no blur available for those windows/views).
- Home: a low-contrast ambient gradient (`bg_study_ambient`) under translucent glass cards, search bar and tabs.

Never put glass over glass; never tint the PDF.

## Typography, space and shape

Use system sans (Roboto/fallback) and Android sp, respecting font scaling. Titles 22sp medium, body 16sp regular, secondary/field labels 14sp, compact swatch metadata 11sp. Never bake text into images.

Spacing scale: 4, 8, 12, 16, 24, 32dp. Panel inset 24dp; field separation 12dp; related controls 8dp. Control corner 12dp, panel corner 20dp, swatches circular. These are product tokens, not values claimed to be extracted from Astryx.

## Components and interaction states

- Highlight editor: one scrollable native dialog, a paper preview, HEX, RGB row, opacity slider, optional meaning, favorite checkbox and compact wrapped favorite/recent rows. Explicit Use/Cancel. Cancel changes no preference or document.
- Selection card: six quick colors plus a custom `+` button. Each has a 48dp hit target; the inner color sample is 36dp. The custom style occupies the first slot, while quick colors inherit its opacity.
- Inputs: outlined, visible label, inline validation, disabled commit for invalid HEX/RGB. Edits synchronize only after valid input. Never silently interpret ARGB as RGB.
- Primary commit: highest contrast. Secondary cancel: quiet. Pressed/focus/ripple feedback uses native Material states. Disabled commit cannot write a stale color. A selected color must have text/outline cues, not just hue.
- Favorite/recent controls expose HEX, opacity and meaning to accessibility services. Meanings are preference labels, not hidden annotation metadata.
- Pen toolbar (0.3.0): a floating glass capsule: grip · Read | Highlighter · Pen · Marker · Eraser | colour well | Undo · Redo | Collapse · Done (accent disc). Instruments are drawn tools (neutral gradient body, tip/band in the live colour, eraser rubber fixed) that rise from the capsule rim when selected (200ms decelerate); in vertical layout their tips point towards the page. Drag by the grip (snaps within 28dp of an edge; position stored as fractions of free space), double-tap the grip or use the options to switch horizontal/vertical, collapse to a 60dp glass bubble with the active tool and a colour ring. Re-tapping the active tool or the colour well opens a glass popover placed beside the bar wherever it is (colour, maximum width with a live pressure-stroke preview, pen buttons, layout, behaviour). 44–48dp targets, tooltips, selection by elevation + state description, never colour alone. Back or Done closes it. Entry: pencil in the top bar, actions menu, full-screen side menu (default), shortcuts.
- Line icons: 24dp, 1.8–2.2 stroke, round caps/joins, tinted by the view; instruments are full-colour artwork with light/dark variants.

## Responsive behavior

The editor scrolls within the Android dialog window, including landscape and keyboard-open states. RGB inputs share width; palette rows wrap after four entries. Tablet dialogs remain bounded by Material dialog sizing; phone width preserves field touch targets. Test at 320dp width, landscape tablet, 200% font size, RTL and split screen before declaring polish complete.

Future expanded tablet layout (>=840dp): optional 280–360dp side panel, compact floating toolbar, document consuming remaining space. Medium: transient side panel. Compact: drawer/sheet with the same component language. Those reader layouts are planned, not implemented here.

## Accessibility and motion

48dp targets, minimum 4.5:1 normal text contrast, descriptive TalkBack labels, keyboard focus order following visual order, validation announced through native fields. Do not rely on color alone. Respect system animation scale; future transitions 120–180ms, no decorative loops. Test Switch Access and pen hover on hardware rather than assuming support.
