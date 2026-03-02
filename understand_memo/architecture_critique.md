# クリーンアーキテクチャ設計 批評

対象: `domain/model/`, `domain/service/`, `domain/usecase/`, `data/parser/`

---

## 重大な違反（アーキテクチャ原則に反する）

---

### 1. UseCase が data 層の具体クラスに直接依存している

`AnalyzeThoughtUseCase` のコンストラクタ：

```kotlin
class AnalyzeThoughtUseCase @Inject constructor(
    private val parser: DependencyParser,           // ✅ インターフェース
    private val mapper: CabochaThoughtMapper,        // ❌ data層の具体クラス
    private val promptBuilder: ThoughtPromptBuilder, // ❌ data層の具体クラス
    private val jsonParser: ThoughtAnalysisJsonParser,   // ❌ data層の具体クラス
    private val serializer: ThoughtStructureJsonAdapter  // ❌ data層の具体クラス
)
```

7依存のうち4つがインターフェースを介さずに具体実装に直接依存している。
依存性逆転原則（DIP）の最も基本的な違反で、
`CabochaThoughtMapper` の実装を変えた瞬間に UseCase 側もコンパイルエラーになる。

**あるべき形：**
```
UseCase → IThoughtMapper（domain/service）← CabochaThoughtMapper（data層が実装）
```

---

### 2. domain/model に Android 固有型が混入している

```kotlin
@Parcelize
data class ThoughtAnalysisResult(...) : Parcelable  // ❌

@Parcelize
data class BiasDetection(...) : Parcelable           // ❌

@Parcelize
data class MissingPerspective(...) : Parcelable      // ❌

@Parcelize
data class Assumption(...) : Parcelable              // ❌
```

`@Parcelize` / `Parcelable` は Android Framework の型であり、domain 層に置くべきでない。
ドメインモデルは Android に依存してはならず、JVM 単体でテストできることがクリーンアーキテクチャの大前提。

**影響：**
- ドメイン層のユニットテストに Android テストランナーが必要になる
- 将来 Compose Navigation の `@Serializable` 等への移行が困難になる

**正しい対処：** Fragment 間の受け渡しが必要なら presentation 層に専用の UI モデルを作り、そこで `Parcelable` を実装する。

---

### 3. `LLMInferenceService` インターフェースに実装詳細が漏れている

```kotlin
interface LLMInferenceService {
    val partialResults: SharedFlow<String>     // ❌ 具体型
    val progress: StateFlow<InferenceProgress> // ❌ 具体型
    fun initModel()                            // ❌ インフラのライフサイクル管理
    fun isModelInitialized(): Boolean          // ❌ 同上
    fun close()                                // ❌ リソース解放
}
```

3つの問題が混在している。

**問題①** `SharedFlow` / `StateFlow` はインフラ寄りの具体型。純粋なドメインインターフェースは `Flow<T>` のみで表現できる。

**問題②** `initModel()`, `isModelInitialized()`, `close()` はリソース管理（インフラの関心事）で、ドメインサービスが「モデルをどう初期化するか」を知る必要はない。Hilt のスコープ管理や `AutoCloseable` に委ねるべき。

**問題③** `generateResponse()`（副作用で SharedFlow に書き込む）と `analyzeThoughtStructure()`（戻り値 Flow）という非一貫な2種類の推論メソッドが1インターフェースに混在している。

---

## 中程度の問題（設計上の課題）

---

### 4. `ConversationUpdateEvent` が UseCase ファイル内に定義されている

```kotlin
// ManageConversationUseCase.kt の末尾に同居している
sealed class ConversationUpdateEvent {
    data class Analyzing(val message: String) : ConversationUpdateEvent()
    data class Done(val topicId: Long, val isNewTopic: Boolean, val entryId: Long) : ConversationUpdateEvent()
    data class Error(val message: String) : ConversationUpdateEvent()
}
```

`AnalysisUpdate` は `domain/model/AnalysisUpdate.kt` に独立ファイルとして置かれているのに、
`ConversationUpdateEvent` だけが UseCase ファイルに同居しており、パッケージの一貫性がない。
`domain/model/ConversationUpdateEvent.kt` として独立させるべき。

---

### 5. `ManageConversationUseCase` が `TopicChangeService` の責務を侵食している

```kotlin
// UseCase 内部で LLM の入出力フォーマットを直接制御している
val prompt = topicChangeService.buildTopicChangePrompt(text, lastEntry.rawText)
val llmResult = llmService.analyzeThoughtStructure(prompt)
    .first { it is LlmStreamEvent.Done } as LlmStreamEvent.Done
val json = JSONObject(llmResult.fullText)
isNewTopic = json.getBoolean("is_new_topic")  // ← JSONキー名まで UseCase が知っている
```

`TopicChangeService` の責務は「話題変化を判定する」はずだが、実際には「プロンプトを組み立てるだけ」に留まり、
LLM 呼び出しと JSON パースを UseCase が肩代わりしている。

**本来 `TopicChangeService` に追加すべきメソッド：**
```kotlin
suspend fun detectTopicChange(currentText: String, previousText: String): TopicChangeResult
// LLM呼び出し・JSONパースを完全に隠蔽する
```

---

### 6. `SystemDiagnosticUseCase` が data 層の具体クラスに直接依存

```kotlin
class SystemDiagnosticUseCase @Inject constructor(
    private val dictionaryManager: DictionaryManager,    // ❌ 具体クラス
    private val cabochaModelManager: CabochaModelManager // ❌ 具体クラス
)
```

`isInstalled()` を宣言した `ResourceInstallChecker` 等のインターフェースを介すべきだった。
`AnalyzeThoughtUseCase` と同じ DIP 違反。

---

### 7. `LogicalFlowAnalysis.overallFlow` に表示用文字列が混入

```kotlin
data class LogicalFlowAnalysis(
    val sentences: List<AnalyzedSentence>,
    val relations: List<LogicalRelation>,
    val overallFlow: List<String>  // "私 が 「リンゴ」を 食べる" ← 日本語UI文字列
)
```

`overallFlow` の値は `"(主語不明) が (述語不明)"` のような**表示フォーマット済みの日本語文字列**で、
presentation 層の関心事がドメインモデルに漏れている。
ドメインモデルは言語・表示形式に依存してはならない。
正しくは `sentences` から ViewModel が必要に応じて生成すべき。

---

## 軽度の問題（コード品質）

---

### 8. `LogicalFlowAnalyzer`（旧実装）がデッドコードとして残存

`LogicalFlowAnalyzerImpl` が現役実装として使われているにもかかわらず、
前身の `LogicalFlowAnalyzer` が同一パッケージに残存している。
両クラスは `SentenceTokenizer` / `MorphemeAnalyzer` / `RelationDetector` への分離前の実装で、
現在は完全なデッドコード。新規参入者の混乱の元。

---

### 9. `ManageConversationUseCase` の空 JSON による整合性リスク

```kotlin
val entryId = repository.storeEntry(
    topicId  = finalTopicId,
    rawText  = text,
    treeJson = "{}",  // ← 空JSON で先にエントリー作成
    timestamp = timestamp
)
// ↑ この後 AnalyzeThoughtUseCase が上書きする前にクラッシュしたら空JSONが残る
```

`AnalyzeThoughtUseCase` の実行前にアプリがクラッシュした場合、
DB に空 JSON エントリーが残り続け、その状態を検出・リカバリーする仕組みがない。

---

### 10. `LogicalFlowModels.kt` の過密な型定義

1ファイルに12の型定義（列挙型3 + データクラス9）が詰め込まれている。
`VerificationQuestion`, `UserResponse`, `VerificationResult`, `Misalignment`, `LogicalFlowReport` は
検証・レポート機能固有の型で、解析系の `MorphemeInfo`, `AnalyzedSentence`, `LogicalRelation` とは関心が異なる。
2ファイル程度に分割することで可読性が改善する。

---

### 11. `VerificationQuestion` / `LogicalFlowReport` 系の型が未接続

`LogicalFlowQuestionGenerator` と `LogicalFlowReportBuilder` が実装されているが、
現在どの UseCase からも呼ばれていない（未接続の機能）。
`VerificationQuestion`, `UserResponse`, `VerificationResult`, `LogicalFlowReport` がドメインモデルに存在するにもかかわらず、
実際のフローには組み込まれていない。設計だけ先行した未使用コードとして残存しており、
メンテナンスコストが発生し続ける。

---

## まとめ表

| # | 問題 | 該当箇所 | 深刻度 |
|---|---|---|---|
| 1 | UseCase が data 層具体クラスに直接依存 | `AnalyzeThoughtUseCase` | 🔴 重大 |
| 2 | domain/model に `@Parcelize`/`Parcelable` 混入 | `ThoughtAnalysisResult` 等4クラス | 🔴 重大 |
| 3 | `LLMInferenceService` に実装詳細（SharedFlow/close）露出 | `LLMInferenceService` | 🔴 重大 |
| 4 | `ConversationUpdateEvent` が UseCase ファイルに同居 | `ManageConversationUseCase.kt` | 🟡 中 |
| 5 | `TopicChangeService` の責務が UseCase に漏れ | `ManageConversationUseCase` | 🟡 中 |
| 6 | `SystemDiagnosticUseCase` が具体クラスに依存 | `SystemDiagnosticUseCase` | 🟡 中 |
| 7 | `overallFlow` が表示用文字列としてドメインモデルに混入 | `LogicalFlowAnalysis` | 🟡 中 |
| 8 | `LogicalFlowAnalyzer` 旧実装の残存 | `data/parser/` | 🟢 軽度 |
| 9 | `treeJson="{}"` による整合性リスク | `ManageConversationUseCase` | 🟢 軽度 |
| 10 | `LogicalFlowModels.kt` の過密 | `domain/model/` | 🟢 軽度 |
| 11 | `VerificationQuestion` 系の未接続コード | `domain/model/`, `data/parser/` | 🟢 軽度 |

**最優先で直すべき3点**は #1・#2・#3 で、いずれも後から修正するほど波及範囲が広がる問題。
