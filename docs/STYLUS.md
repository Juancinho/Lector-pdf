# Digital ink · 0.2 development

## Implemented path

The reader now has a dedicated pen interceptor and a draw-only Android View above PDFView. It preserves DragPinchManager's original touch listener. In Write mode, TOOL_TYPE_STYLUS/ERASER owns a stroke; fingers continue through the existing pan/pinch path. A pen joining an existing finger gesture sends CANCEL to navigation. Additional fingers/palms are swallowed while the pen is active and until remaining pointers lift. ACTION_CANCEL discards the unfinished gesture. Read mode leaves normal reader behavior intact.

This is application-level routing, not a guarantee of hardware palm rejection: a palm moving before the pen touches can still move the page. Pen proximity and Xiaomi-specific buttons have not been verified. BUTTON_STYLUS_PRIMARY and TOOL_TYPE_ERASER select temporary stroke erasing when Android exposes them at stroke start.

Historical MotionEvent samples are included. Pressure is normalized using the actual InputDevice range, with bounded fallback for missing/invalid ranges. A short exponential filter smooths points and pressure. PDFium writes one pressure-dependent vector outline into the annotation appearance; `/InkList` retains the centerline for other readers. The pencil currently uses a softer transparent pressure nib, without texture or tilt simulation. No latency measurement is claimed.

## Tools and coordinates

Pen, pencil, transparent freehand marker, whole-stroke eraser, lasso, line, arrow, rectangle and ellipse. Lasso selects strokes with a sampled point inside its polygon; drag the selection to move, drag its upper-right handle to scale uniformly. Rotation, arbitrary shear and segment erasing are not implemented. Eraser/lasso edit MJ Study-owned ink only; external annotations remain visible and untouched.

Stroke ownership is fixed to the page at pen-down; samples outside it are ignored. Geometry lives in PDF user space. PDFView's existing document↔PDF transforms account for current layout, zoom, offsets, crop and rotation. The preview reprojects PDF geometry; persisted data never depends on screen pixels. Native PDF rendering supplies the committed stroke after pen-up.

## Persistence and history

`InkStroke`, `InkGeometry`, `InkHistory`, `InkCodec` and `InkMutator` contain the independent model and edit logic. `InkController` handles Android input, toolbar and journal integration. PdfFile's existing render fence and PdfiumCore lock serialize native mutation against rendering. Native calls are bounded but currently run synchronously on pen-up; latency of long strokes and batches must be profiled before claiming low latency on the tablet.

Each native annotation is standard `/Subtype /Ink`, with `/InkList`, `/Rect`, `/C`, opacity, printable flag, stable `/NM` and a vector appearance stream. `MJInkData` is optional editing metadata embedded in the same PDF (including pressure and tool profile). It is not the only representation: other readers can render standard ink without it. Third-party editors may strip that metadata; then the stroke remains visible but MJ Study's lasso/eraser will not recognize it as editable owned ink. Shapes currently export as standard Ink rather than semantic Line/Square/Circle subtypes.

Transforms are remove/add patches with best-effort rollback on a failed native step. Successful patches reuse the existing annotation journal, dirty state, staged save-as-copy and recovery. Replay is idempotent by annotation identifier. Journal recovery refreshes the editing cache. Undo/redo is per-document session (survives activity rotation via ViewModel), bounded to 100 commands/200,000 referenced points; it does not survive process death. A new edit clears redo.

Limits: 4,096 samples per stroke; excess samples in an uninterrupted long stroke are ignored. Lasso transform limited to 50,000 points. Editing cache loads at most 200,000 points per page and evicts older pages above eight pages/200,000 points overall. Native metadata query caps 2,000 records/4 million UTF-16 characters and rejects individual metadata above 600,000 characters. The underlying standard PDF annotations still render even when editing metadata exceeds these limits. These are protective development bounds, not a claim of unlimited handwriting scalability.

Color, width and pressure preference are local and included in the existing settings-backup whitelist. No server or account is required. Tool favorites, tilt, hover/proximity behavior and optimized Android low-latency/front-buffer rendering remain future work.

Pen information in the ink options displays actual observed InputDevice name/source, tool IDs, declared pressure axis, observed raw pressure and button bit flags. It is in-memory only, can be copied locally, and explicitly distinguishes unobserved data from supported hardware.

## Validation gate

Unit coverage checks pressure normalization, geometry, shapes, codec, bounds, journal serialization, undo/redo order and transform rollback. `InkPersistenceTest` exercises native create/save/reopen, visible appearance and idempotent removal. `InkInputTest` injects synthetic finger/pen events and checks PDF coordinates after zoom. CI runs these with the highlight/editor integration tests on an accelerated API 30 x86_64 emulator. See DELIVERY.md for actual results; configured tests are not completed tests.

Physical acceptance must record tablet model, Android/HyperOS, pen model, reported ranges, pressure values, tool IDs and button events. Test palm contacts before/during/after pen, simultaneous fingers, cancellation, all rotations, crop, zoom/scroll, dark mode, long writing sessions and independent-reader/print compatibility. No physical Xiaomi verification has been performed here.

## Existing signatures

The upstream SignatureView captures generic touch points and cubic paths. Native signature placement inserts page content instead of editable Ink annotations. That feature is preserved separately; the new handwriting engine does not repurpose or rasterize signatures.
