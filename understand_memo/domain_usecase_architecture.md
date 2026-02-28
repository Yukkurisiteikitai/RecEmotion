# Domain UseCase ドキュメント

`app/src/main/java/com/example/recemotion/domain/usecase/`

---

## 概要

このパッケージはアプリの**ビジネスロジックのエントリーポイント**を担う。
ViewModel はこの UseCase を呼び出すだけで、
パーサー・LLM・DBの処理を直接知らなくてよい。

| UseCase | 責務 |
|---|---|
| `AnalyzeThoughtUseCase` | テキスト1件を構文解析 → LLM解析 → DB保存するメインフロー |
| `ManageConversationUseCase` | テキストを受け取り、話題継続か新規かを判定してDBに保存する |
| `SystemDiagnosticUseCase` | 辞書・モデルの配置状況を診断してリストで返す |

---

## 1. `AnalyzeThoughtUseCase`

アプリのコアフロー。テキストを受け取り、構文解析 → LLM解析 → 永続化 を行い、
途中経過・完了・エラーを `Flow<AnalysisUpdate>` で流す。

### 依存関係

```
AnalyzeThoughtUseCase
  ├── DependencyParser         (Kuromoji / CaboCha の係り受け解析)
  ├── CabochaThoughtMapper     (CabochaResult → ThoughtStructure 変換)
  ├── ThoughtPromptBuilder     (ThoughtStructure → LLMプロンプト文字列)
  ├── LLMInferenceService      (LLMストリーミング推論)
  ├── ThoughtAnalysisJsonParser (LLM出力JSON → ThoughtAnalysisResult)
  ├── ThoughtRepository        (DB保存・取得)
  └── ThoughtStructureJsonAdapter (ThoughtStructure → JSON文字列)
```

### 処理の流れ

```
execute(text="今日は疲れた。なぜなら徹夜したからだ。")
  │
  ├─ [バリデーション]
  │   text.isBlank() → send(AnalysisUpdate.Error("Input is empty")) → 終了
  │
  ├─ send(AnalysisUpdate.Analyzing)
  │   // UIに「解析中...」スピナーを表示させる
  │
  ├─ [Step 1: 係り受け解析]（Dispatchers.Default）
  │   parser.parse("今日は疲れた。なぜなら徹夜したからだ。")
  │     → CabochaResult(chunks=[chunk0"今日は", chunk1"疲れた。", chunk2"なぜなら", chunk3"徹夜したからだ。"])
  │   mapper.map(CabochaResult)
  │     → ThoughtStructure(roots=[
  │           ThoughtNode("1","疲れた。", children=[ThoughtNode("0","今日は")]),
  │           ThoughtNode("3","徹夜したからだ。", children=[ThoughtNode("2","なぜなら")])
  │        ])
  │
  ├─ send(AnalysisUpdate.Progress(structure, partial=""))
  │   // ツリーは確定済み。LLM出力はまだ空
  │
  ├─ [Step 2: LLMプロンプト生成]
  │   promptBuilder.build(structure, emotionContext=null)
  │     → "以下の思考ツリーを分析して前提・感情・仮定・バイアス・欠落視点を抽出してください:\n..."
  │
  ├─ [Step 3: LLMストリーミング推論]
  │   llmService.analyzeThoughtStructure(prompt).collect { event ->
  │     LlmStreamEvent.Delta("前提") → partialBuilder="前提"
  │                                    send(Progress(structure, "前提"))
  │     LlmStreamEvent.Delta(": 徹夜した") → partialBuilder="前提: 徹夜した"
  │                                          send(Progress(structure, "前提: 徹夜した"))
  │     ...（トークンごとに繰り返す）
  │     LlmStreamEvent.Done(fullText="前提: 徹夜した\n感情: 疲れ\n仮定: ...") →
  │       │
  │       ├─ jsonParser.parse(fullText)
  │       │     → ThoughtAnalysisResult(
  │       │           premises=["徹夜した"],
  │       │           emotions=["疲れ"],
  │       │           assumptions=[Assumption("疲れているのは自分だけだ", 3, "他者の状況を確認する")],
  │       │           ...
  │       │        )
  │       │
  │       ├─ [Step 4: DB保存]（Dispatchers.IO）
  │       │   entryId が null の場合 → repository.storeEntry(null, text, treeJson, timestamp)
  │       │   entryId が指定済みの場合 → 既存エントリーを repository.updateEntry() で上書き
  │       │   repository.storeAnalysis(finalEntryId, resultJson, timestamp)
  │       │
  │       └─ send(AnalysisUpdate.Complete(structure, fullText, result))
  │
  └─ LlmStreamEvent.Error(message) → send(AnalysisUpdate.Error(message))
```

### 呼び出し側（ViewModel）が受け取るイベント列

```
AnalysisUpdate.Analyzing
AnalysisUpdate.Progress(structure=<ツリー確定>, partial="")
AnalysisUpdate.Progress(structure=<ツリー確定>, partial="前提")
AnalysisUpdate.Progress(structure=<ツリー確定>, partial="前提: 徹夜した")
AnalysisUpdate.Progress(structure=<ツリー確定>, partial="前提: 徹夜した\n感情: 疲れ")
...
AnalysisUpdate.Complete(structure=<ツリー確定>, fullText="...", result=ThoughtAnalysisResult(...))
```

### `entryId` 引数の使い分け

| 引数 | 動作 |
|---|---|
| `entryId = null`（デフォルト） | 新規エントリーとして `storeEntry()` |
| `entryId = 42L`（既存ID） | IDが存在すれば `updateEntry()`、存在しなければ `storeEntry()` |

---

## 2. `ManageConversationUseCase`

**会話の継続判定とDB保存**を担う。
「今の入力は前の話題の続きか？それとも新しい話題か？」を2段階で判定する。

### 依存関係

```
ManageConversationUseCase
  ├── ThoughtRepository      (アクティブ話題・エントリー取得・新規保存)
  ├── LogicalFlowService     (テキスト → LogicalFlowAnalysis)
  ├── TopicChangeService     (話題変化スコア算出 + LLMプロンプト生成)
  └── LLMInferenceService    (LLMによる話題変化判定)
```

### 戻り値の型（UseCaseローカル定義）

```kotlin
sealed class ConversationUpdateEvent {
    data class Analyzing(val message: String) : ConversationUpdateEvent()
    data class Done(val topicId: Long, val isNewTopic: Boolean, val entryId: Long) : ConversationUpdateEvent()
    data class Error(val message: String) : ConversationUpdateEvent()
}
```

### 処理の流れ

```
processInput(text="今日は上司に怒られた")
  │
  ├─ send(ConversationUpdateEvent.Analyzing("Starting Analysis..."))
  │
  ├─ [Step 1: 構造解析]
  │   flowService.analyze("今日は上司に怒られた")
  │     → currentFlow = LogicalFlowAnalysis(sentences=[...], relations=[...], ...)
  │   repository.getActiveTopic()
  │     → activeTopic = Topic(id=3, title="仕事のストレス")
  │         または null（初ターン）
  │
  ├─ [Case A: activeTopic == null（初ターン）]
  │   isNewTopic = true, suggestedTitle = "New Topic"
  │   → Step 3 へ
  │
  ├─ [Case B: activeTopic あり]
  │   lastEntry = repository.getLatestEntryForTopic(3)
  │     → Entry(rawText="昨日は仕事がうまくいった", ...)
  │   lastFlow  = flowService.analyze("昨日は仕事がうまくいった")
  │
  │   structuralScore = topicChangeService.evaluateStructuralChange(currentFlow, lastFlow)
  │     // 例: subjects重複なし → 1.0  / 重複あり → 0.2
  │
  │   ├─ [structuralScore <= 0.4: 同じ話題とみなす]
  │   │   isNewTopic = false
  │   │   → Step 3 へ
  │   │
  │   └─ [structuralScore > 0.4: 曖昧 → LLMに判定委ねる]
  │       send(ConversationUpdateEvent.Analyzing("Evaluating topic shift..."))
  │       prompt = topicChangeService.buildTopicChangePrompt(currentText, lastEntry.rawText)
  │       llmService.analyzeThoughtStructure(prompt)
  │         .first { it is LlmStreamEvent.Done }
  │         → LlmStreamEvent.Done(fullText="""{"is_new_topic":true,"suggested_title":"上司との衝突"}""")
  │       JSONObject(fullText)
  │         → isNewTopic = true, suggestedTitle = "上司との衝突"
  │
  ├─ [Step 3: DB保存]
  │   isNewTopic == true  → finalTopicId = repository.insertTopic("上司との衝突", timestamp)
  │   isNewTopic == false → finalTopicId = activeTopic.id (= 3)
  │
  │   entryId = repository.storeEntry(
  │       topicId  = finalTopicId,
  │       rawText  = "今日は上司に怒られた",
  │       treeJson = "{}",   // ← 係り受けツリーは別途 AnalyzeThoughtUseCase が保存
  │       timestamp = now
  │   )
  │   repository.updateTopicTimestamp(finalTopicId, timestamp)
  │
  └─ send(ConversationUpdateEvent.Done(topicId=5, isNewTopic=true, entryId=12))
```

### `structuralScore` の閾値設計

```
score ≤ 0.4 → LLM呼び出しなし（コスト0）で同一話題と判定
score > 0.4 → LLMに判定委ねる（Jaccard類似度だけでは曖昧なため）

// 例:
"私は仕事が大変だ。" → "私も疲れた。"   score=0.0 → LLM不要・同一話題
"田中が怒った。"     → "山田が笑った。"  score=1.0 → LLM呼び出し → is_new_topic 判定
"私は怒った。"       → "彼は謝った。"    score=0.5 → LLM呼び出し → 文脈で判断
```

---

## 3. `SystemDiagnosticUseCase`

アプリ起動時の**環境チェック**を行い、診断結果のリストを返す。
DBや非同期処理は一切なく、`runDiagnostic()` を呼ぶだけで即時結果が得られる。

### 依存関係

```
SystemDiagnosticUseCase
  ├── DictionaryManager      (MeCab辞書のインストール状況チェック)
  └── CabochaModelManager    (CaboChaモデルのインストール状況チェック)
  └── Context                (filesDir / Downloads ディレクトリの解決)
```

### 処理の流れ

```
runDiagnostic()
  │
  ├─ [チェック1: MeCab辞書]
  │   dictionaryManager.isInstalled()
  │     true  → DiagnosticMessage("✅ MeCab Dictionary: Installed",           isError=false)
  │     false → DiagnosticMessage("❌ MeCab Dictionary: Missing",             isError=true)
  │
  ├─ [チェック2: CaboChaモデル]
  │   cabochaModelManager.isInstalled()
  │     true  → DiagnosticMessage("✅ CaboCha Models: Installed",             isError=false)
  │     false → DiagnosticMessage("❌ CaboCha Models: Missing",               isError=true)
  │
  ├─ [チェック3: MediaPipe LLMモデル]
  │   resolveModelFile() で以下を順番に探索:
  │     filesDir/model.bin  → 存在すれば返す
  │     filesDir/model.task → 存在すれば返す
  │     Downloads/model.bin → 存在すれば返す
  │     Downloads/model.task → 存在すれば返す
  │   ファイルあり → DiagnosticMessage("✅ MediaPipe LLM: Found (2048MB)",    isError=false)
  │   ファイルなし → DiagnosticMessage("⚠️ MediaPipe LLM: Not found in ...", isError=true)
  │
  └─ return [DiagnosticMessage, DiagnosticMessage, DiagnosticMessage]
```

### 出力例

```
// 全て正常
[
  DiagnosticMessage("✅ MeCab Dictionary: Installed",   isError=false),
  DiagnosticMessage("✅ CaboCha Models: Installed",     isError=false),
  DiagnosticMessage("✅ MediaPipe LLM: Found (2048MB)", isError=false)
]

// 辞書未インストール・LLMモデル未配置
[
  DiagnosticMessage("❌ MeCab Dictionary: Missing",                              isError=true),
  DiagnosticMessage("✅ CaboCha Models: Installed",                             isError=false),
  DiagnosticMessage("⚠️ MediaPipe LLM: Not found in internal storage or Downloads", isError=true)
]
```

UIはこのリストを走査して `isError=true` の行を赤字・`false` を緑で表示するだけでよい。

---

## 3つの UseCase の関係

```
アプリ起動時
  └─ SystemDiagnosticUseCase.runDiagnostic()
       → 全てOKなら以降の処理が安全に動く

ユーザーがテキスト入力
  ├─ ManageConversationUseCase.processInput(text)
  │     「これは新しい話題か？」を判定してDBにエントリー作成
  │     → ConversationUpdateEvent.Done(topicId, isNewTopic, entryId)
  │
  └─ AnalyzeThoughtUseCase.execute(text, entryId=<上で得たID>)
        係り受け解析 → LLM解析 → DBに解析結果を上書き保存
        → AnalysisUpdate.Complete(structure, fullText, result)
```

`ManageConversationUseCase` が先にエントリーを作成し、
`AnalyzeThoughtUseCase` がそのIDを受け取って解析結果で埋める、という2段階になっている。
