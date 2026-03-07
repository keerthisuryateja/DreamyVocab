# Usage Guide

## Toolbar Overview

| Element | Purpose |
|---------|---------|
| Search box | Type a word and press Enter |
| Source dropdown | auto / merriam / wiktionary / manual |
| **Search** | Look up and save the word |
| **+ Add** | Add a word manually with a custom definition |
| **Export** | Save your vocabulary as `.txt` or `.csv` |
| Size dropdown | Choose quiz question count (5 / 10 / 15 / 20) |
| **Quiz** | Start a multiple-choice quiz |
| **A−  A+  ↺** | Zoom out / in / reset |

---

## Searching for a Word

1. Type a word in the search box.
2. Choose a **source** from the dropdown:
   - **Auto** — tries the best available source automatically
   - **Merriam-Webster** — authoritative English dictionary
   - **Wiktionary** — broader coverage including slang and technical terms
3. Press **Enter** or click **Search**.

Words are saved automatically on first successful lookup. The **Today** and **Total** counters in the header update instantly.

---

## Adding a Word Manually

Click **+ Add** to open the Add Word dialog.  
Enter a word and its definition, then click OK. No internet connection is needed.

---

## Editing a Definition

Open a word card, click **Edit**, modify the text, then click **Save** (or press Escape to cancel).

---

## Deleting a Word

Open a word card and click **Delete**. A confirmation dialog will appear.

---

## Exporting Your Vocabulary

Click **Export** in the toolbar. A save dialog opens — choose `.txt` for a readable format or `.csv` for use in spreadsheets.

---

## Viewing Suggestions

If a word isn't found, a suggestion list appears inside the result card. Click any suggestion to search it directly.

---

## Quiz Mode

1. Set the question count with the size dropdown (5 / 10 / 15 / 20).
2. Click **Quiz** — questions are drawn from your most recently added words.
3. Select an answer and click **Submit Answer**.
4. After the last question click **Finish** to save your score.
5. If you got any wrong, **Retry Incorrect** re-quizzes only those words.
6. Click **✕** in the quiz header to dismiss the panel at any time.

Recent quiz session scores are shown inside the quiz panel.

---

## Header Badges

| Badge | Meaning |
|-------|---------|
| **Today: N** | Words added today |
| **Total: N** | All words in your vocabulary |
| **🔥 N day streak** | Consecutive days you've added at least one word (shown when ≥ 2 days) |

---

## Zoom / Accessibility

| Shortcut | Action |
|----------|--------|
| `Ctrl` + `+` | Zoom in |
| `Ctrl` + `-` | Zoom out |
| `↺` button | Reset zoom to default |
| `Escape` | Clear card / cancel edit |

---

## Sidebar

The left panel lists all your saved words, newest first. Use the **Filter** box to search by name. Click any word to look it up immediately.  
Use the **◀ / ▶** button in the top-left to hide or show the sidebar.

---

## Your Word Database

All saved words are stored locally in `%APPDATA%\PersonalVocabTracker\vocabulary.db`.  
See [Installation.md](Installation.md) for backup and migration tips.
