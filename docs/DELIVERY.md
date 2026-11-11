# Delivery 0.2.0-study · 11 November 2026

Status: development handwriting build. Physical Xiaomi acceptance remains pending. Repository: https://github.com/Juancinho/Lector-pdf.

## Implemented

Native floating/collapsible toolbar; read/write modes; pen, softer pencil, transparent freehand marker, line/arrow/rectangle/ellipse; configurable HEX color/nib width/pressure preference; whole-stroke eraser; lasso selection, move and uniform scale; bounded per-document undo/redo. Finger navigation keeps the upstream gesture listener. Ink is PDF-space vector geometry, saved as standard printable Ink annotations with appearance streams and optional embedded editing metadata. Existing staged save/recovery and settings backup are reused.

Custom text highlights from 0.1 remain available. Original PDFium renderer, GPL attribution, offline operation and distinct installed ID are preserved. See STYLUS.md for exact limits; advanced library/page tools and the full requested roadmap are not complete.

## Verification

- Native JNI built for arm64-v8a and x86_64; exported ink JNI symbols checked.
- 1,104 JVM tests passed, 57 skipped, zero failures/errors (785 app, 290 viewer, 29 native bridge).
- Debug APK and instrumentation APK assembled. Lint passed: zero errors, 307 warnings (including inherited warnings). APK signature verified; all five native libraries per ABI match the newly built binaries byte-for-byte.
- Diagnostics/stability APK SHA-256: `d236f9a2aff64212b3977843fa3707d7a63f4ac7463c7a3d99b8e338616e2221`. Local debug signing differs from CI signing.
- GitHub has also compiled the ink APK and passed JVM/lint on the PR. The initial run then failed while hashing generated native files for its post-build cache; cache inputs have been narrowed to source files. APK artifact was uploaded successfully.
- Android integration has not passed: the first emulator boot failed and its diagnostic adb command waited for a device. The workflow now bounds both waits, emits emulator logs and uses the current SwiftShader backend. A fresh run must pass before acceptance.
- No physical tablet/pen, real pressure range, palm rejection, latency, cross-reader interoperability or large-PDF stress test has been verified here.
- PDFium prebuilt profile is pinned to 7920 and omits upstream Arabic patches. Source mode remains available.

## Tablet smoke test

1. Open a PDF copy, tap Write and use each nib/shape. Confirm fingers pan/pinch and pen writes.
2. Vary pressure; try button/eraser if exposed. Rest/lift palm before, during and after strokes. Record unexpected page movement or ink.
3. Zoom, scroll, rotate and crop; check alignment. Test portrait/landscape and dark mode.
4. Erase a stroke, undo/redo; lasso several strokes, move and resize via upper-right handle. Make a new edit after undo and confirm redo clears.
5. Save a new PDF, close, reopen, inspect in another reader and print preview. Reopen in MJ Study and lasso/erase owned ink.
6. Recover unsaved edits, rotate again, check undo session behavior and original-file preservation.

Earlier highlighting delivery details: DELIVERY_0.1.0.md (historical snapshot, not current repository status).
