# topic_change_cabocha.py

import subprocess
import math
from collections import Counter

THRESHOLD = 0.25

def extract_keywords(text: str) -> list[str]:
    result = subprocess.run(
        ["cabocha", "-f1"],
        input=text, capture_output=True,
        text=True, encoding="utf-8"
    )

    STOP = {"する", "ある", "いる", "なる", "こと", "もの", "これ", "それ", "て", "の"}
    keywords = []

    for line in result.stdout.splitlines():
        if "\t" not in line or line.startswith("*") or line == "EOS":
            continue
        
        surface, features = line.split("\t", 1)
        parts = features.split(",")
        pos = parts[0]

        if pos not in ("名詞", "動詞", "形容詞"):
            continue

        # 原形(index 6)が存在して*でなければ原形、なければ表層形
        base = parts[6] if len(parts) > 6 and parts[6] not in ("*", "") else surface

        if base not in STOP:
            keywords.append(base)

    return keywords

def to_vector(keywords: list[str]) -> dict[str, float]:
    if not keywords:
        return {}
    count = Counter(keywords)
    total = len(keywords)
    return {k: v / total for k, v in count.items()}


def cosine(v1: dict, v2: dict) -> float:
    common = set(v1) & set(v2)
    if not common:
        return 0.0
    dot   = sum(v1[k] * v2[k] for k in common)
    norm1 = math.sqrt(sum(x**2 for x in v1.values()))
    norm2 = math.sqrt(sum(x**2 for x in v2.values()))
    return dot / (norm1 * norm2) if norm1 and norm2 else 0.0


def detect_topic_change(history_texts: list[str], new_utterance: str) -> dict:
    history_keywords = []
    for t in history_texts[-3:]:
        history_keywords += extract_keywords(t)

    current_vec = to_vector(history_keywords)
    new_vec     = to_vector(extract_keywords(new_utterance))
    sim         = cosine(current_vec, new_vec)

    return {
        "is_topic_change": sim < THRESHOLD,
        "similarity":      round(sim, 3),
        "threshold":       THRESHOLD,
        "new_keywords":    list(new_vec.keys())[:5],
    }


if __name__ == "__main__":
    history = [
        "Pythonの非同期処理について教えて",
        "asyncioを使うといいですよ",
        "aiohttpと組み合わせるといい？",
    ]

    tests = [
        "asyncioのevent loopってどう動くの？",
        "そういえばラーメン食べたい",
        "Webスクレイピングにも使える？",
    ]

    for utt in tests:
        print(f"\n発話: {utt}")
        r = detect_topic_change(history, utt)
        print(f"  変更: {r['is_topic_change']} "
              f"(類似度: {r['similarity']} / 閾値: {r['threshold']})")
        print(f"  新キーワード: {r['new_keywords']}")