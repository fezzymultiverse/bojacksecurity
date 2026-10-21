# Security notes
- Crypto: Web Crypto API. PBKDF2-SHA256, 600k iterations, random 16-byte salt. AES-256-GCM, random 12-byte IV per file and per metadata blob. Key is non-extractable and held in memory only.
- File names, types and sizes are encrypted. The folder (Documents/Photos/Videos/Audio) is stored in plain text so the app can sort.
- Wrong password: lockout starts at the 3rd miss and doubles up to 5 minutes. Counter lives in app storage, so it slows casual guessing but is not a defence against someone copying the database offline. Password strength is the real defence there.
- Biometric unlock stores the password in Android Keystore via NativeBiometric and releases it only after a successful biometric check.
- No recovery. Forget the password and the data is unrecoverable.
- Each file is encrypted in one piece and held in memory while open, so very large videos (about 500 MB and up) may fail on low-RAM phones. Chunked streaming is on the roadmap.
- Sharing (V0.6.0): an item is encrypted with a password you choose (PBKDF2-SHA256 900k, HKDF, AES-256-GCM, random salt and IV, authenticated header) into one HTML page. File name, type and message are inside the encryption. Anyone holding the file can try passwords offline, so strength is the only defence: 10+ characters required, generator offered. Send the password by a different channel. The wrong-password delay on the page is cosmetic. Whoever you give the password to can keep the decrypted file.
- The shared page makes no network requests (strict Content-Security-Policy, no remote URLs) and never sends the password anywhere.
