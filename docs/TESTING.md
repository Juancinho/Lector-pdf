# Testing

## Automated checks

Build the native libraries first (see README). Then:

```bash
./gradlew :app:testDebugUnitTest :AndroidPdfViewer:android-pdf-viewer:testDebugUnitTest :PdfiumAndroid:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

New unit tests cover strict HEX and RGB parsing, black/shorthand colors, all opacity percentages round-tripping via alpha, Unicode meanings, preference recreation, corrupt data fallback, deduplication/order/limits and favorite removal. Existing suites cover annotation journaling, preferences and file safety; do not equate JVM success with native PDF compatibility.

New native test `com.gitlab.mudlej.MjPdfReader.study.HighlightPersistenceTest` creates a standard highlight with arbitrary RGB and opacity, saves, reopens, recolors, saves again and reopens. It needs a matching native ABI on a connected device:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gitlab.mudlej.MjPdfReader.study.HighlightPersistenceTest
```

This new test is compiled as part of the delivery, but it has not been run on physical hardware unless DELIVERY.md explicitly records that. The editor also ships in English and Spanish; other upstream locales currently use English fallback.

The broad inherited instrumentation suite includes upstream UI assumptions (such as fixed palette colors and the original installed package in some helpers) and needs adaptation before claiming the whole suite passes on this fork. CI runs JVM tests/lint and compiles the device tests; it does not pretend to measure pen behavior.

## Xiaomi Pad 7 acceptance checklist

Use copies of documents for the first development build. Record Android/HyperOS version, pen model and build commit.

1. Install alongside MJ PDF; confirm two distinct app names and independent preferences.
2. Open a selectable-text PDF through the Android file picker. Select one line and multiple lines; tap **+**. Try `#19A37F`, shorthand `#3AF`, black, RGB 255/0/128 and opacities 1/25/40/75/100. Preview and resulting annotation should match on light paper.
3. Invalid HEX, eight-digit ARGB, missing RGB and values 256 must disable commit. Cancel must leave the document and stored style unchanged.
4. Give a meaning, favorite a style, use it, close/reopen app. Check favorites, recent order and last opacity. Remove a favorite by selecting it, unchecking and applying.
5. Edit a highlight created by this fork; change color and opacity. Check delete and note controls still work. The custom editor initially uses the last-used style; it does not currently inspect the tapped annotation's style.
6. Save a new copy, fully close the reader, reopen and compare in another standards-compatible reader. Verify exact color/opacity, multiline alignment, quote text and visible/printable flags. Also import an MJ PDF-created highlight. Editing arbitrary third-party highlights is limited by upstream ownership rules.
7. Rotate while the editor is open (also while input is invalid), use portrait, landscape, split screen, keyboard, dark UI and 200% text. Check all fields remain reachable and draft survives. Annotation selection itself follows existing reader behavior across recreation.
8. Test zoom, scroll, page rotation/crop and a large PDF; new colors must remain aligned with selected text. A scan without text will not support text selection; freehand highlighting is phase 3.
9. Try a read-only URI, revoked permission, insufficient destination capacity and interrupted save; verify existing errors/recovery and preservation of original files.
10. Change style, create several highlights, trigger existing recovery and confirm ARGB survives journal replay. New undo/redo has not been implemented.

## Pen slice checks (0.2.0-study)

JVM: `InkGeometryTest` (pressure→width, outline geometry, caps/dots, appearance stream, locale-independent numbers, pressure codec), `PenInputPolicyTest` (tool resolution for stylus/eraser/finger and both buttons, palm guard, pressure smoothing), `PenSettingsStoreTest`, `PenHistoryTest`, and ink cases in `AnnotationEditJournalLineTest`.

Device (native): `com.gitlab.mudlej.MjPdfReader.study.InkPersistenceTest` creates pressure ink, saves, reopens, checks /InkList, colour/opacity, width and pressure, erases, saves and reopens:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gitlab.mudlej.MjPdfReader.study.InkPersistenceTest
```

Xiaomi Pad 7 checklist for this slice:

1. Pen icon → toolbar appears; Back and ✓ close it. Toolbar never blocks the save button.
2. Highlighter: sweep one word, one line, several lines, backwards; selection snaps to words and the result appears in Highlights (after saving) with the right quote and colour. Tap one word. Change colour in the panel and with **+** on the selection card; both stay in sync.
3. Pen and marker: slow/fast strokes, light/heavy pressure (width must follow), dots, very long strokes, writing near page edges and across page boundaries (stroke stays on its starting page), at 100% and high zoom, all page rotations, crop margins on, dual page, dark theme (ink inverted like the page).
4. Fingers scroll and pinch while a writing tool is active; rest the palm while writing and right after lifting the pen: no scroll, no marks. Turn off "Only the pen writes" and check fingers draw.
5. Pen buttons: hold primary while writing → eraser; secondary → highlighter; while reading hold a button and sweep → highlight. Record which buttons HyperOS delivers. Hover shows the eraser ring / ink dot.
6. Eraser removes MJ Study strokes and highlights only; Undo restores them (with notes), Redo removes again.
7. Save a copy, reopen in MJ Study and another reader: ink visible with the same varying width; translucent marker not darker where it overlaps itself; highlights normal. Kill the app before saving and confirm recovery replays ink.
8. Latency and smoothness with long documents; no stutter while writing at high zoom.

## Future pen validation gate

Before phase 3 is accepted: stylus-only writing vs finger pan/pinch, palm contacts during active stroke and immediately afterward, pointer cancellation, historical samples, pressure range and zero-pressure edge cases, pen buttons/eraser, handwriting at different zooms, all rotations, undo/redo, save/reopen and independent reader compatibility. Measure latency and memory on large/scanned PDFs. None of these capabilities may be inferred from successful highlight tests.
