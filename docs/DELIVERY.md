# Delivery 0.3.0-study · 11 November 2026

Status: **development APK, physical acceptance pending**. Tablet polish on top of 0.2.0.

## Implemented

- Drawn instrument artwork (pen, marker, highlighter, eraser; light/dark) whose tip/band takes the live colour; selected tools rise from the capsule rim. New line icons (cursor, undo, redo, check, collapse, pencil, grip).
- Floating toolbar: drag by the grip with edge snapping and persisted position, horizontal/vertical layout (double-tap grip or options; vertical tips point to the page), collapse to a glass bubble with a colour ring; options popover placed beside the bar with a live pressure-stroke/highlight preview.
- Configurable pen buttons (erase / select text / highlight / nothing per button, defaults erase / select). "Select text" ends in the normal selection card.
- "Write and highlight" in the full-screen side menu (default, plus a one-time migration for customised menus).
- Liquid glass: real backdrop blur under the toolbar on Android 12+ (`GlassBackdropView`), blur-behind for dialogs, sheets and the highlight editor on Android 12+, glass popups, snackbars, side menu, selection card and home library (ambient gradient, glass cards, search bar, tabs).

## Verified in this environment

| Check | Result |
| --- | --- |
| app JVM tests | 824 passed; 39 skipped |
| AndroidPdfViewer JVM tests | 319 passed; 16 skipped |
| PdfiumAndroid JVM tests | 31 passed; 2 skipped |
| Lint | 0 errors, 305 warnings (no new warnings in changed code) |
| Debug / device-test APKs | Assembled; five arm64 .so files byte-identical to the built libraries |
| Visual review | Toolbar (horizontal, vertical both sides, collapsed, options; light/dark), dialogs, home rows and side menu rendered with Robolectric native graphics in a local, uncommitted harness and inspected. Backdrop capture alignment verified; the GPU blur itself (`RenderEffect`) and elevation shadows are not rendered by Robolectric. |

## Not verified / limitations

- Still no Xiaomi Pad 7 / Xiaomi pen: button mapping (which physical button is "primary", HyperOS shortcuts), pressure, hover, palm, latency and the on-device look of blur/shadows are unverified.
- The backdrop blur redraws the page region under the toolbar each changed frame (1/6 resolution, clipped); it pauses while writing. Its cost should be measured on the device; it disables itself on any capture error.
- Cross-window blur for dialogs depends on the system setting; without it dialogs use a near-opaque glass fill.
- Settings screens and some secondary activities keep the previous styling.

# Delivery 0.2.0-study · 11 November 2026

Status: **development APK, physical acceptance pending**. Pen and glass slice on top of 0.1.0-study.

## Implemented

- Floating liquid-glass pen toolbar (Read, Highlighter, Pen, Marker, Eraser, colour, options, Undo, Redo, Done) opened from a pen icon in the top bar, the actions menu or configurable shortcuts; Back closes it. Options panel with tool colours, maximum width, "only the pen writes" and "pen writes while reading".
- Pen highlighter: sweep over text → word-snapped standard /Highlight in the current highlight style (shared with the selection card), listed in Highlights.
- Pressure-sensitive pen and translucent marker as standard /Ink with a variable-width appearance stream; MJ Study redraws them as cached vector overlays without re-rendering pages.
- Whole-stroke eraser for MJ Study ink and highlights; undo/redo; journal recovery for ink (`addInk`/`deleteInk`).
- Pen routing: stylus writes, fingers navigate, pen takes over a finger gesture, palm guard while writing, historical samples, unbuffered dispatch, hover cursor, pen buttons (primary = erase while writing, either = highlight while reading), `TOOL_TYPE_ERASER` erases.
- Glass styling for the selection card, crop status card and actions menu (real backdrop blur where Android 12+ enables cross-window blur). English and Spanish strings.

## Verified in this environment

| Check | Result |
| --- | --- |
| Native JNI with new /Ink functions | Rebuilt for arm64-v8a (prebuilt PDFium profile); `nativeCreateInkAnnotation`, `nativeGetInkAnnotations`, `nativeRemoveInkAnnotation` exported |
| app JVM tests | 821 passed; 39 skipped (includes new PenSettingsStore, PenHistory and ink journal tests) |
| AndroidPdfViewer JVM tests | 319 passed; 16 skipped (includes new InkGeometry and PenInputPolicy tests) |
| PdfiumAndroid JVM tests | 31 passed; 2 skipped |
| Lint | Passed: 0 errors, 305 warnings (same count as 0.1.0; new code adds none of its own) |
| Debug APK / device-test APK | Assembled; all five arm64 .so files in the APK byte-identical to the newly built libraries |

Environment notes: the SDK/NDK had to be downloaded into the session; two inherited `BackupFolderNamingTest` cases fail when the JVM runs with an ASCII filesystem encoding (no UTF-8 locale) and pass with `LANG=C.UTF-8`; the numbers above are from the UTF-8 run. One transient Gradle failure (`extractDebugAnnotations`) passed on retry.

## Not verified / limitations

- No Xiaomi Pad 7 or Xiaomi pen and no emulator able to load the arm64 PDFium build were available. Pressure response, which pen buttons HyperOS forwards to apps, hover, palm behaviour, latency and visual polish are **untested on hardware**. The new `InkPersistenceTest` (native create → save → reopen → erase → save → reopen) is compiled, not run.
- Cross-reader rendering of the generated ink appearance (Acrobat, Xodo, pdf.js) is unverified. The appearance stream format was checked against PDFium's own `FPDFAnnot_SetAP` source for the pinned API (BBox from /Rect, `/GS` ExtGState for /CA).
- The eraser removes whole strokes/highlights, only those created by MJ Study. No partial eraser, lasso, shapes or text boxes yet.
- In-window glass is a translucent frosted fill; Android Views cannot blur content behind a view in the same window.
- Only the arm64 prebuilt PDFium profile was built (upstream Arabic patches omitted, as in 0.1.0).

# Delivery 0.1.0-study · 11 November 2026

Status: **development APK, physical acceptance pending**. This is the first custom-highlighting slice; it is not a finished tablet/stylus product.

## Implemented

Native HEX/RGB highlight editor, 1–100% opacity, preview, English/Spanish strings, current/recent/favorite presets with optional meanings, selection-card and settings integration, standard PDF annotation opacity through JNI and the existing save/recovery path. Separate app identity; original GPL attribution retained; remote crash reporting is not initialized.

Architecture, visual tokens and component rules, stylus investigation, phased roadmap, development instructions and test checklist are in docs/. Two code/build commits are followed by the validation/documentation commits listed in the supplied git bundle.

## Verified in this environment

| Check | Result |
| --- | --- |
| Original source/history imported | GitLab base 286277c6a85a4068cbbfbd18c93a742f0b2087ed |
| Native dependencies and modified JNI | Built successfully for arm64-v8a and x86_64 |
| app JVM tests | 772 passed; 39 skipped |
| AndroidPdfViewer JVM tests | 290 passed; 16 skipped |
| PdfiumAndroid JVM tests | 29 passed; 2 skipped |
| Total JVM tests | 1,091 passed; 57 skipped; zero failures/errors |
| Native dependency Python tests | 34 passed |
| Debug application APK | Assembled; debug signature verified |
| Device test APK | Assembled, including the three new native/editor tests |
| Native runtime packaging | All five required .so files per ABI checked byte-for-byte against the newly built libraries |
| Lint | Passed: zero errors; 305 warnings (see supplied report) |
| Git diff whitespace checks | Passed |

Mockito's inline mock agent was configured explicitly for JDK 21 after self-attachment failed in this environment. The highlight string file intentionally supports EN/ES initially, with English fallback for other inherited locales. Two inherited diagnostic methods now declare their intended worker-thread contract instead of inheriting View's UI-thread contract.

The environment needed a full JDK, SDK/NDK and trusted proxy settings. Native generation is separate from Gradle, so the fork now fails preBuild if a native runtime is missing. Temporary build tools and generated caches are not part of the source distribution.

## Not verified / limitations

- Android runtime tests were compiled but not successfully executed: the software-only emulator did not reach a usable test session. No physical Xiaomi Pad 7 or pen was available. Do not describe pen pressure, palm rejection, latency or saved-PDF interoperability as tested.
- Native `HighlightPersistenceTest` is supplied for create → save → reopen → recolor → save → reopen with exact ARGB assertions. `HighlightEditorTest` covers valid commit and invalid-draft recreation/cancel. Run these before promoting this build beyond development.
- This APK uses checksum-pinned prebuilt PDFium 7920. It omits the upstream Arabic patches. The source-build option is documented and available in CI.
- Full upstream instrumentation scenarios still include original palette/package assumptions. Only their compilation is verified; the suite needs adaptation before broad execution is claimed.
- Cross-reader visual compatibility, physical storage-error recovery, large-file performance, accessibility and orientation acceptance remain in the tablet checklist.
- The new editor starts from the last-used style rather than reading the tapped annotation's style. Recoloring uses the existing upstream ownership rules; it does not enable arbitrary third-party highlight editing.
- Stylus tools, freehand highlighting, undo/redo, floating tablet toolbar and advanced library features remain on the roadmap.
- CI is configured but has not run on GitHub: no matching MJ Study target repository was connected. The accessible rapidPDF repository was inspected and is a separate project, so it was not modified.

## Install and test

Install `MJ_Study_0.1.0_debug.apk` alongside MJ PDF. It contains arm64-v8a (tablet) and x86_64 (test emulator); CI's default artifact builds arm64 only. Select a phrase → **+** → set HEX/RGB, opacity and an optional meaning → **Use highlight** → save a copy. Close the app and reopen the saved copy in MJ Study and another reader. Then check preset persistence, recoloring/deletion, dark theme, rotation and invalid-input cancellation. Use docs/TESTING.md for the full checklist.

## Restore the commits and publish

The source ZIP contains the complete source tree and an incremental git bundle. To restore reviewable history:

```bash
git clone https://gitlab.com/mudlej_android/mj_pdf_reader.git mj-study
cd mj-study
git remote rename origin upstream
git fetch /path/to/mj-study.bundle study/custom-highlights:study/custom-highlights
git switch study/custom-highlights
git remote add origin YOUR_TARGET_REPOSITORY_URL
git push -u origin study/custom-highlights
```

Open a PR in the selected target repository. If it is empty, import the original base/history first rather than creating an unrelated comparison base. Do not push to the upstream author's project. No target repository has been guessed or overwritten.
