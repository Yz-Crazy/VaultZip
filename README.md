# VaultZip

[中文说明](README.zh-CN.md)

VaultZip is an Android archive utility focused on extraction, preview, split-volume support, and a minimal ZIP compression workflow.

## Features

### Extraction
- Open and extract common archive formats
- Current native/archive pipeline includes support for:
  - RAR
  - ZIP / ZIPX
  - 7Z
  - TAR
  - TGZ / GZ
- Split-volume detection and handling
- Password prompt flow for encrypted archives
- Extract the whole archive or a single entry

### Preview
- Preview text files
- Preview images with large-image support
- Preview PDF files with page navigation for the first 10 pages

### Compression
- Separate Compress tab in the main UI
- Select multiple files via SAF
- Create a standard `.zip` archive
- Choose output location with `ACTION_CREATE_DOCUMENT`
- Show progress and status while compressing

## Tech Stack

- Kotlin
- Android Views + Fragments
- MVVM
- Hilt
- WorkManager
- JNI / NDK / CMake
- 7-Zip sources
- UnRAR sources
- SAF (`content://` URIs)

## Project Structure

- `app/src/main/java/com/vaultzip/ui/main` — main screen and top-level navigation
- `app/src/main/java/com/vaultzip/ui/extract` — extraction UI
- `app/src/main/java/com/vaultzip/ui/compress` — ZIP compression UI
- `app/src/main/java/com/vaultzip/ui/preview` — preview activity and viewers
- `app/src/main/java/com/vaultzip/archive` — archive domain, repository, bridge, models
- `app/src/main/java/com/vaultzip/compress` — compression repository and models
- `app/src/main/cpp` — JNI and native archive routing
- `third_party/7zip` — vendored 7-Zip sources
- `third_party/unrar` — vendored UnRAR sources

## Requirements

- Android Studio / Android SDK
- JDK 17
- Android NDK `28.0.13004108`
- `compileSdk 35`
- `minSdk 26`

## Build

If your environment already has Gradle available:

```bash
gradle -p . :app:assembleDebug
```

If you use a local bundled Gradle distribution, point to that binary instead.

The project currently does not include a checked-in Gradle Wrapper, so you need either:
- a local Gradle installation, or
- a local Gradle distribution path

## Run

Install the generated debug APK from:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Current Scope

Implemented:
- archive extraction
- split-volume handling
- password prompt flow
- file preview
- ZIP compression MVP

Not implemented yet:
- 7z archive creation
- encrypted ZIP creation
- split-volume archive creation
- advanced compression options

## Notes

- The extraction path uses native archive backends.
- The current compression MVP is implemented with `ZipOutputStream` on the Kotlin side.
- SAF is used so the app can work with `content://` files selected from the system file picker.
