# Releasing Orbit Music (self-hosted updates)

Orbit Music is distributed outside Google Play. Each release is a **GitHub Release** with two
files, and the app updates itself from them:

| File | What it is |
| --- | --- |
| `orbit-music.apk` | The signed release APK |
| `version.json` | Version, download link, SHA-256 and release notes, read by the app |

The app reads `version.json` from
`https://github.com/hiteshsuthardev/Orbit-Music/releases/latest/download/version.json`
(set as `UPDATE_JSON_URL` in `local.properties`). That link always points at the newest
release, so publishing a release is all it takes to roll out an update.

---

## One-time setup

### 1. Release signing key
A release keystore has been created at `keystore/orbit-music-release.jks`. Its passwords and
alias are in `local.properties` (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). Both are git-ignored.

> **Back up the keystore file and the four `RELEASE_*` values somewhere safe (e.g. a
> password manager).** Every future update must be signed with this same key. If it is lost,
> installed apps can never be updated again; users would have to uninstall and reinstall.

### 2. Google sign-in for release builds
Google only accepts sign-in from apps whose signing certificate is registered. In Google Cloud
Console → *APIs & Services → Credentials*, add another **Android** OAuth client for
`com.example.orbitmusic` with the release SHA-1:

```
69:41:D1:F1:F7:2F:A1:1C:56:A5:05:9A:48:0E:95:76:6A:39:42:0A
```

(You can print it again with
`keytool -list -v -keystore keystore/orbit-music-release.jks -alias orbit-music`.)

### 3. Orbit AI in release builds
Release builds never contain the Groq key. They call a Cloudflare Worker (`proxy/orbit-ai-worker.js`,
deployed as `orbit-ai`) that adds the key and forwards the request to Groq.

- `local.properties`: `AI_PROXY_URL` = the worker's `.workers.dev` link, `AI_PROXY_TOKEN` = the app token.
- Cloudflare → `orbit-ai` → Settings → Variables and Secrets: `GROQ_API_KEY` (Groq key) and
  `ORBIT_APP_TOKEN` (same value as `AI_PROXY_TOKEN`).
- To change the worker code, paste the file again in Cloudflare → *Edit code* → *Deploy*.

The token ships inside the APK, so it only keeps casual callers out; per-user limits are in the app.
Without `AI_PROXY_URL`, Orbit AI shows "not set up" in release builds.

### 4. Moving from a debug install
A release APK can't update an app installed from a **debug** APK (different signing keys).
Uninstall the debug build once before installing the first release.

---

## Making a release

### 1. Bump the version
Follow the versioning rule in `version.properties`:

```bash
./gradlew bumpVersion -Pbump=patch   # bug fixes only
./gradlew bumpVersion -Pbump=minor   # new features / changes
./gradlew bumpVersion -Pbump=major   # big redesign / breaking change
```

### 2. Build the signed APK and version.json

```bash
./gradlew prepareRelease -Pnotes="• What changed for users
• Another improvement"
```

Optional flags:
- `-PminVersionCode=N`: versions below `N` see a blocking **Update required** screen.
- `-PforceUpdate=true`: everyone on an older version must update.

The task prints the SHA-256 and writes both files to `app/build/release-upload/`:
`orbit-music.apk` and `version.json`. The SHA-256 inside `version.json` is computed from that
exact APK; the app refuses to install anything that doesn't match.

### 3. Publish the GitHub Release
Create a release whose **tag is `v<versionName>`** (e.g. `v1.6.0`), mark it as the latest
release, and attach **both** files from `app/build/release-upload/`.

With the GitHub website: *Releases → Draft a new release → Choose a tag `v1.6.0` → attach the
two files → Publish release*.

With the GitHub CLI:

```bash
gh release create v1.6.0 app/build/release-upload/orbit-music.apk app/build/release-upload/version.json \
  --title "Orbit Music 1.6.0" --notes "What changed for users"
```

Then commit and push `version.properties` so the next bump starts from the right number.

### 4. Check it
Open the app on a phone with the previous version and tap **Profile → Check for updates**.

---

## How the app updates itself

- Checks `version.json` when the app opens and when it comes back to the foreground, at most
  once every 15 minutes, plus whenever the user taps **Check for updates**. Failures are ignored.
- **Optional update:** a dialog with the version and notes, offered at most once a day per
  version, with **Update now**, **Later** and **Skip this version**.
- **Required update** (`forceUpdate`, or the installed version is below `minVersionCode`): a
  blocking screen that only offers updating.
- Downloads over HTTPS only, with progress and **Cancel download**. Checks free storage first,
  then verifies the SHA-256 before installing. A mismatching file is deleted.
- The first time, Android asks the user to allow installs from Orbit Music; the app explains
  why and opens the right settings screen.
- If the user backs out of the installer, the app offers **Install** again. Downloaded APKs
  are cleaned up once the new version is running.

---

## Remote config (announcements and switches)

`updates/remote-config.json` on the `main` branch is read from
`https://raw.githubusercontent.com/hiteshsuthardev/Orbit-Music/main/updates/remote-config.json`
(`REMOTE_CONFIG_URL` in `local.properties`). Edit and push it to change the app without a
release:

```json
{
  "announcement_text": "New: ask Orbit AI for a playlist from the middle button!",
  "feature_ai_enabled": true,
  "ai_model": ""
}
```

| Key | Effect |
| --- | --- |
| `announcement_text` | Shown as a closable banner on Home (empty = none). A new text shows again for everyone. |
| `feature_ai_enabled` | `false` hides the Orbit AI button. |
| `ai_model` | Overrides the AI model name (empty = use the built-in one). |

The app caches the last good copy and uses it offline. The file is public: **never put API
keys, passwords or other secrets in it.**
