# Development Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21+ | Compile & run JavaFX frontend |
| Maven | 3.9+ | Java build system |
| Python | 3.10+ | Backend runtime |
| PyInstaller | 6+ | Bundle Python backend |
| Inno Setup | 6+ | Build Windows installer |

---

## Project Structure

```
Personal Vocabulary Tracker/
├── src/main/java/com/vocabulary/   # JavaFX application
│   ├── Main.java                   # JavaFX entry point
│   ├── Launcher.java               # Fat-JAR launcher shim
│   ├── UIController.java           # FXML controller
│   └── PythonBridge.java           # IPC to Python backend
├── src/main/resources/
│   ├── app.fxml                    # UI layout
│   └── dreamy.css                  # Stylesheet
├── backend/                        # Python backend
│   ├── bridge.py                   # stdin/stdout JSON bridge
│   ├── db.py                       # SQLite word storage
│   ├── fetcher.py                  # Dispatch to dictionary sources
│   ├── suggestions.py              # Word suggestions
│   └── sources/
│       ├── auto.py
│       ├── merriam.py
│       └── wiktionary.py
├── backend.spec                    # PyInstaller spec for backend
├── Vocabulary Tracker.spec         # PyInstaller spec for launcher
├── installer.iss                   # Inno Setup installer script
└── pom.xml                         # Maven build file
```

---

## Build Steps

### 1. Java fat JAR

```bash
mvn package -DskipTests
# Output: target/personal-vocabulary-tracker-1.0-SNAPSHOT.jar
# Copy as: dist/Tracker.jar
```

### 2. Python backend bundle

```bash
pip install requests pyinstaller

pyinstaller backend.spec
# Output: dist/backend.exe

pyinstaller "Vocabulary Tracker.spec"
# Output: dist/Vocabulary Tracker.exe
```

### 3. Assemble dist folder

```
dist/
  Tracker.jar
  backend.exe
  Vocabulary Tracker.exe
```

### 4. Build installer

Requires [Inno Setup 6](https://jrsoftware.org/isinfo.php) installed.

```bash
iscc installer.iss
# Output: installer_out/VocabTracker-Setup.exe
```

---

## Running in Dev Mode

```bash
# Start the Python backend directly
python backend/bridge.py

# Run the Java frontend
mvn javafx:run
```

---

## Architecture — How the IPC Works

```
JavaFX UI  ──(stdin JSON)──►  Python backend (bridge.py)
           ◄──(stdout JSON)──
```

`PythonBridge.java` spawns `backend.exe` (or `backend/bridge.py` in dev mode) as a subprocess and exchanges newline-delimited JSON messages. The backend handles `lookup`, `save`, `suggest`, `stats`, and `get` commands.

---

## Tests

```bash
# Python backend tests
python -m pytest test_backend.py test_bridge.py -v
```

---

## Creating a Release

Push a version tag — the GitHub Actions workflow handles the rest:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow builds everything, runs Inno Setup, and publishes `VocabTracker-Setup.exe` as a downloadable asset on the Releases page.
