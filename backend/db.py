import sqlite3
import os
import sys
from datetime import date

if getattr(sys, 'frozen', False):
    # Installed app: write user data to %APPDATA%\PersonalVocabTracker
    # so the install directory (Program Files) never needs write access.
    _app_data = os.environ.get("APPDATA") or os.path.expanduser("~")
    base_dir = os.path.join(_app_data, "PersonalVocabTracker")
    os.makedirs(base_dir, exist_ok=True)
else:
    # Dev/source run: keep db next to the source files
    base_dir = os.path.dirname(os.path.abspath(__file__))

DB_PATH = os.path.join(base_dir, "vocabulary.db")

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS words (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            word      TEXT    NOT NULL UNIQUE,
            meaning   TEXT    NOT NULL,
            source    TEXT,
            added_on  DATE    DEFAULT (date('now'))
        )
    ''')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_word ON words (word)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_added_on ON words (added_on)')
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS quiz_sessions (
            session_id       TEXT PRIMARY KEY,
            total_questions  INTEGER NOT NULL,
            correct_answers  INTEGER NOT NULL,
            created_on       TEXT DEFAULT (datetime('now'))
        )
    ''')
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS quiz_answers (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id      TEXT NOT NULL,
            word            TEXT NOT NULL,
            selected_answer TEXT,
            correct_answer  TEXT NOT NULL,
            is_correct      INTEGER NOT NULL,
            answered_on     TEXT DEFAULT (datetime('now')),
            FOREIGN KEY(session_id) REFERENCES quiz_sessions(session_id)
        )
    ''')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_quiz_sessions_created_on ON quiz_sessions (created_on)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_quiz_answers_session_id ON quiz_answers (session_id)')
    conn.commit()
    conn.close()

def add_word(word, meaning, source="manual"):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    try:
        if len(word) > 100 or len(meaning) > 5000:
            return False # Schema logic constraint test
            
        cursor.execute('''
            INSERT INTO words (word, meaning, source, added_on)
            VALUES (?, ?, ?, ?)
        ''', (word.lower(), meaning, source, date.today().isoformat()))
        conn.commit()
        return True
    except sqlite3.IntegrityError:
        return False
    finally:
        conn.close()

def get_word(word):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('SELECT meaning, source FROM words WHERE word = ?', (word.lower(),))
    res = cursor.fetchone()
    conn.close()
    if res:
        return {"meaning": res[0], "source": res[1]}
    return None

def edit_word(word, new_meaning):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('UPDATE words SET meaning = ? WHERE word = ?', (new_meaning, word.lower()))
    changed = cursor.rowcount > 0
    conn.commit()
    conn.close()
    return changed

def delete_word(word):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('DELETE FROM words WHERE word = ?', (word.lower(),))
    changed = cursor.rowcount > 0
    conn.commit()
    conn.close()
    return changed

def count_today():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('SELECT COUNT(*) FROM words WHERE added_on = ?', (date.today().isoformat(),))
    res = cursor.fetchone()
    conn.close()
    return res[0] if res else 0

def count_total():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('SELECT COUNT(*) FROM words')
    res = cursor.fetchone()
    conn.close()
    return res[0] if res else 0

def list_words(limit=300):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('SELECT word FROM words ORDER BY added_on DESC, id DESC LIMIT ?', (limit,))
    rows = cursor.fetchall()
    conn.close()
    return [row[0] for row in rows]

def list_word_entries(limit=300):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        'SELECT word, meaning FROM words ORDER BY added_on DESC, id DESC LIMIT ?',
        (limit,)
    )
    rows = cursor.fetchall()
    conn.close()
    return [{"word": row[0], "meaning": row[1]} for row in rows]

def save_quiz_session(session_id, total_questions, correct_answers, answers):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        '''
        INSERT OR REPLACE INTO quiz_sessions (session_id, total_questions, correct_answers, created_on)
        VALUES (?, ?, ?, datetime('now'))
        ''',
        (session_id, total_questions, correct_answers)
    )

    cursor.execute('DELETE FROM quiz_answers WHERE session_id = ?', (session_id,))
    for ans in answers:
        cursor.execute(
            '''
            INSERT INTO quiz_answers (session_id, word, selected_answer, correct_answer, is_correct, answered_on)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
            ''',
            (
                session_id,
                ans.get("word", ""),
                ans.get("selected_answer"),
                ans.get("correct_answer", ""),
                1 if ans.get("is_correct") else 0,
            )
        )

    conn.commit()
    conn.close()

def list_quiz_history(limit=20):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        '''
        SELECT session_id, total_questions, correct_answers, created_on
        FROM quiz_sessions
        ORDER BY created_on DESC
        LIMIT ?
        ''',
        (limit,)
    )
    rows = cursor.fetchall()
    conn.close()
    return [
        {
            "session_id": row[0],
            "total_questions": row[1],
            "correct_answers": row[2],
            "created_on": row[3],
        }
        for row in rows
    ]

def count_streak():
    """Return the number of consecutive days (ending today) that at least one word was added."""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "SELECT DISTINCT added_on FROM words ORDER BY added_on DESC"
    )
    rows = cursor.fetchall()
    conn.close()

    if not rows:
        return 0

    from datetime import timedelta
    streak = 0
    check = date.today()
    for (day_str,) in rows:
        try:
            day = date.fromisoformat(day_str)
        except ValueError:
            continue
        if day == check:
            streak += 1
            check -= timedelta(days=1)
        elif day < check:
            break
    return streak

init_db()
