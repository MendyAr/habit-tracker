# Releasing

Releases are automated by [`.github/workflows/release.yml`](../.github/workflows/release.yml).

## Cutting a release

1. Bump `VERSION_NAME` (semantic version) and `VERSION_CODE` (+1) in
   [`gradle.properties`](../gradle.properties).
2. Add a `## [x.y.z] - YYYY-MM-DD` section to [`CHANGELOG.md`](../CHANGELOG.md).
   Its contents become the release notes.
3. Merge to the default branch.

When CI succeeds on the default branch and no `v<VERSION_NAME>` tag exists yet,
the release workflow:

- runs the unit tests and builds the minified release APK,
- verifies its signature with `apksigner`,
- creates the tag `v<VERSION_NAME>` and a GitHub release with
  `HabitTracker-v<VERSION_NAME>.apk` and its SHA-256 checksum.

Re-running the workflow for an already released version does nothing.
It can also be started by hand (*Actions → Release → Run workflow*).

Every CI run also uploads its APKs as a build artifact
(`apk-build-<run number>`), which is handy for testing unreleased changes.

## Signing

Android only installs an update over an existing app when both are signed with
the same certificate, so every release must use the same key.

**Default: the public sideload key.** [`keystore/sideload.jks`](../keystore) and
its passwords ([`keystore/signing.properties`](../keystore/signing.properties))
are committed on purpose. Every APK built from this repository, by CI or by
anyone, is then signed the same way and can update the previous one. The
trade-off is that the key is public, so the signature only proves "built from
this repository's key", not "built by the maintainer". That is fine for
sideloaded personal builds, not for a store listing.

**Private key (recommended for wider distribution).** Create one with

```sh
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias habittracker \
  -keyalg RSA -keysize 3072 -validity 11000
```

and add these repository secrets (*Settings → Secrets and variables → Actions*):

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `habittracker` (or your alias) |
| `SIGNING_KEY_PASSWORD` | key password |

When present they take precedence over the sideload key. For local builds, the
same values can be given as the environment variables `SIGNING_STORE_FILE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

Switching keys means users must uninstall the old build once (losing its data,
unless restored from an Android backup) before installing one signed with the new key.
