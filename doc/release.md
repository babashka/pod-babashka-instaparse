# Release procedure

## 1. Bump version

Update the version in `resources/POD_BABASHKA_INSTAPARSE_VERSION` and add a changelog entry in `CHANGELOG.md`.

## 2. Wait for CI

Push to main and wait for all CI jobs to complete:
- **CircleCI**: linux-amd64, linux-aarch64, mac-aarch64 (builds + uploads draft release assets)
- **GitHub Actions**: windows-amd64 (builds + uploads draft release asset)

## 3. Publish the release

Once all assets are uploaded, manually publish the draft release on GitHub.

## 4. Update pod registry

In `~/dev/pod-registry`:

```bash
# Generate new manifest from previous version
bb script/upgrade-manifest.clj org.babashka/instaparse <new-version>

# Review and edit the manifest if needed (e.g. removing dropped platforms)
# Edit manifests/org.babashka/instaparse/<new-version>/manifest.edn

# Update the README table
bb script/derive-manifests.clj

# Update the example version
# Edit examples/instaparse.clj

# Verify the example works
bb examples/instaparse.clj

# Commit and push
git add manifests/org.babashka/instaparse/<new-version> README.md examples/instaparse.clj
git commit -m "Add org.babashka/instaparse <new-version>"
git push
```
