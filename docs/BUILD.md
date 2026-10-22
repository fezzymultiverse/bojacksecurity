# Build and GitLab setup
1. In Termux: `pkg install nodejs git openjdk-17`, unzip the project, `cd bojack-secures`.
2. `./build.sh init` once, then `./build.sh check`. Commit `package.json`, `package-lock.json` and `capacitor.config.json`. You no longer need to commit `android/`: CI generates it (`build.sh ci-prepare`). Committing the lockfile keeps CI builds reproducible.
3. `./build.sh keystore`. Back up `fezzy-vault.jks` somewhere safe and never commit it (.gitignore already blocks it). Lose it and you can never update the app.
4. GitLab: Settings, CI/CD, Variables. Add masked variables `KEYSTORE_B64` (contents of `keystore.b64`), `KEYSTORE_PASS`, and `KEY_ALIAS` = `fezzyvault`. Delete `keystore.b64` afterwards.
5. `./build.sh assets` to make icons from `assets/icon.svg`, commit, push to `main`. The pipeline checks, builds and signs `BojackSecures-signed.apk` (download it from the job artifacts).
6. Wiki: create the project wiki once in the GitLab UI, then `./build.sh wiki https://gitlab.com/<you>/<repo>.wiki.git`.
7. Protected variables only reach protected refs: under Settings, Repository, protect the `main` branch and a `v*` tag pattern, or untick Protected on the three variables. CI fails early with a clear message if one is missing.
8. Fill in `.gitlab/CODEOWNERS` with your GitLab username.
