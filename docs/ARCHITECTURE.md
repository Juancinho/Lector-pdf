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

## Storage and safety

Retain SAF/content-URI support, existing staged save, unsaved-change gates and recovery flow. The base also has broad-storage library modes; replacing those with a redesigned SAF-first library is phase 4, not completed here. New highlight functionality requests no storage permission and writes through the existing save pipeline.

Do not add direct overwrite code to the picker. Native annotation edits may still execute in the existing synchronous path; moving that shared, synchronized PDFium interaction off the UI thread needs a separate measured change.

## Identity and privacy

Install ID `io.github.juancinho.mjstudy`, version `0.1.0-study`, launcher name MJ Study; source namespace retained to avoid a cross-project package refactor. FileProvider uses `${applicationId}`. Upstream attribution retained. Remote crash reporter initialization and backend configuration removed; dynamic colors disabled for a stable new visual identity. Existing optional upstream network document/link/translation features remain; no account is required for local reading or annotation. Full removal of unused ACRA dependencies can be a separate cleanup.

## Build profiles

First debug delivery uses upstream's explicit checksum-pinned prebuilt PDFium 7920 arm64 profile plus a newly compiled JNI bridge. It omits upstream Arabic source patches; this limitation must accompany the artifact. `--pdfium from-source` preserves those patches but requires depot_tools/GN and a longer build. CI offers either profile explicitly. Never package old JNI after signature changes.

## Ink slice

`study/ink` adds immutable PDF-space strokes, bounded command history, strict metadata codec and rollback-capable patch mutation. The native reader interceptor preserves the original gesture listener. A draw-only overlay previews active input; standard PDFium Ink annotations render committed strokes. Existing render fences, staged saves and annotation journaling are reused. `InkPatch` stores serialized before/after strokes for idempotent recovery. Read STYLUS.md for current cache/sample limits, synchronous pen-up mutation and hardware validation boundaries.
