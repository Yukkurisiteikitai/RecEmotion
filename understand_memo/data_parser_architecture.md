# data/parser アーキテクチャ

対象パス: `app/src/main/java/com/example/recemotion/data/parser/`

---

## 1. 実行順序

パーサーモジュールは **初期化フェーズ** と **解析フェーズ** の 2 段階で動作する。

### 初期化フェーズ（アプリ起動時）

```
1. DictionaryManager.install()
       → assets/ipadic/* を filesDir/ipadic/ へコピー（MeCab 辞書）

2. CabochaModelManager.install()
       → assets/cabocha_model/* を filesDir/cabocha_model/ へコピー（CaboCha モデル）

3. NativeCabochaParser の生成
       → System.loadLibrary("cabocha_jni") でネイティブライブラリをロード
       → mecabDicDir, cabochaModelDir を渡してインスタンス化
```

辞書・モデルが未インストールの場合、`NativeCabochaParser` は生成されず、
後続の解析は Kuromoji（純 Kotlin）のフォールバックで動作する。

---

### 解析フェーズ（ユーザー入力時）

```
入力テキスト "今日は良い天気でした。それから散歩に行った。"

① SentenceTokenizer.split(text)
       → ["今日は良い天気でした。", "それから散歩に行った。"]

② MorphemeAnalyzer.analyze(idx, sentence, nativeParser?)
   ┌─ nativeParser が存在する場合:
   │    NativeCabochaParser.parse(sentence)   ← JNI 経由で本物の CaboCha を呼ぶ
   │        → nativeParse() が JSON を返す
   │        → parseJson() で CabochaResult（CabochaChunk[] + CabochaToken[]）に変換
   │    チャンクから 主語/述語/目的語/時間マーカー/エンティティ を抽出
   │
   └─ nativeParser が null の場合:
        Kuromoji Tokenizer.tokenize(sentence)  ← JVM 上で動作
        → Token[] から 主語/述語/目的語/時間マーカー/エンティティ を抽出
        （各文が AnalyzedSentence に格納される）

③ RelationDetector.detect(analyzedSentences, rawSentences)
       → 隣接する文の先頭・内部から接続詞を検索
       → 時系列/因果/対比/継続/具体例 の LogicalRelation[] を生成

④ LogicalFlowAnalyzerImpl が各結果を統合
       → LogicalFlowAnalysis(sentences, relations, overallFlow) を返す
```

依存解析（係り受けツリー）が必要な場合は別途以下が実行される：

```
⑤ DependencyParser.parse(text)
       → CabochaDependencyParser（Kuromoji 近似）または NativeCabochaParser

⑥ CabochaThoughtMapper.map(cabochaResult)
       → CabochaResult の chunk.link を辿り ThoughtStructure（木構造）に変換
```

---

## 2. クラスの実行で目的をどう達成しているのか

### 目的: 日本語テキストの構造・論理フローを機械的に解析する

| フェーズ | クラス | 目的達成の仕組み |
|---------|--------|----------------|
| 辞書管理 | `DictionaryManager` | MeCab が `mmap()` でファイルシステム上の辞書を必要とするため、assets から filesDir へコピーしてアクセス可能にする |
| モデル管理 | `CabochaModelManager` | 同様に CaboCha モデルファイルを filesDir へ展開し、NativeCabochaParser に渡せる状態にする |
| ネイティブ解析 | `NativeCabochaParser` | JNI 経由で本物の CaboCha を呼び出す。結果は JSON で受け取り、Kotlin の `CabochaResult` に変換する。辞書未インストール時は空結果を返すグレースフルデグレード設計 |
| 近似解析 | `CabochaDependencyParser` | 辞書不要の Kuromoji でハブ＆スポーク構造（全文節が文末述語にリンク）を生成する。日本語の SOV 語順に基づく言語的に妥当な近似 |
| 構文データ定義 | `CabochaResult / Chunk / Token` | パーサーの出力形式を統一する軽量データクラス。`CabochaChunk.text` は `tokens` から動的に生成 |
| ドメインマッピング | `CabochaThoughtMapper` | チャンクの `link` フィールドを使って親子関係を再帰的に構築し `ThoughtStructure` へ変換。旧実装の「参照が古くなるバグ」はチャンクIDのみ記録→再帰ビルドで解決 |
| 文分割 | `SentenceTokenizer` | 句読点（。！？.!?）を境界として文字列を走査し、1 文ずつ切り出す |
| 形態素・構造解析 | `MorphemeAnalyzer` | 1 文に対して「は/が の直前 = 主語」「を の直前 = 目的語」「文末の自立動詞 = 述語」というルールで SVO を抽出。nativeParser の有無で実装を切り替える |
| 関係検出 | `RelationDetector` | 隣接文の「先頭一致（高信頼）→ 内部一致（信頼度 80% 降格）→ 暗黙（40%）」の 3 段階で接続詞辞書を適用する |
| 論理フロー統合 | `LogicalFlowAnalyzerImpl` | `SentenceTokenizer` → `MorphemeAnalyzer` → `RelationDetector` の 3 コンポーネントを DI で受け取り、パイプライン的に組み合わせる |
| 話題変化検出 | `TopicChangeDetectorImpl` | Jaccard 類似度（主語集合の積集合 / 和集合）で構造的変化を数値化（0.0〜1.0）し、曖昧なケースは LLM プロンプトを構築して委譲する |
| 検証質問生成 | `LogicalFlowQuestionGenerator` | 解析結果から「全体フロー確認」「暗黙の関係確認」「因果強度確認」「主語変化確認」の 4 種類の質問を自動生成する |
| レポート構築 | `LogicalFlowReportBuilder` | Phase1（構造テキスト）/ Phase4（乖離スコア算出）/ Phase5（最終レポート）の 3 段階を担当 |
| 比較デバッグ | `ParserComparisonLogger` | Kuromoji と NativeCaboCha の両方で同一テキストを解析し、チャンク数・リンク一致率・実行時間をログ出力する開発用ユーティリティ |

---

## 3. 目的適合の理由

### Kuromoji 近似 ＋ NativeCaboCha の二段構え

Android は NDK 依存ライブラリのインストールが必要なため、初回起動時に辞書が存在しない状態が発生しうる。
`NativeCabochaParser` が利用できない間は `CabochaDependencyParser`（純 JVM）が代替することで、**アプリが最初から解析機能を提供できる**。
両者は共通の `DependencyParser` インターフェースを実装しており、呼び出し側はどちらか意識しなくてよい。

### SOV 言語向けのハブ＆スポーク構造

日本語は「述語が文末」の SOV 言語であるため、文末文節を根ノードとし、他のすべての文節がそこへ係る構造は言語的に自然。
旧実装の「全形態素を線形チェーン」は文節内の接続にしかならず、係り受け構造を表現できなかった（`CabochaDependencyParser` のコメントに修正記録あり）。

### 接続詞辞書の信頼度スコア付き設計

文頭一致は「その後 → 95%」のように高信頼とし、文内一致は 80% に降格させることで、
明示的な接続詞がある場合と暗黙的な場合を定量的に区別できる。
これにより `LogicalFlowQuestionGenerator` が「信頼度 < 60% の関係だけ確認質問を出す」という**コスト効率のよいインタラクション**を実現している。

### 再帰ビルドによる ThoughtNode の正確な構築

旧実装は `MutableList` に格納した `ThoughtNode` への参照を `childrenMap` に保存し後から更新していたが、
Kotlin の `data class` はコピーオブジェクトを返すため参照が一致せず children が常に空になっていた。
ID のみ記録して `buildNode()` で再帰的に構築する方式に変更することで正確なツリーを生成している。

---

## 4. クラスの依存関係

```
┌──────────────────────────────────────────────────────────────┐
│  data/parser 層                                              │
│                                                              │
│  DictionaryManager ──────────────────────────────────────┐  │
│  CabochaModelManager ────────────────────────────────┐   │  │
│                                                       │   │  │
│  ┌────────────────────────┐    ┌──────────────────────┴───┴┐ │
│  │ DependencyParser (if)  │    │ NativeCabochaParser        │ │
│  └────────┬───────────────┘    │  - mecabDicDir             │ │
│           │implements          │  - cabochaModelDir         │ │
│    ┌──────┴──────────────┐     └──────────────┬────────────┘ │
│    │CabochaDependency    │                     │ optional     │
│    │Parser (Kuromoji近似)│     ┌───────────────┘             │
│    └─────────────────────┘     │                             │
│                                ↓                             │
│    CabochaResult ←─────── MorphemeAnalyzer ──────────────────┤
│    CabochaChunk              (uses nativeParser?)            │
│    CabochaToken                        ↑                     │
│                                        │                     │
│    CabochaThoughtMapper ──────────────-┤                     │
│       → ThoughtStructure (domain)      │                     │
│                                        │                     │
│    SentenceTokenizer ──────────────────┤                     │
│    RelationDetector  ──────────────────┤                     │
│                                        │                     │
│    LogicalFlowAnalyzerImpl ────────────┘                     │
│      implements LogicalFlowService (domain.service)          │
│      depends on: SentenceTokenizer,                          │
│                  MorphemeAnalyzer,                           │
│                  RelationDetector                            │
│                                                              │
│    TopicChangeDetectorImpl                                   │
│      implements TopicChangeService (domain.service)          │
│      uses: LogicalFlowAnalysis (domain.model)                │
│                                                              │
│    LogicalFlowQuestionGenerator (uses LogicalFlowAnalysis)   │
│    LogicalFlowReportBuilder     (uses LogicalFlowAnalysis)   │
│                                                              │
│    ParserComparisonLogger (dev tool / object)                │
│      depends on: CabochaDependencyParser,                    │
│                  NativeCabochaParser?                        │
│                                                              │
│  [Legacy / 非推奨]                                           │
│    LogicalFlowAnalyzer   ← LogicalFlowAnalyzerImpl に統合済み │
│    TopicChangeDetector   ← TopicChangeDetectorImpl に統合済み │
└──────────────────────────────────────────────────────────────┘
```

### 外部依存

| ライブラリ | 使用クラス | 役割 |
|-----------|-----------|------|
| `com.atilika.kuromoji:kuromoji-ipadic` | CabochaDependencyParser, MorphemeAnalyzer, LogicalFlowAnalyzer | 純 JVM 形態素解析 |
| NDK `libcabocha_jni.so` | NativeCabochaParser | ネイティブ係り受け解析 |
| Android `Context` | DictionaryManager, CabochaModelManager | assets/filesDir アクセス |
| `javax.inject.Inject`, Hilt | 各クラス | DI |

---

## 5. クラスの使用例とユースケース

### ユースケース 1: テキストの論理フロー解析

```kotlin
// DI 注入済みの LogicalFlowAnalyzerImpl を使う
val analyzer: LogicalFlowService = LogicalFlowAnalyzerImpl(
    sentenceTokenizer = SentenceTokenizer(),
    morphemeAnalyzer = MorphemeAnalyzer(),
    relationDetector = RelationDetector()
)
// NativeCaboCha が利用可能なら設定
(analyzer as LogicalFlowAnalyzerImpl).nativeParser = nativeCabochaParser

val text = "私はリンゴを食べた。それから散歩に行った。しかし疲れてしまった。"
val analysis: LogicalFlowAnalysis = analyzer.analyze(text)

// analysis.sentences: [
//   AnalyzedSentence(id=0, subject="私", verb="食べる", obj="リンゴ", ...)
//   AnalyzedSentence(id=1, subject="", verb="行く", obj="", timeMarkers=["それから"], ...)
//   AnalyzedSentence(id=2, subject="", verb="疲れる", ...)
// ]

// analysis.relations: [
//   LogicalRelation(from=0, to=1, type=TEMPORAL, connector="それから", confidence=95)
//   LogicalRelation(from=1, to=2, type=CONTRAST, connector="しかし", confidence=95)
// ]

// analysis.overallFlow: [
//   "私 が 「リンゴ」を 食べる",
//   "[それから] (主語不明) が 行く",
//   "(主語不明) が 疲れる"
// ]
```

### ユースケース 2: 係り受けツリーへのマッピング

```kotlin
val parser: DependencyParser = CabochaDependencyParser()
val result: CabochaResult = parser.parse("今日は良い天気でした。")

// result.chunks:
//   CabochaChunk(id=0, link=2, tokens=[CabochaToken("今日", "名詞"), CabochaToken("は", "助詞")])
//   CabochaChunk(id=1, link=2, tokens=[CabochaToken("良い", "形容詞"), CabochaToken("天気", "名詞")])
//   CabochaChunk(id=2, link=-1, tokens=[CabochaToken("でし", "助動詞"), CabochaToken("た", "助動詞"), CabochaToken("。", "記号")])
//   ※ id=2 が ROOT（link=-1）、id=0 と id=1 が ROOT へリンク

val mapper = CabochaThoughtMapper()
val structure: ThoughtStructure = mapper.map(result)

// structure.roots[0]:
//   ThoughtNode(id="2", text="でした。", children=[
//     ThoughtNode(id="0", text="今日は"),
//     ThoughtNode(id="1", text="良い天気")
//   ])
```

### ユースケース 3: 話題変化の検出

```kotlin
val detector: TopicChangeService = TopicChangeDetectorImpl()

val previousAnalysis: LogicalFlowAnalysis = analyzer.analyze("私は仕事で疲れた。上司に叱られた。")
val currentAnalysis:  LogicalFlowAnalysis = analyzer.analyze("今日は天気が良い。猫が庭で遊んでいる。")

val changeScore: Double = detector.evaluateStructuralChange(currentAnalysis, previousAnalysis)
// 主語集合 {私} と {} の Jaccard = 0.0 → changeScore = 1.0（高確率で話題変化）

if (changeScore > 0.5) {
    // 意味的確認が必要な場合は LLM へ問い合わせる
    val prompt = detector.buildTopicChangePrompt(currentText, previousText)
    // → LLM に送信して is_new_topic を確認
}
```

### ユースケース 4: 検証質問の生成とレポート作成

```kotlin
val generator = LogicalFlowQuestionGenerator()
val reportBuilder = LogicalFlowReportBuilder()

// Phase 1: 解析結果テキストを表示
println(reportBuilder.buildPhase1Report(analysis))

// Phase 3: 検証質問を UI に表示してユーザーが回答
val questions: List<VerificationQuestion> = generator.generateQuestions(analysis)
// → questions に FLOW_ORDER, IMPLICIT_LINK, CAUSAL_LINK, SUBJECT_CHANGE の質問が含まれる

val userResponses: List<UserResponse> = collectUserAnswers(questions)

// Phase 4: 乖離分析
val report: LogicalFlowReport = reportBuilder.buildReport(analysis, questions, userResponses)
// report.overallAlignmentScore: 0〜100 のスコア
// report.criticalMisalignments: 乖離箇所と改善提案

// Phase 5: 最終レポート
println(reportBuilder.buildFinalReport(report))
```

### ユースケース 5: パーサー比較（開発/デバッグ用）

```kotlin
val result = ParserComparisonLogger.compare(
    text = "機械学習を使って感情を認識するアプリを開発しています。",
    kuromojiParser = CabochaDependencyParser(),
    nativeParser = nativeCabochaParser
)
// Logcat に以下が出力される:
// [Kuromoji] 18ms  チャンク数=4
//   chunk[0] "機械学習を" → chunk[3]
//   chunk[1] "使って" → chunk[3]
//   chunk[2] "感情を" → chunk[3]
//   chunk[3] "開発しています。" ROOT
// [Native CaboCha] 142ms  チャンク数=5
//   ...
// リンク一致率: 75.0%
```
