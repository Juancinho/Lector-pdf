# Roadmap

This is a sequence of functional deliveries, not a list of completed promises.

## Phase 0: foundation

- [x] Import original git history and preserve GPL/attribution.
- [x] Inspect rendering, standard highlights, signature capture and save/recovery.
- [x] Document architecture, design system and pen investigation.
- [x] Compile the development fork, both native ABIs and the device-test APK; pass all executable JVM tests.
- [x] Add Android CI workflow with unit tests, lint, debug/test APK artifacts.
- [x] Publish imported source to Juancinho/Lector-pdf. Preserve upstream authorship; oversized historical archives and connector-managed workflow files are excluded from imported history.
- [x] Start GitHub builds; fix runner SDK setup. Require green checks before accepting the ink delivery.

## Phase 1: custom highlighting

- [x] Implement validated HEX/RGB, opacity, live preview, local preference persistence.
- [x] Implement current, recent and favorite styles with optional meanings.
- [x] Integrate selection-card creation/recolor and settings entry.
- [x] Carry opacity through Java/JNI, standard PDF annotations, overlays and journal edits.
- [x] Add unit coverage and native save/reopen instrumentation test.
- [x] Native save/reopen and editor-recreation tests on the API 30 CI emulator.
- [ ] Physical-device save/reopen, third-party PDF reader comparison, landscape/font-size visual acceptance.
- [ ] See TESTING.md and DELIVERY.md for actual build/test outcomes; implementation checkmarks do not imply hardware verification.

## Phase 2: tablet reader

- [x] Collapsible floating ink toolbar, read/write modes and native light/dark components.
- [ ] Configurable favorite tool groups, fully hidden reading controls and responsive page/annotation panels.
- [ ] Apply common tokens across reader chrome, day/night and accessibility states.

## Phase 3: pen engine

- [x] Implement PDF-space geometry, pen/finger routing, pressure normalization, historical samples and smoothing.
- [x] Implement vector pen/pencil/marker/shapes, stroke eraser, lasso move/scale, bounded undo/redo, standard /Ink and journal patches.
- [x] Execute targeted native create/save/reopen and synthetic pen/finger integration tests in CI.
- [ ] Independent-reader checks and physical capability diagnostics.
- [ ] Validate Xiaomi pressure/buttons/palm rejection and profile latency/memory.
- [ ] Tool favorites, tilt, long-stroke segmentation, rotation transforms and external ink editing.

## Phase 4: library

- [ ] SAF-first real folders and batch operations, virtual collections separately modeled.
- [ ] Tags, thumbnails, search and session restore with bounded background work.

## Phase 5: academics

- [ ] Unified annotation panel, Markdown exports with page references, text boxes/sticky notes.
- [ ] Blank/grid/dot page insertion and page management after PDFium capability verification.
- [ ] Optional dual-page/tabs/comparison; external-provider sync and offline handwriting recognition evaluated later.
