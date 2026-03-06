# Installation Guide

## Using the Installer (Recommended)

1. Go to the [**Releases**](../../releases/latest) page.
2. Download **`VocabTracker-Setup.exe`**.
3. Double-click the installer and follow the wizard.
4. Optionally create a desktop shortcut when prompted.
5. Click **Launch Personal Vocabulary Tracker** at the end.

**System requirements:**
- Windows 10 or Windows 11 (64-bit)
- ~100 MB free disk space
- Internet connection (for looking up new words)

No Java or Python installation is required — everything is bundled inside the installer.

---

## Data Storage

Your saved words are stored in:

```
%APPDATA%\PersonalVocabTracker\vocabulary.db
```

This is a standard SQLite database file. You can back it up, copy it to another machine, or open it with any SQLite browser (e.g., [DB Browser for SQLite](https://sqlitebrowser.org/)).

---

## Uninstalling

Open **Settings → Apps** (Windows 11) or **Control Panel → Programs and Features** (Windows 10), find **Personal Vocabulary Tracker**, and click **Uninstall**.

Your word database in `%APPDATA%\PersonalVocabTracker` is **not** deleted automatically — remove that folder manually if you want a clean uninstall.
