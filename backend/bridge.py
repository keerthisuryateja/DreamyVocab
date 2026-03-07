import sys
import json
import os
import random
import uuid

# Suppress stderr to prevent breaking the Java-side JSON parser
sys.stderr = open(os.devnull, 'w')

# Force stdout to be unbuffered so responses reach Java immediately.
# When spawned as a subprocess with stdout=PIPE (as Java does), Python
# switches to block-buffered mode and sys.stdout.flush() may not reach
# the OS pipe in a frozen PyInstaller exe. os.write(1,...) writes directly
# to file descriptor 1, bypassing all Python buffering.
def _write_stdout(data: str):
    os.write(1, (data + "\n").encode("utf-8"))

import db
import suggestions
import fetcher


def _to_int(value, default):
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _build_quiz_questions(limit=5, target_words=None, option_count=4):
    entries = db.list_word_entries(400)
    if len(entries) < 2:
        return []

    meaning_by_word = {entry["word"]: entry["meaning"] for entry in entries}

    if target_words:
        quiz_words = []
        for raw_word in target_words:
            word = str(raw_word).strip().lower()
            if word in meaning_by_word and word not in quiz_words:
                quiz_words.append(word)
        quiz_words = quiz_words[:limit]
    else:
        quiz_words = [entry["word"] for entry in entries[:limit]]

    questions = []
    all_meanings = [entry["meaning"] for entry in entries]

    for word in quiz_words:
        correct = meaning_by_word.get(word)
        if not correct:
            continue
        distractors = [m for m in all_meanings if m != correct]
        random.shuffle(distractors)
        take = min(max(option_count - 1, 1), len(distractors))
        options = [correct] + distractors[:take]
        random.shuffle(options)
        questions.append(
            {
                "word": word,
                "options": options,
                "correct_index": options.index(correct),
            }
        )

    return questions


def process_request(req):
    action = req.get("action")
    word = req.get("word", "")
    
    if action == "lookup":
        # Check local DB
        local_res = db.get_word(word)
        if local_res:
            return {
                "status": "found",
                "word": word,
                "meaning": local_res["meaning"],
                "source": "local",
                "suggestions": []
            }
            
        # Try fetching
        source_pref = req.get("source", "auto")
        fetched_meaning = fetcher.fetch_meaning(word, source_pref)
        if fetched_meaning:
            db.add_word(word, fetched_meaning, "dictionary_api")
            return {
                "status": "fetched",
                "word": word,
                "meaning": fetched_meaning,
                "source": "dictionary_api",
                "suggestions": []
            }
            
        # Suggestions fallback
        sugs = suggestions.get_suggestions(word)
        if sugs:
            return {
                "status": "suggestions",
                "word": word,
                "meaning": "",
                "source": "",
                "suggestions": sugs
            }
            
        return {
            "status": "not_found",
            "word": word,
            "meaning": "",
            "source": "",
            "suggestions": []
        }
        
    elif action == "add":
        meaning = req.get("meaning", "")
        success = db.add_word(word, meaning)
        return {"status": "success" if success else "error"}
        
    elif action == "edit":
        meaning = req.get("meaning", "")
        success = db.edit_word(word, meaning)
        return {"status": "success" if success else "error"}
        
    elif action == "delete":
        success = db.delete_word(word)
        return {"status": "success" if success else "error"}
        
    elif action == "stats":
        today = db.count_today()
        total = db.count_total()
        return {
            "status": "success",
            "today": today,
            "total": total
        }

    elif action == "list_words":
        words = db.list_words()
        return {"status": "success", "words": words}

    elif action == "start_quiz":
        limit = max(1, min(_to_int(req.get("limit"), 5), 20))
        words = req.get("words")
        target_words = words if isinstance(words, list) else None
        questions = _build_quiz_questions(limit=limit, target_words=target_words)
        if not questions:
            return {
                "status": "error",
                "message": "Not enough learned words to build a quiz yet.",
            }
        return {
            "status": "success",
            "session_id": str(uuid.uuid4()),
            "questions": questions,
        }

    elif action == "submit_quiz":
        session_id = str(req.get("session_id") or uuid.uuid4())
        incoming_answers = req.get("answers")
        answers = incoming_answers if isinstance(incoming_answers, list) else []

        normalized = []
        incorrect_words = []
        correct_count = 0
        for ans in answers:
            if not isinstance(ans, dict):
                continue
            word = str(ans.get("word", "")).strip().lower()
            selected_answer = ans.get("selected_answer")
            correct_answer = str(ans.get("correct_answer", ""))
            is_correct = bool(ans.get("is_correct"))

            if is_correct:
                correct_count += 1
            elif word:
                incorrect_words.append(word)

            normalized.append(
                {
                    "word": word,
                    "selected_answer": selected_answer,
                    "correct_answer": correct_answer,
                    "is_correct": is_correct,
                }
            )

        total = len(normalized)
        db.save_quiz_session(session_id, total, correct_count, normalized)

        return {
            "status": "success",
            "session_id": session_id,
            "total": total,
            "correct": correct_count,
            "incorrect_words": incorrect_words,
        }

    elif action == "quiz_history":
        limit = max(1, min(_to_int(req.get("limit"), 20), 100))
        return {
            "status": "success",
            "history": db.list_quiz_history(limit),
        }

    elif action == "export_words":
        entries = db.list_word_entries(10000)
        return {"status": "success", "entries": entries}

    elif action == "streak":
        return {"status": "success", "streak": db.count_streak()}

    return {"status": "error", "message": f"Unknown action: {action}"}

def main():
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
            resp = process_request(req)
        except Exception as e:
            resp = {"status": "error", "message": str(e)}
            
        _write_stdout(json.dumps(resp))

if __name__ == "__main__":
    main()
