# Domain Service ドキュメント

`app/src/main/java/com/example/recemotion/domain/service/`

---

## 概要

このパッケージはドメイン層の**サービスインターフェース**を定義する。
「何ができるか」だけを宣言し、「どう実現するか」は `data/` 層の実装クラスに委ねる。

これにより UseCase・ViewModel はインターフェースだけに依存でき、
実装（Kuromoji/CaboCha/LLMの切り替えなど）を知らなくてよくなる。

| インターフェース | 実装クラス | 責務 |
|---|---|---|
| `LLMInferenceService` | `LlmInferenceServiceImpl`（data層） | LLMモデルのロード・推論・ストリーミング |
| `LogicalFlowService` | `LogicalFlowAnalyzerImpl`（data/parser） | 日本語テキストの論理フロー解析 |
| `TopicChangeService` | `TopicChangeDetectorImpl`（data/parser） | 会話ターン間の話題変化検出 |

---

## 1. `LLMInferenceService`

LLMモデルの操作をドメイン層に公開するインターフェース。

```kotlin
interface LLMInferenceService {
    val partialResults: SharedFlow<String>          // 生成中のトークン断片をブロードキャスト
    val progress: StateFlow<InferenceProgress>      // 現在の推論フェーズ（ロード中/生成中 等）

    fun initModel()                                 // モデルを初期化・リロード
    fun isModelInitialized(): Boolean               // ロード済みかチェック

    fun generateResponse(prompt: String)            // 自由形式の生成（結果は partialResults へ流れる）
    fun analyzeThoughtStructure(prompt: String): Flow<LlmStreamEvent>  // 構造化解析（Flow で返す）
    fun close()                                     // リソース解放
}
```

### 2つの生成メソッドの使い分け

| メソッド | 返り値 | 用途 |
|---|---|---|
| `generateResponse()` | なし（`partialResults` に流れる） | UIへのリアルタイム文字描画など、非同期で"垂れ流し"たい場合 |
| `analyzeThoughtStructure()` | `Flow<LlmStreamEvent>` | UseCase でコルーチンとして `collect` しながら構造解析したい場合 |

### 値の流れ例

```
// --- generateResponse() を呼んだ場合 ---
generateResponse("今日の気分を教えてください")
  ↓ (非同期で partialResults に流れる)
partialResults.collect { token ->
    // token = "今日"
    // token = "は"
    // token = "少し"
    // token = "疲れ"
    // token = "気味"
    // token = "です"
    // token = "。"
}
progress: IDLE → GENERATING → DONE

// --- analyzeThoughtStructure() を使った場合 ---
analyzeThoughtStructure(prompt).collect { event ->
    when (event) {
        is LlmStreamEvent.Delta -> // "前提:" ... "感情:" ... (逐次トークン)
        is LlmStreamEvent.Done  -> // "前提: ...\n感情: ...\n仮定: ..." (全文)
        is LlmStreamEvent.Error -> // "モデル未初期化"
    }
}
```

### ライフサイクル

```
initModel()           ← アプリ起動後・モデルファイル配置後に呼ぶ
isModelInitialized()  ← UseCase がプロンプト送信前に確認
generateResponse() / analyzeThoughtStructure()  ← 実際の推論
close()               ← Activity/Fragment が破棄されるとき
```

---

## 2. `LogicalFlowService`

日本語テキストの論理フロー解析をドメイン層に公開するインターフェース。
実装は `LogicalFlowAnalyzerImpl`（SentenceTokenizer → MorphemeAnalyzer → RelationDetector のパイプライン）。

```kotlin
interface LogicalFlowService {
    suspend fun analyze(text: String): LogicalFlowAnalysis
}
```

### 値の流れ例

```
val analysis = logicalFlowService.analyze(
    "今日は疲れた。なぜなら徹夜したからだ。それでも仕事は終わった。"
)

// 返り値:
LogicalFlowAnalysis(
    sentences=[
        AnalyzedSentence(0, "今日は疲れた。",         structure=SentenceStructure("","疲れる",""), ...),
        AnalyzedSentence(1, "なぜなら徹夜したからだ。", structure=SentenceStructure("","する",""),  ...),
        AnalyzedSentence(2, "それでも仕事は終わった。", structure=SentenceStructure("仕事","終わる",""), ...)
    ],
    relations=[
        LogicalRelation(0, 1, CAUSAL,    "なぜなら", 95),
        LogicalRelation(1, 2, CONTRAST,  "それでも", 82)
    ],
    overallFlow=[
        "(主語不明) が (述語不明)",
        "(主語不明) が (述語不明)",
        "仕事 が (述語不明)"
    ]
)
```

`suspend fun` なので呼び出し元は ViewModel や UseCase のコルーチンスコープから呼ぶ。

---

## 3. `TopicChangeService`

会話ターン間で話題が変わったかを検出するインターフェース。
実装は `TopicChangeDetectorImpl`（構造類似度 + LLMプロンプト生成）。

```kotlin
interface TopicChangeService {
    fun evaluateStructuralChange(
        current: LogicalFlowAnalysis,
        previous: LogicalFlowAnalysis?
    ): Double    // 0.0 = 同じ話題 / 1.0 = 完全に別の話題

    fun buildTopicChangePrompt(currentText: String, previousText: String): String
}
```

### `evaluateStructuralChange()` の値の変化例

主語集合の **Jaccard類似度** を使って数値化する。

```
// ターン1: "私は昨日疲れた。" → subjects={"私"}
// ターン2: "私もそう思う。"   → subjects={"私"}
evaluateStructuralChange(turn2, turn1) → 0.0   // 完全一致 → 話題変化なし

// ターン1: "私は仕事が大変だ。" → subjects={"私"}
// ターン2: "田中さんは元気か。" → subjects={"田中さん"}
evaluateStructuralChange(turn2, turn1) → 1.0   // 共通なし → 話題変化あり

// ターン1: "私と彼が議論した。" → subjects={"私", "彼"}
// ターン2: "私は後悔している。" → subjects={"私"}
// Jaccard: intersection={私} / union={私,彼} = 0.5 → change=0.5
evaluateStructuralChange(turn2, turn1) → 0.5   // 中程度

// previous=null（初ターン）
evaluateStructuralChange(turn1, null) → 0.0    // 比較対象なし → 変化なしとみなす
```

### `buildTopicChangePrompt()` の出力例

構造類似度だけでは判定が難しいケースをLLMに委ねるためのプロンプトを生成する。

```kotlin
buildTopicChangePrompt(
    currentText  = "今日は上司に怒られた。",
    previousText = "昨日は仕事がうまくいった。"
)
```

```
You are a conversation analyzer. Compare the two texts below and determine
if they are discussing the same topic or if a new topic has started.

Previous Text:
昨日は仕事がうまくいった。

Current Text:
今日は上司に怒られた。

Respond with ONLY a JSON object:
{
  "is_new_topic": boolean,
  "confidence": 0.0 to 1.0,
  "reason": "short explanation",
  "suggested_title": "a short title for the current topic"
}
```

このプロンプトを `LLMInferenceService.generateResponse()` に渡し、
LLMからのJSON応答を受け取ることで最終的な話題変化判定を行う。

---

## 設計方針まとめ

| 方針 | 具体的な表れ |
|---|---|
| **依存性逆転** | UseCase はインターフェースだけに依存。Kuromojiか CaboChaかを知らない |
| **Flowによる非同期** | LLMの出力を逐次Flowで流す。collectする側が処理を決める |
| **2段構えの話題変化検出** | 軽量な構造類似度（即時・0コスト）→ 必要なときだけLLMプロンプトへエスカレーション |
| **suspend vs 非suspend** | `LogicalFlowService.analyze()` は結果待ちが必要なので `suspend`。`LLMInferenceService.generateResponse()` は「流しっぱなし」なので非suspend |
