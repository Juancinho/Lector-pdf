# Delivery 0.2.0-study · 11 November 2026

Status: development handwriting build. Physical Xiaomi acceptance remains pending. Repository: https://github.com/Juancinho/Lector-pdf.

## Implemented

Native floating/collapsible toolbar; read/write modes; pen, softer pencil, transparent freehand marker, line/arrow/rectangle/ellipse; configurable HEX color/nib width/pressure preference; whole-stroke eraser; lasso selection, move and uniform scale; bounded per-document undo/redo. Finger navigation keeps the upstream gesture listener. Ink is PDF-space vector geometry, saved as standard printable Ink annotations with appearance streams and optional embedded editing metadata. Existing staged save/recovery and settings backup are reused.

Pen-down now stops page fling/snap motion, cancels a finger gesture that the pen takes over and forwards down/up to auto-scroll. Invalid HEX/RGB drafts, opacity and meaning also survive editor recreation without writing preferences on Cancel.

Custom text highlights from 0.1 remain available. Original PDFium renderer, GPL attribution, offline operation and distinct installed ID are preserved. See STYLUS.md for exact limits; advanced library/page tools and the full requested roadmap are not complete.

## Verification

- Native JNI built for arm64-v8a and x86_64; exported ink JNI symbols checked.
- 1,104 JVM tests passed, 57 skipped, zero failures/errors (785 app, 290 viewer, 29 native bridge).
- Debug APK and instrumentation APK assembled. Lint passed: zero errors, 307 warnings (including inherited warnings). APK signature verified; all five native libraries per ABI match the newly built binaries byte-for-byte.
- Current CI arm64 debug APK SHA-256: `5a18f35c016e2b0453e20dc6f858fc69b4f1e7bef9929d1980afe3f4bc5953d9`. It contains all five required native libraries. CI and locally produced debug builds may use different signing keys.
- GitHub Actions passes the complete JVM/lint/APK job and the native Android integration job on the PR.
- Six targeted study tests pass on an accelerated API 30 x86_64 emulator: highlight persistence/recolor, invalid-draft recreation, native ink create/save/reopen/removal, pressure and marker-opacity appearance, and synthetic finger/pen routing after zoom/scroll. Pen-down stopping page inertia is covered. These tests use synthetic input and do not establish Xiaomi hardware behavior.
- No physical tablet/pen, real pressure range, palm rejection, latency, cross-reader interoperability or large-PDF stress test has been verified here.
- PDFium prebuilt profile is pinned to 7920 and omits upstream Arabic patches. Source mode remains available.

The ink appearance alpha initially differed from annotation `/CA`: a host PDFium 8076/MuPDF probe showed opaque strokes despite `/CA 0.8`. Both engines render the expected blended pixel after carrying alpha into the appearance path itself (PDFium RGB 71/97/151, MuPDF 70/97/151 over white for RGB 25/58/126 at 80%). JNI now applies the same correction to filled pressure outlines and constant-width paths. This host API probe is not an execution of Android JNI/PDFium 7920; Android integration and physical checks remain required.

## Tablet smoke test

1. Open a PDF copy, tap Write and use each nib/shape. Confirm fingers pan/pinch and pen writes.
2. Vary pressure; try button/eraser if exposed. Rest/lift palm before, during and after strokes. Record unexpected page movement or ink.
3. Zoom, scroll, rotate and crop; check alignment. Test portrait/landscape and dark mode.
4. Erase a stroke, undo/redo; lasso several strokes, move and resize via upper-right handle. Make a new edit after undo and confirm redo clears.
5. Save a new PDF, close, reopen, inspect in another reader and print preview. Reopen in MJ Study and lasso/erase owned ink.
6. Recover unsaved edits, rotate again, check undo session behavior and original-file preservation.

Earlier highlighting delivery details: DELIVERY_0.1.0.md (historical snapshot, not current repository status).
