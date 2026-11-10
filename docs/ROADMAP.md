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

- [ ] Floating tool groups, read/write modes, hide controls, responsive page/annotation panels.
- [ ] Apply common tokens across reader chrome, day/night and accessibility states.

## Phase 3: pen engine

- [ ] Capability diagnostics on Xiaomi hardware, page-coordinate transforms, pen/finger routing.
- [ ] Pressure/smoothing/palm state machine, vector tools, undo/redo, standard /Ink persistence.
- [ ] Lasso and transformations after coordinate/serialization tests pass.

## Phase 4: library

- [ ] SAF-first real folders and batch operations, virtual collections separately modeled.
- [ ] Tags, thumbnails, search and session restore with bounded background work.

## Phase 5: academics

- [ ] Unified annotation panel, Markdown exports with page references, text boxes/sticky notes.
- [ ] Blank/grid/dot page insertion and page management after PDFium capability verification.
- [ ] Optional dual-page/tabs/comparison; external-provider sync and offline handwriting recognition evaluated later.
