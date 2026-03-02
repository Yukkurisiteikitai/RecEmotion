# Domain Model ドキュメント

`app/src/main/java/com/example/recemotion/domain/model/`

---

## 概要

このパッケージは、アプリ全体を流れる**データの設計図（型定義）**を担う。
ビジネスロジック・パーサー・UI のどの層も、ここで定義された型を共通言語として使う。

モデルは大きく3系統に分かれる。

| 系統 | ファイル | 役割 |
|---|---|---|
| **LLM ストリーミング系** | `LlmStreamEvent`, `InferenceProgress`, `AnalysisUpdate` | LLM推論の進行状態を表現 |
| **論理フロー解析系** | `LogicalFlowModels` | テキスト構造解析の中間・最終データ |
| **思想ツリー系** | `ThoughtNode`, `ThoughtStructure`, `ThoughtAnalysisResult` | 解析結果のUI向けツリー・LLM最終判定 |
| **補助型** | `BiasDetection`, `MissingPerspective`, `DiagnosticMessage` | 個別の分析要素・診断メッセージ |

---

## クラス詳細

---

### 1. LLM推論の進行 ── `InferenceProgress`

LLMモデルのロード・生成フェーズを追跡するための状態型。

```kotlin
enum class LlmStage { IDLE, LOADING, GENERATING, DONE, ERROR }

data class InferenceProgress(
    val stage: LlmStage,
    val current: Long,   // 現在進行量（例: ロード済みバイト数）
    val total: Long,     // 全体量
    val message: String  // UIに表示するメッセージ
)
```

**値の変化例（モデルロード → 推論 → 完了）：**

```
InferenceProgress(stage=IDLE,      current=0,        total=0,        message="待機中")
InferenceProgress(stage=LOADING,   current=52428800, total=104857600, message="モデル読込中... 50%")
InferenceProgress(stage=GENERATING,current=0,        total=0,        message="生成中...")
InferenceProgress(stage=DONE,      current=0,        total=0,        message="完了")
```

---

### 2. LLMストリーミングイベント ── `LlmStreamEvent`

LLMがトークンを逐次出力するとき、その断片を Kotlin Flow で流す sealed class。

```kotlin
sealed class LlmStreamEvent {
    data class Delta(val text: String) : LlmStreamEvent()   // トークン断片
    data class Done(val fullText: String) : LlmStreamEvent() // 全文テキスト
    data class Error(val message: String) : LlmStreamEvent() // エラー
}
```

**値の変化例（"今日は良い天気" を生成する場合）：**

```
LlmStreamEvent.Delta("今日")
LlmStreamEvent.Delta("は")
LlmStreamEvent.Delta("良い")
LlmStreamEvent.Delta("天気")
LlmStreamEvent.Done("今日は良い天気")

// エラー時
LlmStreamEvent.Error("OOM: モデルのロードに失敗しました")
```

---

### 3. 解析フローイベント ── `AnalysisUpdate`

`AnalyzeThoughtUseCase` が UseCase 層からプレゼンテーション層へ向けて放出する進行イベント。
`LlmStreamEvent` よりも上位の概念で、UI状態への変換を担うプレゼンテーション層が受け取る。

```kotlin
sealed class AnalysisUpdate {
    object Analyzing : AnalysisUpdate()                          // 解析開始
    data class Progress(
        val structure: ThoughtStructure,                         // 途中経過のツリー
        val partial: String                                      // 途中経過のテキスト
    ) : AnalysisUpdate()
    data class Complete(
        val structure: ThoughtStructure,                         // 完成したツリー
        val fullText: String,                                    // LLMの全出力テキスト
        val result: ThoughtAnalysisResult?                       // 構造化済み最終判定
    ) : AnalysisUpdate()
    data class Error(val message: String) : AnalysisUpdate()
}
```

**値の変化例（テキスト解析の一連の流れ）：**

```
AnalysisUpdate.Analyzing
  ↓ (LLM が少し出力した)
AnalysisUpdate.Progress(
    structure=ThoughtStructure(roots=[ThoughtNode("前提: ...", children=[])]),
    partial="前提: 天気が悪い"
)
  ↓ (LLM 完了)
AnalysisUpdate.Complete(
    structure=ThoughtStructure(roots=[...完成ツリー...]),
    fullText="前提: 天気が悪い\n感情: 不安\n仮定: ...",
    result=ThoughtAnalysisResult(premises=["天気が悪い"], emotions=["不安"], ...)
)

// エラー時
AnalysisUpdate.Error("LLM推論中に例外が発生しました: ...")
```

---

### 4. 論理フロー解析の中間・最終データ ── `LogicalFlowModels.kt`

このファイルは1ファイルに複数の型をまとめた **型の辞書** で、パーサー層とプレゼンテーション層が共有する。

#### 4a. 列挙型

```kotlin
// 文中に登場するエンティティの種類
enum class EntityType { PERSON("人物"), ORGANIZATION("組織/固有名詞"), CONCEPT("概念"), ACTION("行動") }

// 隣接する文の論理関係
enum class RelationType { TEMPORAL("時系列"), CAUSAL("因果"), CONTRAST("対比"), CONTINUATION("継続"), EXEMPLIFICATION("具体例") }

// ユーザーへの確認質問の種類
enum class QuestionType { FLOW_ORDER, SUBJECT_CHANGE, CAUSAL_LINK, IMPLICIT_LINK }
```

#### 4b. 形態素情報 ── `MorphemeInfo`

Kuromoji / CaboCha が出力する1形態素の情報。

```kotlin
data class MorphemeInfo(
    val surface: String,   // 表層形: "食べ"
    val pos: String,       // 品詞: "動詞"
    val pos2: String = "", // 品詞細分類: "自立"
    val baseForm: String = "" // 原形: "食べる"
)
```

**例：**
```
"私はリンゴを食べた。" → [
  MorphemeInfo("私",   "名詞", "代名詞",  "私"),
  MorphemeInfo("は",   "助詞", "係助詞",  "は"),
  MorphemeInfo("リンゴ","名詞", "一般",    "リンゴ"),
  MorphemeInfo("を",   "助詞", "格助詞",  "を"),
  MorphemeInfo("食べ", "動詞", "自立",    "食べる"),
  MorphemeInfo("た",   "助動詞","",       "た"),
  MorphemeInfo("。",   "記号", "句点",    "。")
]
```

#### 4c. 文構造 ── `SentenceStructure`

1文から抽出した主語・述語・目的語の三項組。

```kotlin
data class SentenceStructure(val subject: String, val verb: String, val obj: String = "")
```

**例：**
```
"私はリンゴを食べた。" → SentenceStructure(subject="私", verb="食べる", obj="リンゴ")
"今日は良い天気だ。"   → SentenceStructure(subject="",   verb="だ",    obj="")
// 主語なし（省略）の場合は空文字列
```

#### 4d. エンティティ ── `EntityInfo`

文中から抽出された固有名詞・人物・行動など。

```kotlin
data class EntityInfo(val type: EntityType, val value: String)
```

**例：**
```
"私はリンゴを食べた。" → [
  EntityInfo(PERSON, "私"),
  EntityInfo(ACTION, "食べる")
]
```

#### 4e. 解析済み文 ── `AnalyzedSentence`

1文の全解析結果をまとめたコンテナ。`MorphemeAnalyzer` が生成し、以降の処理がこれを参照する。

```kotlin
data class AnalyzedSentence(
    val sentenceId: Int,
    val originalText: String,
    val morphemes: List<MorphemeInfo>,
    val structure: SentenceStructure,
    val timeMarkers: List<String>,
    val entities: List<EntityInfo>
)
```

**例：**
```
AnalyzedSentence(
    sentenceId=0,
    originalText="昨日、私は会議でプレゼンをした。",
    morphemes=[...7形態素...],
    structure=SentenceStructure(subject="私", verb="する", obj="プレゼン"),
    timeMarkers=["昨日"],
    entities=[EntityInfo(PERSON,"私"), EntityInfo(ACTION,"する")]
)
```

#### 4f. 文間の論理関係 ── `LogicalRelation`

隣接する2文の関係を表す。`RelationDetector` が生成する。

```kotlin
data class LogicalRelation(
    val fromSentence: Int,
    val toSentence: Int,
    val relationType: RelationType,
    val connector: String,   // 検出された接続詞 ("なぜなら") or "implicit"
    val confidence: Int      // 信頼度 0-100
)
```

**例：**
```
"なぜなら徹夜したからだ。" が文1なら →
LogicalRelation(fromSentence=0, toSentence=1, relationType=CAUSAL, connector="なぜなら", confidence=95)

接続詞なしの場合 →
LogicalRelation(fromSentence=1, toSentence=2, relationType=CONTINUATION, connector="implicit", confidence=40)
```

#### 4g. 検証質問 ── `VerificationQuestion`

解析結果の不確かな部分についてユーザーに確認するための質問。
`LogicalFlowQuestionGenerator` が生成する。

```kotlin
data class VerificationQuestion(
    val id: Int,
    val type: QuestionType,
    val questionText: String,
    val relatedSentences: List<Int>,
    val options: List<String>
)
```

**例（信頼度が低い文間関係の確認）：**
```
VerificationQuestion(
    id=1,
    type=IMPLICIT_LINK,
    questionText="文1:「今日は疲れた...」\n→ 文2:「明日も頑張ろ...」\n\nこの2文のつながりはどのような関係ですか？",
    relatedSentences=[0, 1],
    options=["時系列（その後・次に）", "因果（だから・その結果）", "対比（しかし・一方）", "継続（また・そして）"]
)
```

#### 4h. ユーザー回答 ── `UserResponse`

ユーザーが `VerificationQuestion` に答えた内容。

```kotlin
data class UserResponse(
    val questionId: Int,
    val selectedOption: String,
    val questionType: QuestionType,
    val relatedSentences: List<Int>
)
```

**例：**
```
UserResponse(
    questionId=1,
    selectedOption="因果（だから・その結果）",
    questionType=IMPLICIT_LINK,
    relatedSentences=[0, 1]
)
```

#### 4i. 検証結果（文単位） ── `VerificationResult`

`LogicalFlowReportBuilder` がユーザー回答を踏まえて算出する、文ごとのスコア。

```kotlin
data class VerificationResult(
    val sentenceId: Int,
    val extractedFlow: String,       // "私 が 「リンゴ」を 食べる"
    val alignmentScore: Int,         // 0-100（ユーザー意図との一致度）
    val alignmentLabel: String,      // "✅ 一致" / "⚠️ 部分乖離" / "❌ 乖離"
    val discrepancy: String? = null  // 乖離の説明
)
```

**例：**
```
VerificationResult(
    sentenceId=0,
    extractedFlow="私 が 「プレゼン」を する",
    alignmentScore=85,
    alignmentLabel="⚠️ 部分乖離",
    discrepancy="一部の順序または内容に修正が必要"
)
```

#### 4j. 乖離情報 ── `Misalignment`

システムの解釈とユーザーの意図のズレを表す。最終レポートに含まれる。

```kotlin
data class Misalignment(
    val location: String,     // "文2" や "全体フロー"
    val issue: String,        // "因果として抽出されたが実際は時系列"
    val suggestion: String,   // "「その後」「次に」などを使用してください"
    val severity: String      // "critical" | "warning" | "info"
)
```

#### 4k. 解析結果コンテナ ── `LogicalFlowAnalysis`

フェーズ1〜2の解析出力をまとめるコンテナ。`LogicalFlowAnalyzerImpl` が返す。

```kotlin
data class LogicalFlowAnalysis(
    val sentences: List<AnalyzedSentence>,  // 文ごとの解析
    val relations: List<LogicalRelation>,   // 文間の論理関係
    val overallFlow: List<String>           // 概要フロー（"私 が 食べる" など）
)
```

#### 4l. 最終レポート ── `LogicalFlowReport`

全フェーズが完了した後の最終的な集約データ。`LogicalFlowReportBuilder.buildReport()` が返す。

```kotlin
data class LogicalFlowReport(
    val analysis: LogicalFlowAnalysis,                   // 元の解析結果
    val userResponses: List<UserResponse>,               // ユーザーの全回答
    val verificationResults: List<VerificationResult>,  // 文ごとのスコア
    val overallAlignmentScore: Int,                      // 総合一致度（0-100）
    val criticalMisalignments: List<Misalignment>        // 検出された乖離一覧
)
```

**例：**
```
LogicalFlowReport(
    analysis=...,
    userResponses=[UserResponse(0, "正しい", ...), UserResponse(1, "因果（...）", ...)],
    verificationResults=[
        VerificationResult(0, "私 が 食べる", 100, "✅ 一致", null),
        VerificationResult(1, "? が (述語不明)", 70, "⚠️ 部分乖離", "一部の順序に修正が必要")
    ],
    overallAlignmentScore=88,
    criticalMisalignments=[Misalignment("文2", "...", "...", "warning")]
)
```

---

### 5. 思想ツリー ── `ThoughtNode` / `ThoughtStructure`

係り受け解析結果を**再帰的なツリー**として表現する。UIでのツリー描画に使われる。

```kotlin
data class ThoughtNode(
    val id: String,                              // チャンクID ("0", "1", ...)
    val text: String,                            // 文節テキスト ("今日は")
    val children: List<ThoughtNode> = emptyList()
)

data class ThoughtStructure(
    val roots: List<ThoughtNode> = emptyList()   // ROOT文節（link=-1）の集合
)
```

**例（"今日は良い天気でした。"）：**

```
ThoughtStructure(
    roots=[
        ThoughtNode(id="1", text="良い天気でした。", children=[
            ThoughtNode(id="0", text="今日は", children=[])
        ])
    ]
)
```

**複数文の場合：**
```
ThoughtStructure(
    roots=[
        ThoughtNode(id="1", text="でした。",  children=[ThoughtNode("0","今日は",[])]),
        ThoughtNode(id="3", text="晴れます。", children=[ThoughtNode("2","明日も",[])])
    ]
)
// 各文のROOTが roots の要素になる
```

---

### 6. LLM最終判定 ── `ThoughtAnalysisResult` / `Assumption`

LLMがテキストを読んで構造化した分析結果。`@Parcelize` によりFragmentをまたいでも受け渡せる。

```kotlin
@Parcelize
data class ThoughtAnalysisResult(
    val premises: List<String> = emptyList(),              // 前提・根拠
    val emotions: List<String> = emptyList(),              // 感情
    val inferences: List<String> = emptyList(),            // 推論（旧フィールド）
    val statedFacts: List<String> = emptyList(),           // 明示された事実
    val assumptions: List<Assumption> = emptyList(),       // 仮定・思い込み
    val possibleBiases: List<BiasDetection> = emptyList(), // 認知バイアス
    val missingPerspectives: List<MissingPerspective> = emptyList() // 欠落した視点
) : Parcelable

@Parcelize
data class Assumption(
    val text: String,              // "明日も同じ結果になるだろう"
    val importance: Int,           // 重要度 1-5
    val verificationGoal: String   // "実際のデータで確認する"
) : Parcelable
```

**例：**
```
ThoughtAnalysisResult(
    premises=["昨日の会議でうまく話せなかった"],
    emotions=["恥ずかしい", "不安"],
    statedFacts=["参加者が10人いた", "プレゼンは30分だった"],
    assumptions=[
        Assumption(
            text="みんなが自分を批判していると思う",
            importance=4,
            verificationGoal="実際にフィードバックを確認する"
        )
    ],
    possibleBiases=[
        BiasDetection(name="読心術", evidence="「みんなが」という根拠のない他者の思考の推測")
    ],
    missingPerspectives=[
        MissingPerspective(description="聴衆側の視点：内容に集中していた可能性")
    ]
)
```

---

### 7. 補助型

#### `BiasDetection`

```kotlin
@Parcelize
data class BiasDetection(val name: String, val evidence: String) : Parcelable
```

LLMが検出した認知バイアスの名前と、その根拠テキスト。
`ThoughtAnalysisResult.possibleBiases` のリスト要素として使われる。

```
BiasDetection(name="破局化", evidence="「絶対に失敗する」という極端な予測")
BiasDetection(name="一般化", evidence="「いつも自分だけが...」という表現")
```

#### `MissingPerspective`

```kotlin
@Parcelize
data class MissingPerspective(val description: String) : Parcelable
```

LLMがテキストから「欠けている視点」を1つの文字列として表現したもの。

```
MissingPerspective("相手側の意図について考慮されていない")
MissingPerspective("長期的な影響についての検討がない")
```

#### `DiagnosticMessage`

```kotlin
data class DiagnosticMessage(val text: String, val isError: Boolean = false)
```

システムの診断ログやエラーメッセージをドメイン層で扱うための軽量型。
プレゼンテーション層がUIのトースト・バナー等に変換する。

```
DiagnosticMessage("辞書のインストールが完了しました", isError=false)
DiagnosticMessage("CaboCha初期化に失敗しました。Kuromojiにフォールバックします", isError=true)
```

---

## データの流れ（全体像）

```
テキスト入力
    │
    ▼
[パーサー層が生成]
MorphemeInfo（形態素）
SentenceStructure（主語・述語・目的語）
EntityInfo（エンティティ）
AnalyzedSentence（1文の全解析）
LogicalRelation（文間関係）
LogicalFlowAnalysis（解析結果の集約）
    │
    ▼
[CabochaThoughtMapperが変換]
ThoughtNode ──┐
ThoughtStructure ─┘  ← UIのツリー描画に使用
    │
    ▼
[LLMが推論]
LlmStreamEvent.Delta → AnalysisUpdate.Progress（途中経過）
LlmStreamEvent.Done  → AnalysisUpdate.Complete（最終）
    │
    ▼
[最終成果物]
ThoughtAnalysisResult（前提・感情・バイアス・欠落視点）
    ├── Assumption（仮定・思い込み）
    ├── BiasDetection（認知バイアス）
    └── MissingPerspective（欠落した視点）
    │
    ▼
[ユーザー確認フロー]
VerificationQuestion（確認質問）
UserResponse（ユーザー回答）
VerificationResult（文単位のスコア）
Misalignment（乖離情報）
LogicalFlowReport（最終レポート）
```
