# Releasing
1. Update `CHANGELOG.md`, bump `VER` in `www/index.html` and `version` in `package.json`.
2. Commit, then tag: `git tag v0.6.0 && git push --tags`.
3. The pipeline builds the signed APK and creates a GitLab Release with the download link.
