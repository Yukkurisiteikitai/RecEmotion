# lm_studio_tester.py

from openai import OpenAI
import json

client = OpenAI(
    base_url="https://f196-240d-1a-1038-4a00-991f-1738-ae0c-9320.ngrok-free.app/v1",
    api_key="lm-studio"  # 何でもOK
)

def detect_topic_change_lmstudio(
    history: list[str],
    new_utterance: str,
    model: str = "local-model"
) -> dict:
    
    history_text = "\n".join(history[-4:]) if history else "(会話開始)"
    
    prompt = f"""あなたは会話の議題管理システムです。
会話履歴全体のテーマを把握し、新しい発言がそのテーマから逸脱しているか判断してください。
同じテーマの別の側面・言い換え・深掘りは「変更なし(false)」です。
JSONのみで答えてください。

## 会話履歴
{history_text}

## 新しい発言
{new_utterance}

{{
  "is_topic_change": true か false,
  "confidence": 0.0から1.0,
  "current_theme": "会話全体のテーマを一言で",
  "reason": "継続 or 逸脱と判断した理由"
}}"""

    try:
        response = client.chat.completions.create(
            model=model,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.0,
        )
        
        raw = response.choices[0].message.content.strip()
        print(f"  [raw] {raw[:80]}")

        # パターン1: 裸のtrue/false
        if raw.lower() in ("true", "false"):
            return {
                "is_topic_change": raw.lower() == "true",
                "confidence": 0.5,
                "current_theme": "unknown",
                "reason": "(モデルがJSONなしで返答)"
            }

        # パターン2: is_topic_changeキーが欠落してるJSON
        start = raw.find("{")
        end   = raw.rfind("}") + 1
        if start == -1 or end == 0:
            return {
                "is_topic_change": None,
                "confidence": 0.0,
                "current_theme": "parse_error",
                "reason": f"JSON not found: {raw[:80]}"
            }
        
        parsed = json.loads(raw[start:end])
        
        # is_topic_changeが欠落してる場合
        if "is_topic_change" not in parsed:
            parsed["is_topic_change"] = None
        
        # テンプレート文字列が残ってたら上書き
        if parsed.get("current_theme") in (
            "会話全体のテーマを一言で", "会話全体のテーマ",
            "会話全体のテーマの一言で", "会話全体のテーマを一言で"
        ):
            parsed["current_theme"] = "unknown"
        
        return parsed
    
    except Exception as e:
        return {
            "is_topic_change": None,
            "confidence": 0.0,
            "current_theme": "error",
            "reason": str(e)
        }


# --- テスト実行 ---
if __name__ == "__main__":
    
    history = [
        "user: Pythonの非同期処理について教えて",
        "assistant: asyncioを使うといいですよ",
        "user: aiohttpと組み合わせるといい？",
    ]

    # Dataset 1: 汚染強度別
    dataset1 = [
        ("弱汚染", "asyncioのevent loopはどう動く？ラーメン"),
        ("中汚染", "asyncioのevent loopはどう動く？ラーメン食べたい"),
        ("強汚染", "ラーメン食べたいんだけど、asyncioのevent loopはどう動く？"),
    ]

    # Dataset 2: 名称変化
    dataset2 = [
        ("名称変化1", "asyncの書き方がわからない"),
        ("名称変化2", "並列実行ってどうやるの？"),
        ("名称変化3", "ノンブロッキングI/Oの仕組みは？"),
        ("名称変化4", "コルーチンって何？"),
    ]

    for dataset, cases in [("Dataset1 汚染", dataset1), ("Dataset2 名称変化", dataset2)]:
        print(f"\n{'='*60}")
        print(f"【{dataset}】")
        for label, utt in cases:
            print(f"\n  [{label}] {utt}")
            try:
                r = detect_topic_change_lmstudio(history, utt)
                print(f"    変更: {r['is_topic_change']} (確信度: {r['confidence']:.2f})")
                print(f"    理由: {r['reason']}")
            except Exception as e:
                print(f"    ERROR: {e}")