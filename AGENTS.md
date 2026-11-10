# MJ Study development

- Product: a native, offline academic PDF reader for Android, forked from MJ PDF under GPLv3-or-later. Keep upstream attribution and licenses.
- Preserve Kotlin + Android Views, PDFView and PDFium. No broad Compose migration, server dependency, telemetry, ads or mandatory accounts.
- Main physical target: Xiaomi Pad 7 + Xiaomi pen. Hardware pressure, buttons and palm rejection require physical verification; never claim them tested from a build.
- Preserve `com.gitlab.mudlej.MjPdfReader` source namespace; installed ID is `io.github.juancinho.mjstudy`. FileProvider authorities must derive from applicationId.
- Keep render, annotation domain, UI and SAF persistence separate. Work in `study/` for new isolated components, adapt existing paths narrowly.
- Standard PDF annotations are authoritative. A preference, journal or database must not be the only copy of document annotations. Never rasterize ink as the default export.
- Use existing staged save/recovery path. Test safe failure and save/reopen after native annotation changes.
- Read docs/DESIGN_SYSTEM.md before new UI. Restrained neutral surfaces, 48dp touch targets, localized strings, accessible labels, light/dark parity. Astryx is a reference, not a dependency.
- Follow docs/ROADMAP.md. Finish small functional slices; do not label planned features implemented.
- Check `./gradlew :app:testDebugUnitTest :AndroidPdfViewer:android-pdf-viewer:testDebugUnitTest :PdfiumAndroid:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` after building native libraries. Record environmental blockers and existing failures honestly.
- Native builds: `ANDROID_NDK=... python3 build_dependencies.py --pdfium prebuilt --abi arm64-v8a` explicitly omits upstream Arabic patches. Source builds retain the patches and are the default upstream path. Do not silently switch between modes.
- Debug APK must contain `libjniPdfium.so`, PDFium and its dependent .so files. An APK that assembles without them is not a functional delivery.
- Commits/PRs must be scoped and reviewable. No generated outputs, SDK, local.properties, credentials, signing keys or development PDFs in git.
