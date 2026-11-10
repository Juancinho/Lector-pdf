# Digital ink: investigation and next implementation

Status: design/investigation only. The first delivery changes text highlighting, not pen input.

Upstream SignatureView captures generic touch points, decimates input and converts it into cubic strokes. SignatureData keeps normalized vector paths. SignatureController places these with PDF coordinates, but nativeAddSignatureContent inserts page path content; it does not create editable PDF /Ink annotations. Keep it for signatures and reuse only appropriate math.

Before phase 3, implement a dedicated input surface that distinguishes TOOL_TYPE_STYLUS and TOOL_TYPE_ERASER from fingers. Track the active pointer ID, historical/coalesced samples, pressure and tool buttons. Finger gestures route to PDFView; pen ink routes to the overlay. Palm rejection must be a documented state machine including CANCEL, pointer transitions and pen proximity when exposed, not a tool-type heuristic presented as a guarantee.

Coordinate contract: capture per-page points in PDF user space by inverting the actual page-to-view matrix, including zoom, page origin, scroll, crop and rotation. Define page ownership at stroke start; test all four rotations and page boundaries. Reproject PDF points each frame, never retain screen coordinates as authoritative.

Model strokes as immutable vector data with point pressure/time and a tool profile. Maintain command-based undo/redo with explicit memory limits and checkpoint recovery. Write standard /Ink annotations with appearance streams where supported; pressure-dependent rendering may require a matching standard appearance path. Sidecars may cache richer samples but cannot be the only representation.

Investigate the bundled fpdf_annot.h APIs (FPDFPage_CreateAnnot, FPDFAnnot_AddInkStroke, appearance-object support) against the exact pinned PDFium revision before adding JNI. Test create/save/reopen, independent renderer compatibility, erasing, transformations and write-failure rollback.

Xiaomi Pad 7 verification must record model, Android/HyperOS, pen model, reported InputDevice motion ranges, observed pressure values, tool IDs, button events and latency. Do not assume a pressure range, eraser button, hover capability or hardware palm rejection from marketing names. No such physical verification has been performed in this session.
