# 🕐 Alarm Clock

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue?style=flat-square&logo=java&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=flat-square&logo=gradle&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey?style=flat-square)

A clean, lightweight **desktop Alarm Clock** application built with pure Java and JavaFX. No external frameworks or backend required — everything runs natively on your machine.

---

## ✨ Features

- 🕐 **Live Digital Clock** — real-time display using `java.time`
- ⏰ **Set Multiple Alarms** — schedule alarms with custom labels
- 🔔 **Audio Alert** — plays sound on alarm trigger via `javax.sound.sampled`
- 😴 **Snooze Support** — snooze active alarms with a single click
- 🔁 **Repeat Alarms** — configure daily or one-time alarms
- 💾 **Persistent Alarms** — alarms saved to a local file and restored on relaunch
- 🖥️ **Modern JavaFX UI** — clean, responsive desktop interface

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| UI Framework | JavaFX 21 |
| Build Tool | Gradle (with Gradle Wrapper) |
| Time API | `java.time` (LocalTime, ZonedDateTime) |
| Alarm Scheduling | `ScheduledExecutorService` / `java.util.Timer` |
| Audio Playback | `javax.sound.sampled` |
| Data Persistence | `java.io` / `java.nio` (flat file) |
| Testing | JUnit 5 |

> **No database. No Spring. No external backend.** Pure Java is all you need.

---

## 📁 Project Structure

```
Alarm_Clock/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── org/example/       # Application source code
│       │   └── resources/             # FXML, CSS, audio assets
│       └── test/
│           ├── java/
│           │   └── org/example/       # Unit tests
│           └── resources/
├── gradle/
│   └── wrapper/                       # Gradle wrapper files
├── build.gradle                       # App build config
├── settings.gradle                    # Project settings
└── gradlew / gradlew.bat              # Gradle wrapper scripts
```

---

## ⚙️ Prerequisites

Before running the project, make sure you have:

- **JDK 21** — [Download here](https://adoptium.net/)
- **JavaFX 21 SDK** *(only if not bundled via Gradle)* — [Download here](https://openjfx.io/)
- **Git** — to clone the repository

Verify your Java installation:

```bash
java -version
# Expected: openjdk version "21.x.x" ...
```

---

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/abhi-pillai/Alarm_Clock.git
cd Alarm_Clock
```

### 2. Build the Project

```bash
# On macOS / Linux
./gradlew build

# On Windows
gradlew.bat build
```

### 3. Run the Application

```bash
# On macOS / Linux
./gradlew run

# On Windows
gradlew.bat run
```

### 4. Run Tests

```bash
./gradlew test
```

Test reports are generated at:
```
app/build/reports/tests/test/index.html
```

---

## 📦 Build Distribution

To create a standalone distributable package:

```bash
./gradlew distZip    # Creates a .zip archive
./gradlew distTar    # Creates a .tar archive
```

Output is placed in:
```
app/build/distributions/
```

The archive includes a launch script (`bin/app` or `bin/app.bat`) so end users don't need Gradle installed.

---

## 🧠 How It Works

```
┌──────────────────────────────────────────────────────┐
│                     JavaFX UI Layer                  │
│         (Clock display, Alarm list, Controls)        │
└─────────────────────┬────────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────────┐
│                  Alarm Manager                       │
│     ScheduledExecutorService — polls every second    │
│     Compares current time against alarm list         │
└──────────┬───────────────────────┬───────────────────┘
           │                       │
┌──────────▼──────┐     ┌──────────▼──────────────────┐
│  Sound Engine   │     │     Persistence Layer        │
│ javax.sound     │     │  java.io — reads/writes      │
│ .sampled (.wav) │     │  alarms to local file        │
└─────────────────┘     └─────────────────────────────-┘
```

---

## 🧪 Running Tests

The project uses **JUnit 5** for unit testing. Tests are located in:

```
app/src/test/java/org/example/
```

Run all tests:

```bash
./gradlew test
```

After the run, open the HTML report for detailed results:

```
app/build/reports/tests/test/index.html
```

---

## 🤝 Contributing

Contributions are welcome! Here's how to get started:

1. Fork the repository
2. Create a new branch: `git checkout -b feature/your-feature-name`
3. Make your changes and commit: `git commit -m "Add: your feature description"`
4. Push to your fork: `git push origin feature/your-feature-name`
5. Open a Pull Request

Please make sure all existing tests pass before submitting a PR.

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**Abhishek Pillai**
- GitHub: [@abhi-pillai](https://github.com/abhi-pillai)
- Email: 12a.abhishekpillai@gmail.com

---

> Built with ☕ Java 21 + 🎨 JavaFX — no fluff, just clean desktop engineering.