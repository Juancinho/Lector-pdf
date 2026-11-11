# Roadmap

This is a sequence of functional deliveries, not a list of completed promises.

## Phase 0: foundation

- [x] Import original git history and preserve GPL/attribution.
- [x] Inspect rendering, standard highlights, signature capture and save/recovery.
- [x] Document architecture, design system and pen investigation.
- [x] Compile the development fork, both native ABIs and the device-test APK; pass all executable JVM tests.
- [x] Add Android CI workflow with unit tests, lint, debug/test APK artifacts.
- [ ] Confirm target GitHub repository and publish branch/PR (none matching MJ Study is connected in this session).
- [ ] Run workflow on GitHub after publishing.

## Phase 1: custom highlighting

- [x] Implement validated HEX/RGB, opacity, live preview, local preference persistence.
- [x] Implement current, recent and favorite styles with optional meanings.
- [x] Integrate selection-card creation/recolor and settings entry.
- [x] Carry opacity through Java/JNI, standard PDF annotations, overlays and journal edits.
- [x] Add unit coverage and native save/reopen instrumentation test.
- [ ] Native save/reopen test on device, third-party PDF reader comparison, landscape/font-size visual acceptance.
- [ ] See TESTING.md and DELIVERY.md for actual build/test outcomes; implementation checkmarks do not imply hardware verification.

## Phase 2: tablet reader

- [x] Floating liquid-glass pen toolbar (one tool group, options panel on demand, read/write modes, Done/back closes). Entry from the top bar pen icon, the actions menu and configurable shortcuts.
- [x] Glass tokens (light/dark) applied to the toolbar, selection card, crop status card and actions menu (real backdrop blur on Android 12+ when the system enables cross-window blur).
- [ ] Responsive page/annotation side panels; glass tokens across the remaining reader chrome and settings.
- [ ] Visual acceptance on Xiaomi Pad 7: portrait/landscape, split screen, 200% font, TalkBack, dark mode.

## Phase 3: pen engine

- [x] Page-coordinate transform (PDF user space ↔ document, rotation and crop aware) and pen/finger routing: stylus writes, fingers scroll/zoom; pen takes over a finger gesture cleanly.
- [x] Pen highlighter: sweep over text → word-snapped standard /Highlight with the current highlight style; listed with the other highlights.
- [x] Pressure-sensitive pen and translucent marker as standard /Ink (centreline /InkList, variable-width /AP outline, MJPressure sidecar inside the same annotation), stroke eraser for ink and app highlights, undo/redo through the recovery journal.
- [x] Pen buttons policy: primary = eraser while writing, secondary = highlighter; while reading, holding a button and sweeping highlights. Eraser tool type always erases. Hover cursor. Palm guard while writing.
- [ ] Capability diagnostics and acceptance on Xiaomi Pad 7 + Xiaomi pen (pressure range, which buttons reach the app, hover, latency, palm). Nothing pen-related is hardware-verified yet.
- [ ] Partial (point) eraser, lasso and transformations after coordinate/serialization tests pass.

## Phase 4: library

- [ ] SAF-first real folders and batch operations, virtual collections separately modeled.
- [ ] Tags, thumbnails, search and session restore with bounded background work.

## Phase 5: academics

- [ ] Unified annotation panel, Markdown exports with page references, text boxes/sticky notes.
- [ ] Blank/grid/dot page insertion and page management after PDFium capability verification.
- [ ] Optional dual-page/tabs/comparison; external-provider sync and offline handwriting recognition evaluated later.
