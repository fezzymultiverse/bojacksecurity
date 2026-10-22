# Bojack Secures wiki
## Using the vault
1. First launch: create a password (8+ characters, no recovery).
2. Tap + then Choose files. Copy keeps originals. Move lists the originals for you to delete (Android blocks apps from deleting picked files).
3. Tap a file to view. Restore to phone saves a decrypted copy to Documents/BojackSecures.
4. Settings: biometrics, auto-lock, screenshots, auto-delete of restored files, backup, About, Privacy, Feedback, Reset. Notes tab keeps SSH keys, API keys and phone numbers encrypted.
## Building
See README. CI variables: KEYSTORE_B64, KEYSTORE_PASS, KEY_ALIAS.
## Security
See the Security page.
## Syncthing
Turn on Syncthing: mirror in Settings, then share Documents/BojackSecures/Sync from Syncthing. Only encrypted .bjv files and vault.meta.bjv are written. On a new device: install, tap Restore from sync folder, pick all the .bjv files, then unlock with the original password.
