# Alarm Clock — About This Project

A desktop **Alarm Clock** application built with **Java 21** and **JavaFX 21**, packaged as
native installers (`.msi`, `.dmg`, `.deb`) for Windows, macOS, and Linux using Gradle and
the `org.beryx.jlink` plugin.

---

## 1. What the App Does

The app opens as a single window titled **"Alarm Clock"** with four tabs:

| Tab | Purpose |
|---|---|
| 🕐 Clock | Live digital clock display |
| ⏰ Alarm | Create, edit, and delete alarms (time, label, repeat, custom tune) |
| ⏱ Timer | Countdown timer |
| ⏱ Stopwatch | Stopwatch with lap recording |

**Alarms**
- Each alarm has a time, an optional label, a repeat flag, and an assigned tune.
- A background scheduler checks every second for due alarms and triggers the assigned
  sound when one matches the current minute.
- Alarms persist across restarts (see Data Storage below).

**Sound**
- Plays the built-in default beep, or a custom audio file the user picks (via the Sound
  Picker screen).
- MP3 playback is supported through the `mp3spi` / `tritonus-share` / `jlayer` libraries,
  registered as `javax.sound.sampled` SPI providers — the app code itself only calls the
  standard `javax.sound.sampled.AudioSystem` API.

---

## 2. How It Works (Architecture)

```
App.java (javafx.application.Application)
 └─ loads main.fxml → MainController
     ├─ clock.fxml     → ClockController
     ├─ alarm.fxml     → AlarmController ──> AlarmManager (scheduler) ──> SoundEngine
     ├─ timer.fxml     → TimerController
     └─ stopwatch.fxml → StopwatchController

alarm_setter.fxml / sound_picker.fxml → AlarmSetterController / SoundPickerController
  (dialogs used by AlarmController to add/edit alarms and pick tunes)

PersistenceManager  → saves/loads alarms to/from disk
TuneManager         → manages the list of available tunes (built-in + custom)
```

- **`App`** is the JavaFX entry point (`org.example.App`), launched via `Application.launch()`.
- **`MainController`** wires the four tab controllers together and propagates
  `shutdown()` to each when the window is closed (so the alarm scheduler thread stops
  cleanly).
- **`AlarmManager`** runs a daemon `ScheduledExecutorService` that ticks once per second,
  compares the current time (`HH:mm`) against each alarm, and fires `SoundEngine` when a
  match is found. It tracks which alarms already fired this minute to avoid repeat
  triggers.
- **`SoundEngine`** opens the selected audio file via `AudioSystem` (with MP3 decoding
  provided transparently by the bundled SPI libraries) and plays/stops it.
- **`PersistenceManager`** and **`TuneManager`** read/write plain text files under the
  user's home directory (format: `HH:mm|label|repeat|tuneId` per line for alarms).

---

## 3. Data Storage (Where Your Alarms Are Saved)

The app stores user data in a hidden folder in your home directory — **the same path on
every OS**, because it's based on the Java `user.home` system property:

| OS | Path |
|---|---|
| Windows | `C:\Users\<you>\.alarmclock\` |
| macOS | `/Users/<you>/.alarmclock/` |
| Linux | `/home/<you>/.alarmclock/` |

Inside that folder:
- `alarms.txt` — your saved alarms
- `tunes.txt` — registered custom tunes (paths to audio files you've added)

Deleting this folder resets the app to a fresh state (no alarms, default tune only).

---

## 4. Building & Running Locally

Requires **JDK 21**.

```bash
# Run the app directly
./gradlew run

# Run the test suite
./gradlew test

# Build a native installer for your current OS
./gradlew jpackage
```

The installer is produced in `app/build/jpackage/`:
- Windows → `AlarmClock-<version>.msi`
- macOS → `AlarmClock-<version>.dmg`
- Linux → `alarmclock_<version>-1_amd64.deb` (or similar, per Debian naming rules)

The `jpackage` task uses the `org.beryx.jlink` plugin to bundle a custom Java runtime
(including JavaFX modules and the audio SPI libraries) directly into the installer —
**no separate Java installation is required by end users**.

---

## 5. Deployment via GitHub Actions

`.github/workflows/build-release.yml` automates building all three installers:

- **Trigger**: pushing a tag matching `v*.*.*` (e.g. `v1.0.8`), or running the workflow
  manually (`workflow_dispatch`).
- **Build job**: runs on `windows-latest`, `macos-latest`, and `ubuntu-latest`, each
  executing `./gradlew jpackage` (via `gradlew.bat` on Windows) to produce the
  platform-specific installer.
- **Release job**: downloads all three installers and attaches them to a new GitHub
  Release named after the pushed tag, with auto-generated release notes.

### To publish a new release
```bash
git add .
git commit -m "Release notes / changes"
git tag v1.0.9
git push origin main
git push origin v1.0.9
```
Then check the **Actions** tab — once all three build jobs succeed, a new entry appears
under **Releases** with the `.msi`, `.dmg`, and `.deb` files attached.

---

## 6. Installing & Finding the App

### 🪟 Windows (`AlarmClock-<version>.msi`)
1. Double-click the `.msi` and follow the installer (you can choose the install
   directory, a Start Menu entry and Desktop shortcut are created).
2. Default install location: `C:\Program Files\AlarmClock\`
3. Executable: `C:\Program Files\AlarmClock\AlarmClock.exe`
4. Launch from the **Start Menu** ("AlarmClock") or the **Desktop shortcut**.

### 🍎 macOS (`AlarmClock-<version>.dmg`)
1. Open the `.dmg` and drag **AlarmClock** into the **Applications** folder.
2. Installed at: `/Applications/AlarmClock.app`
3. Launch via **Launchpad**, **Spotlight** (⌘+Space → "AlarmClock"), or directly from
   `/Applications`.
4. On first launch, macOS Gatekeeper may warn the app is from an unidentified developer —
   right-click → **Open** to bypass this for an unsigned build.

### 🐧 Linux (`.deb`)
1. Install with:
   ```bash
   sudo dpkg -i alarmclock_<version>-1_amd64.deb
   ```
2. Installed at: `/opt/alarmclock/`
3. Executable: `/opt/alarmclock/bin/AlarmClock`
4. A desktop entry ("AlarmClock") is added to your application menu/launcher
   (via `--linux-shortcut`); you can also run it from a terminal:
   ```bash
   /opt/alarmclock/bin/AlarmClock
   ```

### Uninstalling
- **Windows**: Settings → Apps → AlarmClock → Uninstall.
- **macOS**: drag `/Applications/AlarmClock.app` to the Trash.
- **Linux**: `sudo dpkg -r alarmclock`

Note: uninstalling does **not** delete your saved alarms in `~/.alarmclock/` — remove
that folder manually if you want a completely clean removal.

---

## 7. Troubleshooting

- **App won't start**: ensure you're on Windows 10+, macOS 10.15+, or a Debian-based
  Linux distro with glibc compatible with the bundled JDK 21 runtime.
- **No sound**: check system volume/output device; confirm the selected tune file still
  exists at the path stored in `~/.alarmclock/tunes.txt`.
- **Alarms not firing**: the app must be running (not just installed) for alarms to
  trigger — there is currently no background/tray service.

---

**Tech stack**: Java 21 · JavaFX 21 · Gradle · `org.beryx.jlink` (jlink/jpackage) ·
`org.openjfx.javafxplugin` · JUnit 5 · GitHub Actions