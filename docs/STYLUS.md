# Digital ink

Status (0.2.0-study): implemented against Android's stylus APIs and PDFium; **not verified on Xiaomi hardware**. The original investigation follows the implementation notes.

## What is implemented

- Routing: `MotionEvent.getToolType` decides. With a writing tool active and "only the pen writes" (default), `TOOL_TYPE_STYLUS` writes and fingers keep PDFView's scroll/pinch. A pen landing as a second pointer during a finger gesture cancels the navigation gesture and takes over. The pen gesture owns its pointer ID until UP/CANCEL; other pointers in that gesture are ignored.
- Palm guard (writing tools only): a finger gesture that starts while the pen is down, while it hovers (hover state older than 1.5 s is ignored), or within 400 ms of the pen lifting is ignored. This is a heuristic, not a guarantee; HyperOS may add its own palm rejection.
- Samples: all historical samples are consumed, `requestUnbufferedDispatch` delivers stylus events as they arrive, points closer than 0.75 px are merged. Pressure is normalised by the device's reported `AXIS_PRESSURE` range, clamped to 0..1 and lightly smoothed. Fingers/mouse use a fixed pressure.
- Buttons (configurable, 0.3.0): `BUTTON_STYLUS_PRIMARY` and `BUTTON_STYLUS_SECONDARY` each map to erase, select text, highlight or nothing (defaults erase / select text). A held button wins in every mode, reading included; "select text" sweeps a word-snapped selection and then shows the normal selection card. `TOOL_TYPE_ERASER` always erases. Whether the Xiaomi pen's buttons reach the app (rather than HyperOS shortcuts), and which physical button Android reports as primary, must be checked on the device.
- Coordinates: page ownership is fixed at stroke start; samples are converted to PDF user space with the same inverse transform as text selection (zoom, page origin, scroll, crop and rotation). Overlays reproject through `PdfFile.pdfToDocumentAffine` every frame.
- Storage: one standard `/Ink` annotation per stroke: `/InkList` centreline, `/C`, `/CA`, `/Border`, `/AP` normal appearance containing the pressure outline as one non-zero fill (no double darkening for translucent marker), and `MJPressure` per-point pressure inside the same annotation. The appearance is the authoritative look for other readers; MJ Study redraws from `/InkList` + `MJPressure`. Without the sidecar, width is uniform.
- Eraser: whole-stroke eraser for MJ Study ink and app highlights (third-party annotations are not touched). Undo/redo replays the inverse `AnnotationEdit`s through the journal.

## Hardware acceptance still required

Record model, HyperOS version, pen model, `InputDevice` motion ranges (pressure, tilt), observed pressure values, button states during contact and hover, palm behaviour, latency at different zooms, all four page rotations, save/reopen in another reader (Acrobat, Xodo, Chrome/pdf.js) for opaque and translucent ink.

## Original investigation

Upstream SignatureView captures generic touch points, decimates input and converts it into cubic strokes. SignatureData keeps normalized vector paths. SignatureController places these with PDF coordinates, but nativeAddSignatureContent inserts page path content; it does not create editable PDF /Ink annotations. Keep it for signatures and reuse only appropriate math.

Before phase 3, implement a dedicated input surface that distinguishes TOOL_TYPE_STYLUS and TOOL_TYPE_ERASER from fingers. Track the active pointer ID, historical/coalesced samples, pressure and tool buttons. Finger gestures route to PDFView; pen ink routes to the overlay. Palm rejection must be a documented state machine including CANCEL, pointer transitions and pen proximity when exposed, not a tool-type heuristic presented as a guarantee.

Coordinate contract: capture per-page points in PDF user space by inverting the actual page-to-view matrix, including zoom, page origin, scroll, crop and rotation. Define page ownership at stroke start; test all four rotations and page boundaries. Reproject PDF points each frame, never retain screen coordinates as authoritative.

Model strokes as immutable vector data with point pressure/time and a tool profile. Maintain command-based undo/redo with explicit memory limits and checkpoint recovery. Write standard /Ink annotations with appearance streams where supported; pressure-dependent rendering may require a matching standard appearance path. Sidecars may cache richer samples but cannot be the only representation.

Investigate the bundled fpdf_annot.h APIs (FPDFPage_CreateAnnot, FPDFAnnot_AddInkStroke, appearance-object support) against the exact pinned PDFium revision before adding JNI. Test create/save/reopen, independent renderer compatibility, erasing, transformations and write-failure rollback.

Xiaomi Pad 7 verification must record model, Android/HyperOS, pen model, reported InputDevice motion ranges, observed pressure values, tool IDs, button events and latency. Do not assume a pressure range, eraser button, hover capability or hardware palm rejection from marketing names. No such physical verification has been performed in this session.
