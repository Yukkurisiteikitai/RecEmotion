# topic_change_prompt_builder.py
# Google AI Edge Galleryに貼り付けるプロンプトを生成するだけ

def build_topic_change_prompt(history: list[str] = [""], new_utterance: str = "HappyHappyHappyCat") -> str:
    """
    Edge Galleryに手動で貼るプロンプトを構築する。
    APIコールは一切しない。
    """
    history_text = "\n".join(history[-4:]) if history else "(会話開始)"

    return f"""Yあなたは話題変更検出器です。以下の会話履歴と新しい発言を見て、話題が変わったか判断してください。
JSONのみで答えてください。説明不要。

## 会話履歴
{history_text}

## 新しい発言
{new_utterance}

以下の形式で答えてください:
{{
  "is_topic_change": true か false,
  "confidence": 0.0から1.0の数値,
  "previous_topic": "会話履歴のトピックを日本語で要約",
  "new_topic": "変更した場合の新トピック、変更なければnull",
  "reason": "判断理由を日本語で"
}}"""


if __name__ == "__main__":
    history = [
        "user: Pythonの非同期処理について教えて",
        "assistant: asyncioを使うといいですよ",
        "user: aiohttpと組み合わせるといい？",
    ]

    tests = [
        "asyncioのevent loopってどう動くの？",  # 継続想定
        "そういえばラーメン食べたい",             # 変更想定
        "Webスクレイピングにも使える？",          # 漸進・曖昧
    ]

    for utt in tests:
        print("=" * 60)
        print(f"【テスト発話】: {utt}")
        print()
        print(build_topic_change_prompt(history, utt))
        print()
