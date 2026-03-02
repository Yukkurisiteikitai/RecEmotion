# Parser モジュール 設計ドキュメント

`app/src/main/java/com/example/recemotion/data/parser/`

---

## 概要

このパッケージは、日本語テキストの**構文解析・論理構造抽出**を担う。
主な目的は「ユーザーが話した・書いたテキストを、係り受け構造・主語・述語・文間の論理関係に分解する」こと。

### 2種類のパーサー（戦略パターン）

| パーサー | 使用ライブラリ | 精度 | 利用条件 |
|---|---|---|---|
| `CabochaDependencyParser` | Kuromoji (JVM) | 近似（ハブ&スポーク） | 常時利用可能 |
| `NativeCabochaParser` | CaboCha (NDK/JNI) | 高精度 | 辞書インストール後のみ |

処理全体は「どちらのパーサーを使うか」に関わらず同じインターフェース (`DependencyParser`) を通じて行われる。

---

## クラス一覧と責務

```
初期化系
  DictionaryManager      - MeCab辞書のassets→filesDir展開
  CabochaModelManager    - CaboChaモデルのassets→filesDir展開

解析インターフェース
  DependencyParser       - 構文解析の抽象インターフェース

解析実装
  CabochaDependencyParser - Kuromojiベースの近似実装
  NativeCabochaParser     - JNI経由の本格CaboCha実装

データモデル
  CabochaResult          - 解析結果の共通データ構造
  CabochaChunk           - 文節（bunsetsu）単位
  CabochaToken           - 形態素（単語）単位

高レベル解析パイプライン
  SentenceTokenizer      - テキスト→文リストへの分割
  MorphemeAnalyzer       - 1文の形態素・構造解析
  RelationDetector       - 文間の論理関係検出
  LogicalFlowAnalyzerImpl- 上記3クラスを統合するサービス実装

思想マッピング
  CabochaThoughtMapper   - CabochaResult → ThoughtStructure（ツリー構造）への変換

補助・旧実装
  LogicalFlowAnalyzer    - LogicalFlowAnalyzerImplの前身（直接Kuromojiを使う実装）
  LogicalFlowQuestionGenerator - 解析結果から検証質問を自動生成
  LogicalFlowReportBuilder    - ユーザー回答と解析結果から乖離レポートを構築
  TopicChangeDetector    - 話題変化検出（プロトタイプ）
  TopicChangeDetectorImpl- 話題変化検出の具体実装
  ParserComparisonLogger - 2パーサーの結果を比較・ベンチマークするデバッグツール
```

---

## 実行の流れ

### フェーズ 0：初期化（アプリ起動時）

**担当：`DictionaryManager`, `CabochaModelManager`**

MeCab・CaboChaは mmap() でファイルシステム上のバイナリを読むため、apkのassetsから直接は使えない。
初回起動時にファイルを `filesDir` へコピーする。

```
DictionaryManager.isInstalled() == false の場合
  ↓
DictionaryManager.install()
  assets/ipadic/sys.dic → filesDir/ipadic/sys.dic  (など9ファイル)
  assets/ipadic/matrix.bin → filesDir/ipadic/matrix.bin

CabochaModelManager.isInstalled() == false の場合
  ↓
CabochaModelManager.install()
  assets/cabocha_model/chunk.ipa.model → filesDir/cabocha_model/chunk.ipa.model  (~20MB)
  assets/cabocha_model/dep.ipa.model   → filesDir/cabocha_model/dep.ipa.model    (~41MB)
  assets/cabocha_model/ne.ipa.model    → filesDir/cabocha_model/ne.ipa.model     (~20MB)
```

インストール後、`NativeCabochaParser` がそのパスを受け取って使用する。

---

### フェーズ 1：テキストの文分割

**担当：`SentenceTokenizer`**

```kotlin
SentenceTokenizer().split("今日は良い天気でした。明日も晴れるといいな。")
```

`。！？.!?` を区切りとして文ごとに分割する。

```
入力: "今日は良い天気でした。明日も晴れるといいな。"
出力: ["今日は良い天気でした。", "明日も晴れるといいな。"]
```

---

### フェーズ 2：構文解析（係り受け解析）

**担当：`CabochaDependencyParser` or `NativeCabochaParser`**
両者とも `DependencyParser` インターフェースを実装している。

#### 2a. Kuromojiベース実装（`CabochaDependencyParser`）

日本語がSOV言語（述語が文末）であることを利用し、**ハブ&スポーク構造**で近似する。

```
入力: "今日は良い天気でした。"

Step 1: Kuromojiで形態素分解
  [今日][は][良い][天気][で][し][た][。]

Step 2: 文節（bunsetsu）境界で区切る
  文節境界: 助詞・助動詞・記号の後
  → ["今日は", "良い天気でした。"]

Step 3: 文末文節をROOT、それ以外を全部ROOTへリンク
  chunk[0] id=0 text="今日は"      link=1   (→ 文末文節へ)
  chunk[1] id=1 text="良い天気でした。" link=-1  (ROOT)
```

出力 `CabochaResult`:
```
CabochaResult(
  chunks=[
    CabochaChunk(id=0, link=1, tokens=[CabochaToken("今日","名詞"), CabochaToken("は","助詞")]),
    CabochaChunk(id=1, link=-1, tokens=[CabochaToken("良い","形容詞"), CabochaToken("天気","名詞"), ...])
  ]
)
```

**複数文の場合：** 句読点で文を区切り、各文に独立したハブ&スポーク構造を付与する。
例: "今日は良い天気でした。明日も晴れます。" → チャンクID 0〜3 で2つの独立した木構造。

#### 2b. NativeCabochaベース実装（`NativeCabochaParser`）

JNI経由でC++のCaboChaを呼び出し、真の係り受け解析を行う。
結果はJSON文字列で返され、Kotlinでパースする。

```
nativeParse(mecabDicDir, cabochaModelDir, "彼女が書いた手紙は美しかった。")
↓ (C++ CaboCha)
JSON:
{
  "chunks": [
    {"id":0, "link":2, "tokens":[{"surface":"彼女","pos":"名詞"},{"surface":"が","pos":"助詞"}]},
    {"id":1, "link":2, "tokens":[{"surface":"書い","pos":"動詞"},{"surface":"た","pos":"助動詞"}]},
    {"id":2, "link":3, "tokens":[{"surface":"手紙","pos":"名詞"},{"surface":"は","pos":"助詞"}]},
    {"id":3, "link":-1,"tokens":[{"surface":"美しかっ","pos":"形容詞"},{"surface":"た","pos":"助動詞"},{"surface":"。","pos":"記号"}]}
  ]
}
↓ parseJson()
CabochaResult(chunks=[...])
```

Kuromojiとの差分：「彼女が書いた手紙は」が正しく3文節に分割され、
「彼女が → 書いた → 手紙は → 美しかった」という正確な係り受けになる。

---

### フェーズ 3：データモデル（`CabochaResult`, `CabochaChunk`, `CabochaToken`）

解析結果の共通データ構造。どちらのパーサーも同じ形式で返す。

```
CabochaResult
  └─ chunks: List<CabochaChunk>
       └─ CabochaChunk
            id: Int        // 文節のID（0始まり）
            link: Int      // 係り先のID（-1=ROOT）
            tokens: List<CabochaToken>
              └─ CabochaToken
                   surface: String  // 表層形（"今日"）
                   pos: String      // 品詞（"名詞"）
```

`CabochaChunk.text` は `tokens.joinToString("") { it.surface }` の計算プロパティ。
例: `tokens=[Token("良い"), Token("天気"), Token("でした")]` → `text="良い天気でした"`

---

### フェーズ 4：1文の構造解析

**担当：`MorphemeAnalyzer`**

`CabochaResult`（またはKuromojiトークン列）から**主語・述語・目的語・時間マーカー・エンティティ**を抽出する。

```
入力: sentenceId=0, text="私はリンゴを食べた。", nativeParser=null (Kuromojiを使用)

形態素解析:
  [私][は][リンゴ][を][食べ][た][。]

主語抽出: "は"/"が"の直前の名詞連続
  → "私"

目的語抽出: "を"の直前の名詞連続
  → "リンゴ"

述語抽出: 文末から最初の自立動詞
  → "食べる" (baseForm)

出力: AnalyzedSentence(
  sentenceId=0,
  originalText="私はリンゴを食べた。",
  structure=SentenceStructure(subject="私", verb="食べる", obj="リンゴ"),
  timeMarkers=[],
  entities=[EntityInfo(PERSON, "私"), EntityInfo(ACTION, "食べる")]
)
```

---

### フェーズ 5：文間の論理関係検出

**担当：`RelationDetector`**

隣接する文の先頭に接続詞があるかをチェックし、関係種別と信頼度を付与する。

```
入力: sentences=[文0, 文1, 文2], rawSentences=["今日は疲れた。", "なぜなら徹夜したからだ。", "それでも仕事は終わった。"]

文0→文1 の関係を検出:
  nextText = "なぜなら徹夜したからだ。"
  "なぜなら" → CAUSAL_CONNECTORS にある → 先頭一致
  → RelationType.CAUSAL, connector="なぜなら", confidence=95

文1→文2 の関係を検出:
  nextText = "それでも仕事は終わった。"
  "それでも" → CONTRAST_CONNECTORS にある → 先頭一致
  → RelationType.CONTRAST, connector="それでも", confidence=82

出力: [
  LogicalRelation(fromSentence=0, toSentence=1, relationType=CAUSAL, connector="なぜなら", confidence=95),
  LogicalRelation(fromSentence=1, toSentence=2, relationType=CONTRAST, connector="それでも", confidence=82)
]
```

接続詞が見つからない場合はデフォルトで `CONTINUATION / "implicit" / confidence=40` になる。

---

### フェーズ 6：論理フロー統合

**担当：`LogicalFlowAnalyzerImpl`**

`SentenceTokenizer` → `MorphemeAnalyzer` → `RelationDetector` を統合するオーケストレーター。

```kotlin
// Hiltがinjectする
class LogicalFlowAnalyzerImpl @Inject constructor(
    private val sentenceTokenizer: SentenceTokenizer,
    private val morphemeAnalyzer: MorphemeAnalyzer,
    private val relationDetector: RelationDetector
)
```

```
入力: "今日は疲れた。なぜなら徹夜したからだ。それでも仕事は終わった。"

Step 1: SentenceTokenizer.split()
  → ["今日は疲れた。", "なぜなら徹夜したからだ。", "それでも仕事は終わった。"]

Step 2: MorphemeAnalyzer.analyze() × 3文
  → [AnalyzedSentence(subject="", verb="疲れる", ...), ...]

Step 3: RelationDetector.detect()
  → [LogicalRelation(CAUSAL, "なぜなら", 95), LogicalRelation(CONTRAST, "それでも", 82)]

Step 4: buildOverallFlow() (内部)
  → ["(主語不明) が (述語不明)", "なぜなら (主語不明) が (述語不明)", ...]

出力: LogicalFlowAnalysis(sentences=[...], relations=[...], overallFlow=[...])
```

---

### フェーズ 7：ThoughtStructure（思想ツリー）への変換

**担当：`CabochaThoughtMapper`**

係り受けグラフ（`CabochaResult`）を、UIで表示可能な**ツリー構造**（`ThoughtStructure`）に変換する。

```
入力: CabochaResult(
  chunks=[
    CabochaChunk(id=0, link=2, text="今日は"),
    CabochaChunk(id=1, link=2, text="良い天気"),
    CabochaChunk(id=2, link=-1, text="でした。")  ← ROOT
  ]
)

childrenIds = {2: [0, 1]}  // id=2 の子は id=0 と id=1

buildNode(2) を再帰呼び出し:
  ThoughtNode(id="2", text="でした。", children=[
    ThoughtNode(id="0", text="今日は", children=[]),
    ThoughtNode(id="1", text="良い天気", children=[])
  ])

出力: ThoughtStructure(roots=[ThoughtNode("でした。", children=["今日は", "良い天気"])])
```

---

## 補助機能

### `LogicalFlowQuestionGenerator`

`LogicalFlowAnalysis` を受け取り、ユーザーへの確認質問を自動生成する。
生成条件：
- 2文以上ある場合 → 全体フロー確認質問 (`FLOW_ORDER`)
- confidence < 60 の文間 → 関係種別を聞く質問 (`IMPLICIT_LINK`)
- 因果関係と判定された文間 → 因果の強度確認 (`CAUSAL_LINK`)
- 主語が変化した文間 → 視点切り替えの意図確認 (`SUBJECT_CHANGE`)

### `LogicalFlowReportBuilder`

ユーザーの質問への回答（`UserResponse`）と解析結果を突き合わせ、**乖離スコア**を算出する。
- `buildPhase1Report()` → 解析結果テキスト（Q&A前に表示）
- `buildReport()` → 文ごとのalignmentScore（0〜100）と `Misalignment` リスト
- `buildFinalReport()` → 総合スコア付きの最終テキストレポート

### `TopicChangeDetectorImpl`

2つの `LogicalFlowAnalysis` を比較し、話題が変わったかを判定する。
- `evaluateStructuralChange()` → 主語集合のJaccard類似度で構造的変化度（0.0〜1.0）を返す
- `buildTopicChangePrompt()` → LLMに判定させるためのプロンプト文字列を生成する

### `ParserComparisonLogger`

開発・デバッグ用。Kuromoji実装とNative実装を同一テキストで比較し、
チャンク数の差・リンク一致率・実行時間をLogcatに出力する。
`runBenchmark()` で5文の定型テストを一括実行できる。

---

## 設計方針まとめ

| 方針 | 具体的な表れ |
|---|---|
| **フォールバック戦略** | Nativeパーサーが使えなくてもKuromojiで動き続ける |
| **同一インターフェース** | `DependencyParser` により呼び出し元はどちらのパーサーか意識しない |
| **単一責任** | 文分割・形態素解析・関係検出を別クラスに分離 |
| **データ指向** | 解析結果は不変のデータクラス（`CabochaResult`等）として流れる |
| **遅延初期化** | `Tokenizer()` は `by lazy` で初回使用時のみ生成（Android起動速度への配慮） |