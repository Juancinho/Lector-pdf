# MJ Study

Native, open-source academic PDF reading and annotation for Android. Forked from [MJ PDF by Mudlej](https://gitlab.com/mudlej_android/mj_pdf_reader), licensed under GPLv3-or-later; upstream history and third-party notices are preserved.

## First development delivery · 0.1.0-study

- PDFium rendering and the existing lightweight Kotlin/Views reader.
- Custom text-highlight color: HEX (`#RGB` / `#RRGGBB`), RGB channels, 1–100% opacity and live preview.
- Local current style, eight recent styles and eight favorites, with optional color meanings.
- Create/recolor using the selection card's **+** button; custom style also available in Settings → Highlight colors.
- Opacity carried into standard PDF highlights and retained through existing save/recovery paths.
- Separate installation ID `io.github.juancinho.mjstudy`, alongside original MJ PDF.
- No mandatory account, ads or initialized remote crash reporter. Local PDF reading and highlighting work offline.

The pen engine, redesigned tablet toolbar and advanced library are planned, not implemented. See [roadmap](docs/ROADMAP.md) and [delivery status](docs/DELIVERY.md). Physical testing on Xiaomi Pad 7 is still required.

## Try it

Install the arm64 debug APK from a successful GitHub Actions run (Actions → MJ Study Android → artifact `mj-study-arm64-debug-…`). The workflow also supplies an instrumentation-test APK. Debug builds are for testing. CI caches its debug signing key across builds while the cache exists; local builds use their own key. Switching signing keys requires reinstalling, so export PDFs/settings first. Release signing remains a future setup.

Open a PDF with selectable text, long-press/select a phrase, tap **+**, choose HEX/RGB and opacity, then **Use highlight**. Tap a saved highlight and **+** to recolor. Press the existing save control and choose a writable destination; open the saved copy in another reader to check compatibility. Meanings label personal presets; they are not embedded note text.

Tap **Write** to open the floating ink toolbar. Use the pen to write and fingers to navigate. Choose pen/pencil/freehand marker, shapes, whole-stroke eraser or lasso; drag inside a lasso selection to move it and its upper-right handle to resize. Color, nib width and pressure are in the palette button. Undo/redo belongs to the current document session. Save a PDF copy before leaving. See [ink behavior and limits](docs/STYLUS.md); Xiaomi hardware acceptance is pending.

## Build

Requires a full JDK 21, Android SDK platform 36, Build Tools 36.0.0, NDK 29.0.14206865, Python 3, requests, CMake, Ninja and the pinned Gradle wrapper. Set `sdk.dir` in an untracked local.properties or ANDROID_HOME.

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install requests cmake ninja
export ANDROID_NDK="$ANDROID_HOME/ndk/29.0.14206865"
python build_dependencies.py --pdfium prebuilt --abi arm64-v8a
./gradlew :app:assembleDebug :app:testDebugUnitTest :AndroidPdfViewer:android-pdf-viewer:testDebugUnitTest :PdfiumAndroid:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

`--pdfium prebuilt` is an explicit upstream comparison profile: checksum-pinned PDFium 7920 **without upstream Arabic patches**. The JNI bridge is always compiled from this source. To retain the Arabic patches use `--pdfium from-source` (the upstream default), with depot_tools/GN/source dependencies; see [SETUP.md](SETUP.md) and build_dependencies/patches/pdfium/README.md. Never distribute an APK without native libraries; Gradle assembly alone does not build them.

Output: `app/build/outputs/apk/debug/app-debug.apk`. First debug target is arm64-v8a, matching the Xiaomi tablet. Build additional ABIs explicitly before testing on other architectures.

## Project guides

- [Architecture](docs/ARCHITECTURE.md)
- [Design system and Astryx reference](docs/DESIGN_SYSTEM.md)
- [Digital ink engine](docs/STYLUS.md)
- [Tests and tablet checklist](docs/TESTING.md)
- [Development instructions](AGENTS.md)

Original author: Mudlej. Original project was based on work by Gokul Swaminathan; see LICENSE, OLD_LICENSE and original file headers. MJ Study is an independent fork, not an official Mudlej release.
