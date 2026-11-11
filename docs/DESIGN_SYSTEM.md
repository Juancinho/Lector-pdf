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

## Typography, space and shape

Use system sans (Roboto/fallback) and Android sp, respecting font scaling. Titles 22sp medium, body 16sp regular, secondary/field labels 14sp, compact swatch metadata 11sp. Never bake text into images.

Spacing scale: 4, 8, 12, 16, 24, 32dp. Panel inset 24dp; field separation 12dp; related controls 8dp. Control corner 12dp, panel corner 20dp, swatches circular. These are product tokens, not values claimed to be extracted from Astryx.

## Components and interaction states

- Highlight editor: one scrollable native dialog, a paper preview, HEX, RGB row, opacity slider, optional meaning, favorite checkbox and compact wrapped favorite/recent rows. Explicit Use/Cancel. Cancel changes no preference or document.
- Selection card: six quick colors plus a custom `+` button. Each has a 48dp hit target; the inner color sample is 36dp. The custom style occupies the first slot, while quick colors inherit its opacity.
- Inputs: outlined, visible label, inline validation, disabled commit for invalid HEX/RGB. Edits synchronize only after valid input. Never silently interpret ARGB as RGB.
- Primary commit: highest contrast. Secondary cancel: quiet. Pressed/focus/ripple feedback uses native Material states. Disabled commit cannot write a stale color. A selected color must have text/outline cues, not just hue.
- Favorite/recent controls expose HEX, opacity and meaning to accessibility services. Meanings are preference labels, not hidden annotation metadata.
- Ink toolbar: collapsed “Write” pill, expanded card bounded to 760dp, quiet 48dp monoline icon buttons and a visible current-tool label. Horizontal overflow keeps targets large on compact screens. Selected tools use a tinted neutral surface and accent icon; disabled history actions are dimmed. Save and collapse remain in the header. Color/nib settings use progressive disclosure in one scrollable dialog. Configurable/favorite tool groups remain planned.
- Ink palette: graphite, clear blue, warm coral, green and gold offer a more cheerful study character without saturating the reader chrome. PDF colors are content choices; theme changes never recolor saved strokes.

## Responsive behavior

The editor scrolls within the Android dialog window, including landscape and keyboard-open states. RGB inputs share width; palette rows wrap after four entries. Tablet dialogs remain bounded by Material dialog sizing; phone width preserves field touch targets. Test at 320dp width, landscape tablet, 200% font size, RTL and split screen before declaring polish complete.

Future expanded tablet layout (>=840dp): optional 280–360dp side panel, compact floating toolbar, document consuming remaining space. Medium: transient side panel. Compact: drawer/sheet with the same component language. Those reader layouts are planned, not implemented here.

## Accessibility and motion

48dp targets, minimum 4.5:1 normal text contrast, descriptive TalkBack labels, keyboard focus order following visual order, validation announced through native fields. Do not rely on color alone. Respect system animation scale; future transitions 120–180ms, no decorative loops. Test Switch Access and pen hover on hardware rather than assuming support.
