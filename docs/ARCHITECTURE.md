# Architecture

## Imported baseline

Source: https://gitlab.com/mudlej_android/mj_pdf_reader at commit 286277c (full SHA in git history). Original modules and licenses are preserved: app (Kotlin Views/Material 3, Room and preferences), AndroidPdfViewer/android-pdf-viewer (Java PDFView, page caches, selection and coordinate conversion), PdfiumAndroid (Java/JNI/C++ PDFium bridge), macrobenchmark.

The upstream already has grouped text-highlight annotations, recoloring/deletion, notes, a highlight list, signature capture and a staged save/recovery journal. Reuse those facilities. The signature canvas captures points into smoothed paths but accepts generic touch, has a fixed width and exports signature paths into page content. It is not a pressure-sensitive, editable /Ink annotation engine.

## First implementation slice

- `study/annotation/HighlightStyle`: Android-independent validated RGB + percent opacity + optional user label. ARGB transports style through existing annotation edits and the journal. All 1–100% opacities round-trip through 8-bit alpha.
- `HighlightStyleStore`: local current style and bounded recent/favorite lists in versioned SharedPreferences keys; malformed entries are ignored individually. Keys are registered with the existing backup preference whitelist.
- `study/ui/HighlightStyleDialog`: native DialogFragment, isolated draft, preview, HEX/RGB validation, opacity, favorites and recent styles. A fragment result applies through the existing InlineAnnotationActionController. Saved draft survives recreation.
- Selection card and settings expose the editor. No new render engine or Compose runtime.
- JNI creation/recolor writes RGB and constant opacity via FPDFAnnot_SetColor. Native read preserves alpha instead of forcing 255. PDFView uses that alpha for overlays. Upstream save temporarily clears app-only HIDDEN flags before writing standard /Highlight annotations, then restores render state.

Preferences do not store document geometry or replace PDF annotations. Color meanings currently label personal presets; they are not exported into the PDF. Existing edit journal carries the complete ARGB integer, so recovery preserves opacity without a second format.

## Pen slice (0.2.0-study)

- `AndroidPdfViewer` owns input and drawing, never persistence: `PenInputManager` routes stylus events (tool type, buttons, historical samples, unbuffered dispatch, hover, palm guard); `PenInputPolicy` holds the pure routing rules; `TextSelectionManager` gained a caret-to-caret word-snapped "sweep" that never notifies selection listeners; `InkGeometry` builds the pressure outline, the appearance stream and the pressure sidecar codec; `InkOverlayRenderer` draws cached outlines through each page's PDF→document affine transform (`PdfFile.pdfToDocumentAffine`), so zoom/scroll never rebuild geometry.
- `PdfiumAndroid`: `nativeCreateInkAnnotation`/`nativeGetInkAnnotations`/`nativeRemoveInkAnnotation`. Ink is written as a standard `/Ink` annotation: `/InkList` centreline, `/C` + `/CA`, `/Border` width, generated `/AP` with the variable-width outline (`/GS` ExtGState for opacity), `MJGroup`/`NM`/`T`/`CreationDate`, and `MJPressure` (per-point pressure) inside the same annotation. App-owned ink shares the app highlights' hidden-while-rendering/visible-on-save rule, so the viewer draws it as an overlay with no page re-render.
- `app/study/pen`: `StudyPenController` turns pen results into `AnnotationEdit`s (`Add`, `AddInk`, `Delete`, `DeleteInk`, `SetNote`) applied through `AnnotationController.applyLive` and recorded through the normal journal path; `PenHistory` keeps bounded undo/redo as forward/backward edit lists; `PenSettings(Store)` is one versioned preference key (in the backup whitelist) and never stores document data.
- `app/study/ui`: `StudyPenToolbar` (floating glass toolbar + options panel) and `GlassSurfaces` (translucent surfaces; real cross-window blur only for separate windows).

## Storage and safety

Retain SAF/content-URI support, existing staged save, unsaved-change gates and recovery flow. The base also has broad-storage library modes; replacing those with a redesigned SAF-first library is phase 4, not completed here. New highlight functionality requests no storage permission and writes through the existing save pipeline.

Do not add direct overwrite code to the picker. Native annotation edits may still execute in the existing synchronous path; moving that shared, synchronized PDFium interaction off the UI thread needs a separate measured change.

## Identity and privacy

Install ID `io.github.juancinho.mjstudy`, version `0.2.0-study`, launcher name MJ Study; source namespace retained to avoid a cross-project package refactor. FileProvider uses `${applicationId}`. Upstream attribution retained. Remote crash reporter initialization and backend configuration removed; dynamic colors disabled for a stable new visual identity. Existing optional upstream network document/link/translation features remain; no account is required for local reading or annotation. Full removal of unused ACRA dependencies can be a separate cleanup.

## Build profiles

First debug delivery uses upstream's explicit checksum-pinned prebuilt PDFium 7920 arm64 profile plus a newly compiled JNI bridge. It omits upstream Arabic source patches; this limitation must accompany the artifact. `--pdfium from-source` preserves those patches but requires depot_tools/GN and a longer build. CI offers either profile explicitly. Never package old JNI after signature changes.
