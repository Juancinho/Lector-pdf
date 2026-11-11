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
