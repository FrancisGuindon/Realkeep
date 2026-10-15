# ReelKeep

Save public Instagram Reels, YouTube videos, and Shorts shared from Android to
`Movies/ReelKeep`. Supported links are canonicalized to remove tracking
parameters. No login or account cookies are used.

Version 0.4.0 supports Android 10/API 29 or newer, arm64-v8a and x86_64. It
remains experimental until the device checks below are completed.

## Download behavior

Both yt-dlp and FFmpeg initialize before a download or update; initialization
failures can be retried. The APK includes the verified official yt-dlp
2026.08.19 bundle, including EJS scripts. QuickJS comes from the pinned
`youtubedl-android:0.18.1`. An APK upgrade preserves a newer manually updated
engine. The updater reports the actual engine version.

YouTube prefers H.264 MP4 plus M4A audio, then a combined H.264 MP4, followed
by other available adaptive/combined formats. FFmpeg copies streams without
transcoding; fallback codec support depends on the device. Ongoing live and
upcoming streams are rejected. Sign-in-only videos can fail.

Only the single file in yt-dlp's `after_move` manifest can be published. Empty,
missing, intermediate, or unexpected output paths are rejected. Video and
expected audio tracks are checked before Gallery publication. Copy and publish
failures remove the pending MediaStore row, including a zero-row update.
Missing FFmpeg is a hard error rather than a warning with a zero exit status.

One process-owned worker retains the job across rotation without replaying the
share. Another share while busy fills the next link in the box. Tap Download
when the current job finishes. Cancellation is available for downloads. This
version has no foreground service or resume after process death; retaining a
job across Activity recreation does not guarantee background process survival.

## Build and verify

Use Java 17, Python 3, Android SDK platform 35, NDK 28.2.13676358 and CMake
3.22.1. The Gradle 8.9 wrapper pins its distribution checksum. A first build
fetches and verifies these exact upstream source bundles:

| Input | Pinned identity | SHA-256 |
| --- | --- | --- |
| Official yt-dlp | 2026.08.19 | `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6` |
| libwebp 1.4.0 source | `845d5476a866141ba35ac133f856fa62f0b7445f` | `746aa87589ca248d7dd9755fd3aad5368a8e26531e5e85636da8399f13885e7a` |

`tools/rebuild-webp.sh` builds five shared libraries per ABI for Android API29
with 16 KB ELF LOAD alignment, strips the outputs, records their checksums,
and includes the upstream BSD license in APK assets. `prepare_ffmpeg.py`
validates architecture/alignment and replaces exactly ten entries inside the
pinned FFmpeg AAR's nested payloads. Other FFmpeg/Python files stay unchanged.
No unverified prebuilts are stored in this repository.

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
python3 tools/test_download_fixture.py  # host ffmpeg + ffprobe required
python3 tools/check_native_alignment.py app/build/outputs/apk/debug/app-debug.apk
```

The fixture reads the app's Java options, generates H.264/AAC streams and
verifies one completed MP4 with both tracks. An unusable FFmpeg must fail
without a completed-file manifest. The native checker recursively scans the
APK and nested payloads. These host checks do not run Android native code.

## Release signing

Keep one private PKCS12 release key (alias `reelkeep`) and reuse it for every
release. Never commit the key or password. A signed release requires:

```sh
export REELKEEP_STORE_FILE=/private/path/reelkeep-release.p12
export REELKEEP_STORE_PASSWORD='your-keystore-password'
./gradlew --no-daemon assembleRelease
```

`REELKEEP_KEY_ALIAS` and `REELKEEP_KEY_PASSWORD` override the defaults. An
ordinary release task fails when signing credentials are absent. PR CI uses
an explicit `-PunsignedReleaseForVerification` flag to build a non-debuggable
unsigned APK for inspection and subsequent signing. This artifact cannot be
installed until signed.

To sign automatically in GitHub Actions, configure repository secrets
`REELKEEP_KEYSTORE_BASE64` and `REELKEEP_STORE_PASSWORD` from the same saved
release key. Tag `v*` or run the workflow with `signed_release=true`. It checks
signature, ZIP/native alignment, records the certificate/checksum and stores
a versioned signed artifact. It does not publish a stable release automatically.

Old 0.2/0.3 CI APKs have different ephemeral Android Debug certificates. Those
private keys are unavailable here. Installing the new release-key APK may
require uninstalling the experimental app once. Subsequent releases must use
the saved key. Gallery exports are separate from private temporary app data.

## Device checks before a stable release

- Fresh install/data: share public YouTube watch + Short links without tapping
  Update; play the results in Gallery with sound. Check Instagram too.
- Rotate during a slow download: one job and one Gallery file. Test another
  share, cancellation, network loss, restricted videos and low storage.
- Test download → update, update → download, backgrounding and screen locking.
- Execute a real adaptive merge on ordinary arm64 Android and a 16 KB device
  with compatibility mode disabled; `adb shell getconf PAGESIZE` must be `16384`.
- Check fallback codecs on the oldest supported phone.
- Build two successive version codes with the saved key, compare fingerprints
  with `apksigner verify --print-certs`, and confirm `adb install -r` succeeds.
- Publish only the tested signed APK, recording source commit, checksum,
  signing fingerprint, app version and downloader version.
