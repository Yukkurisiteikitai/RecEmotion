# data/llm アーキテクチャドキュメント

対象パス: `app/src/main/java/com/example/recemotion/data/llm/`

---

## 1. 実行順序

思考分析リクエストが発生してから結果を返すまでの処理フロー：

```
1. ThoughtPromptBuilder.build(structure, emotionContext?)
       ↓  JSON スキーマ付きプロンプト文字列を生成
2. LLMInferenceServiceImpl.analyzeThoughtStructure(prompt)
       ↓  MediaPipe LlmInference に推論を委譲
       ↓  モデル未初期化の場合は TestLLMInference へフォールバック
3. Flow<LlmStreamEvent> を収集
       ↓  LlmStreamEvent.Delta → トークン受信
       ↓  LlmStreamEvent.Done(fullText) → 完了・JSON 文字列取得
4. ThoughtAnalysisJsonParser.parse(jsonText)
       ↓  JSON を ThoughtAnalysisResult ドメインモデルへ変換
5. 結果を上位 UseCase へ返却
```

モデルロードの順序（アプリ起動時）：
```
1. LLMInferenceServiceImpl.initModel()
       ↓  resolveModelFile() で internal storage → Downloads の順に探索
       ↓  LlmInference.createFromOptions() で MediaPipe 初期化
       ↓  _progress StateFlow を LOADING → IDLE/ERROR で更新
```

---

## 2. クラスの実行で目的をどう達成しているのかの説明

### LLMInferenceServiceImpl

`LLMInferenceService` ドメインインターフェースを実装し、MediaPipe の `LlmInference` を
ラップするデータ層の中核クラス。以下の責務を担う：

- **モデルライフサイクル管理**: `initModel()` / `close()` でデバイス上の LLM を制御
- **推論結果のストリーミング**: `partialResults: SharedFlow<String>` で UI へリアルタイム配信
- **進捗通知**: `progress: StateFlow<InferenceProgress>` で LOADING/GENERATING/DONE/ERROR を伝達
- **トークン制限制御**: `trimPromptToTokenLimit()` でプロンプトを 768 トークン以内に収める（MAX 1024 - OUTPUT_RESERVE 256）
- **フォールバック**: モデル未準備・推論エラー時に `TestLLMInference` へ委譲し、開発中でも動作を継続

生成パスは 2 系統：
| メソッド | 用途 | 出力先 |
|---|---|---|
| `generateResponse(prompt)` | 汎用会話応答（文単位分割） | `_partialResults SharedFlow` |
| `analyzeThoughtStructure(prompt)` | 思考構造解析（JSON 取得） | `Flow<LlmStreamEvent>` |

### ThoughtPromptBuilder

`ThoughtStructure`（ドメインモデル）をインデント付きテキストに変換し、
LLM が必ず JSON のみを返すよう schema 制約を付加したプロンプトを構築する。

```
ThoughtNode のツリー → 2 スペースインデントの箇条書き → プロンプト本文に埋め込み
感情コンテキスト（省略可） → "Emotion State Log" セクションとして末尾に付与
```

### ThoughtAnalysisJsonParser

LLM が返す JSON 文字列を `ThoughtAnalysisResult` ドメインモデルに変換するパーサー。

- `extractJson()` で余分なテキストを除去し `{...}` のみを抽出（LLM が前後にテキストを付ける場合に対処）
- `readBiases()` / `readMissing()` は JSONObject / String の両形式を受け付け、LLM 出力のばらつきを吸収

### InferenceProgress.kt / LlmStreamEvent.kt

`data.llm` パッケージにあった型を `domain.model` へ移動した際の後方互換レイヤー。
`typealias` により既存コードのインポートを変更せず参照できる。実体はドメイン層に存在する。

---

## 3. 目的適合の理由

| 設計選択 | 理由 |
|---|---|
| `@Singleton` + `helperScope(IO)` | MediaPipe LlmInference はコスト高な初期化を持つため、プロセス内で 1 インスタンスのみ保持し IO スレッドで非同期実行する |
| `SharedFlow(replay=0)` for partialResults | 過去のトークンを再配信せず、常に「今流れているトークン」のみ UI に届ける |
| `StateFlow` for progress | 最新状態を常に参照可能にし、購読開始タイミングに依らず現在のステージを取得できる |
| フォールバック to `TestLLMInference` | デバイスにモデルファイルが無い開発環境でもアプリ全体の動作検証が可能 |
| プロンプトにスキーマを明示 | 小型の量子化 LLM は指示追従能力が限られるため、出力形式を schema で強制し JSON 解析の成功率を上げる |
| JSON の柔軟パース（String / Object 両対応） | 小型 LLM が配列要素を文字列で返す場合があり、その差異を吸収することで parse エラーを防ぐ |

---

## 4. クラスの依存関係

```
data/llm パッケージ
┌─────────────────────────────────────────┐
│  LLMInferenceServiceImpl                │
│   ├── implements: LLMInferenceService   │  ← domain/service
│   ├── uses: LlmInference               │  ← com.google.mediapipe
│   ├── uses: InferenceProgress          │  ← domain/model (typealias)
│   ├── uses: LlmStreamEvent             │  ← domain/model (typealias)
│   └── fallback: TestLLMInference       │  ← app root package
│                                         │
│  ThoughtPromptBuilder                   │
│   └── uses: ThoughtStructure/ThoughtNode│  ← domain/model
│                                         │
│  ThoughtAnalysisJsonParser              │
│   └── returns: ThoughtAnalysisResult   │  ← domain/model
│       ├── Assumption                   │
│       ├── BiasDetection                │
│       └── MissingPerspective           │
│                                         │
│  InferenceProgress.kt  (typealias)      │  → domain/model/InferenceProgress
│  LlmStreamEvent.kt     (typealias)      │  → domain/model/LlmStreamEvent
└─────────────────────────────────────────┘

上位からの依存:
  domain/usecase/AnalyzeThoughtUseCase
      → LLMInferenceService (インターフェース経由)
      → ThoughtPromptBuilder
      → ThoughtAnalysisJsonParser
```

Hilt による注入：
- `LLMInferenceServiceImpl` は `@Singleton` + `@Inject constructor(@ApplicationContext)`
- `ThoughtPromptBuilder` / `ThoughtAnalysisJsonParser` は `@Inject constructor()`（スコープなし、UseCase に注入される）

---

## 5. 使用例・ユースケース

### ユースケース A: 思考構造を LLM で解析する

```kotlin
// AnalyzeThoughtUseCase 内での利用イメージ
val prompt = promptBuilder.build(thoughtStructure, emotionLog)

llmService.analyzeThoughtStructure(prompt)
    .collect { event ->
        when (event) {
            is LlmStreamEvent.Delta -> { /* 途中経過を UI へ流す */ }
            is LlmStreamEvent.Done  -> {
                val result = parser.parse(event.fullText)
                // result.assumptions, result.possibleBiases などを永続化
            }
        }
    }
```

### ユースケース B: 汎用会話応答（MainScreenFragment）

```kotlin
// モデル初期化（アプリ起動時に MainActivity が呼ぶ）
llmService.initModel()

// 進捗を UI に反映
llmService.progress.collect { progress ->
    when (progress.stage) {
        LlmStage.LOADING    -> showLoadingIndicator()
        LlmStage.IDLE       -> hideLoadingIndicator()
        LlmStage.ERROR      -> showError(progress.message)
        else -> {}
    }
}

// テキスト入力後に生成リクエスト
llmService.generateResponse(userInput)

// 部分結果を TextView に追記
llmService.partialResults.collect { sentence ->
    appendToOutput(sentence)
}
```

### ユースケース C: 開発時フォールバック動作

モデルファイル (`model.bin` / `model.task`) が Downloads にも内部ストレージにも無い場合、
`LLMInferenceServiceImpl` は自動的に `TestLLMInference` へ処理を委ねる。
開発環境ではモデル不要で `ThoughtAnalysisResult` のダミーデータが返り、
UI やデータ永続化の検証を行える。
