# Personal Vocabulary Tracker

A desktop application for looking up, saving, and reviewing English word definitions — all in one clean window.

![App Screenshot](docs/assets/screenshot.png)

---

## Features

- **Instant definitions** — look up any word via Merriam-Webster, Wiktionary, or auto-select the best source
- **Personal word list** — save words to a local SQLite database with one click
- **Daily & total counters** — see how many words you've added today and in total
- **Smart suggestions** — get related word suggestions as you type
- **Zoom support** — Ctrl +/− to scale the UI to your preference
- **Fully offline after first run** — saved words are always available without internet

---

## Download

Go to the [**Releases**](../../releases/latest) page and download **`VocabTracker-Setup.exe`**.  
Run the installer — no Java or Python installation needed.

**Requirements:** Windows 10/11 (64-bit)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Java 21 + JavaFX 17 |
| Backend | Python 3 (PyInstaller bundle) |
| IPC | JSON over stdin/stdout (`PythonBridge`) |
| Database | SQLite (stored in `%APPDATA%\PersonalVocabTracker`) |
| Dictionary sources | Merriam-Webster API, Wiktionary API |
| Installer | Inno Setup 6 |

---

## Building from Source

See [docs/Development.md](docs/Development.md) for the full build guide.

Quick summary:

```bash
# 1. Build the Java fat JAR
mvn package -DskipTests

# 2. Bundle the Python backend
pip install pyinstaller requests
pyinstaller backend.spec
pyinstaller "Vocabulary Tracker.spec"

# 3. Copy artifacts and build installer
#    (requires Inno Setup 6 installed)
iscc installer.iss
```

---

## Documentation

| Page | Description |
|------|-------------|
| [Installation](docs/Installation.md) | Step-by-step install guide |
| [Usage](docs/Usage.md) | How to use the app |
| [Development](docs/Development.md) | Build & contribute guide |

---

## License

This project is for personal use. Feel free to fork and adapt it.
