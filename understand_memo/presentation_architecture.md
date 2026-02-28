# Presentation Layer Architecture

対象パス: `app/src/main/java/com/example/recemotion/presentation/`

---

## 1. 実行順序

```
アプリ起動
│
├─ MainActivity
│   └─ SetupFragment / SettingsFragment / ConversationFragment / ChatFragment
│       │
│       ├─ [Setup フロー]
│       │   SetupViewModel.init()
│       │     └─ wakeTimeUnix を 07:00 で初期化
│       │   UI イベント → onCalibrationStarted()
│       │             → onCalibrationSuccess() / onCalibrationError()
│       │             → saveSetup() → SetupSettingsStore に永続化
│       │
│       ├─ [Settings フロー]
│       │   SettingsViewModel.uiState (StateFlow)
│       │     └─ SetupSettingsStore の 3 Flow を combine して emit
│       │
│       ├─ [Conversation フロー]
│       │   ThoughtAnalysisViewModel.init()
│       │     ├─ loadHistory()   → DB から全トピック/エントリ/ToDo を購読
│       │     └─ runDiagnostic() → SystemDiagnosticUseCase を実行してログ追加
│       │
│       │   analyze(text) 呼び出し
│       │     ├─ buildEmotionContext()         → EmotionTimelineDao から直近 5 件取得
│       │     ├─ ManageConversationUseCase.processInput() → トピック分類
│       │     └─ startDetailedAnalysis()       → AnalyzeThoughtUseCase → LLM ストリーミング
│       │
│       ├─ [ConversationAdapter バインド]
│       │   historyItems (StateFlow<List<ConversationDisplayItem>>) を observe
│       │     → ConversationAdapter.submitList()
│       │       ├─ TopicViewHolder.bind()
│       │       ├─ ThoughtViewHolder.bind()
│       │       ├─ ToDoViewHolder.bind()
│       │       └─ SystemViewHolder.bind()
│       │
│       └─ [ChatAdapter バインド]
│           chatItems (StateFlow) を observe
│             → ChatAdapter.submitList()
│               ├─ UserMessageViewHolder.bind()
│               ├─ AssistantOutputViewHolder.bind()
│               ├─ TopicDividerViewHolder.bind()
│               └─ SystemNoticeViewHolder.bind()
```

---

## 2. クラスの実行で目的をどう達成しているのか

### ConversationDisplayItem (Sealed Class)

UI に描画する全種別を 1 つの型で表現するディスプレイモデル。

| サブクラス | 役割 |
|---|---|
| `TopicHeader` | トピック名 + 解決済みフラグ |
| `ThoughtAnalysis` | ユーザー入力テキスト + LLM 解析結果 |
| `ToDoItem` | 仮説検証タスク + 完了チェック |
| `SystemMessage` | エラー/情報ログ表示 |

DB エンティティや Domain モデルをそのまま RecyclerView に渡さず、このシールドクラスに変換することで、UI とデータ層を疎結合に保つ。

---

### ConversationAdapter

`ListAdapter<ConversationDisplayItem>` として 4 種類の ViewHolder を管理。

- `getItemViewType()` でサブクラスを ViewType に変換
- `onCreateViewHolder()` で各 XML レイアウトをインフレート
- `DiffUtil.ItemCallback` で差分更新、不要な再描画を防止

**ThoughtViewHolder** では `result` が `null` の場合に "Analyzing..." を表示し、解析完了後に `submitList()` が再呼び出されることで自動更新される。

**TopicViewHolder** では `isResolved` に応じて「解決ボタン」と「解決済みラベル」を切り替え、ボタンタップで `onResolveTopic(id)` コールバックを ViewModel に委譲する。

---

### ChatAdapter

Chat 画面専用の `ListAdapter<ChatDisplayItem>`。ConversationAdapter と構造は同じだが、`ChatDisplayItem` は `ConversationDisplayItem` とは別の sealed class であり、Chat UI 特有の `stressLevel` による感情バー色計算を持つ。

**AssistantOutputViewHolder** では `Markwon` ライブラリで Markdown をレンダリングし、クリップボードコピーボタンを提供する。

---

### SetupViewModel

セットアップ画面の状態機械。`SetupUiState` に全 UI 状態を集約し、ユーザー操作ごとに `_uiState.update {}` で不変コピーを生成する。

```
CalibrationButtonState 遷移:
UNSET → NOW_SETTING → PASS_SETTINGS
                    → ERROR_SETTING → NOW_SETTING (再試行)
```

`saveSetup()` 呼び出し時のみ `SetupSettingsStore` (DataStore) に永続化し、それ以外は UI 状態はメモリ上のみに存在する。

---

### SettingsViewModel

DataStore の 3 つの Flow (`autoCalibrateFlow`, `wakeTimeUnixFlow`, `lastDateFlow`) を `combine` で合成し、`SettingsUiState` として emit する読み取り専用ビューモデル。

書き込みは `setAutoCalibrate()` のみを提供し、最小限の更新 API となっている。

---

### ThoughtAnalysisViewModel (中核ViewModel)

アプリの主要機能を統括する ViewModel。

**loadHistory()**: DB の全テーブルを `combine` で購読し、トピック → ToDo → エントリの順序でフラットな `ConversationDisplayItem` リストを構築して `_historyItems` に流す。DB 変更が起きるたびに自動再構築される。

**analyze(text)**: テキスト入力から LLM 解析までの全パイプラインを実行する。

```
1. buildEmotionContext()     感情コンテキスト文字列を生成
2. ManageConversationUseCase トピック分類・エントリ保存
3. AnalyzeThoughtUseCase     LLM ストリーミング解析
4. _uiState 更新             Progress / Complete / Error を反映
```

**generateToDo()**: `ThoughtAnalysis` の仮説リストを LLM に投げ、具体的な To-Do タスク文字列を生成して DB に挿入する。

---

## 3. 目的適合の理由

| 設計判断 | 理由 |
|---|---|
| `ConversationDisplayItem` を sealed class に集約 | DB エンティティの変更が Adapter に波及しない。新種別追加時に when 式のコンパイルエラーで漏れを検知できる |
| `ListAdapter` + `DiffUtil` | トピックやエントリが増えても差分のみを再描画し、スクロール位置が保持される |
| `ThoughtAnalysisViewModel` が `EmotionTimelineDao` を直接注入 | 感情コンテキストは補助情報であり UseCase 経由では過剰な抽象化になるため、例外的に直接アクセスを許容 |
| `SetupUiState` に全状態を集約 | キャリブレーション中・完了・エラーのような多段階 UI 状態を if 分散させず、1 つのデータクラスで一貫管理する |
| `SettingsViewModel` を読み取りビューモデルに特化 | 設定画面は DataStore の値を表示するだけで、ロジックは持たない。責務を最小化することで複雑性を排除 |

---

## 4. クラスの依存関係

```
ThoughtAnalysisViewModel
├── AnalyzeThoughtUseCase         (domain.usecase)
├── ManageConversationUseCase     (domain.usecase)
├── SystemDiagnosticUseCase       (domain.usecase)
├── ThoughtRepository             (domain.repository)
├── LLMInferenceService           (domain.service)
├── EmotionTimelineDao            (data.db)
└── ThoughtAnalysisJsonParser     (data.llm)

SetupViewModel
└── SetupSettingsStore            (settings / KSP 生成)

SettingsViewModel
└── SetupSettingsStore            (settings / KSP 生成)

ConversationAdapter
└── ConversationDisplayItem       (presentation)

ChatAdapter
├── ChatDisplayItem               (presentation / ChatAdapter.kt 内定義)
└── EmotionCursorDrawable         (ui)

ThoughtAnalysisUiState
├── ThoughtAnalysisResult         (domain.model)
├── ThoughtStructure              (domain.model)
└── ConversationDisplayItem       (presentation)
```

---

## 5. 使用例・ユースケース

### ユースケース 1: ユーザーが思考テキストを入力する

```kotlin
// Fragment からの呼び出し
viewModel.analyze("今日の会議で自分の意見が通らなかった")

// ThoughtAnalysisViewModel 内部フロー
// 1. ManageConversationUseCase がトピックを分類して entryId を返す
// 2. startDetailedAnalysis() が LLM ストリーミングを開始
// 3. AnalysisUpdate.Progress が届くたびに partialStreamingText が更新される
// 4. Complete で finalResult にパース済み解析結果が格納される
// 5. loadHistory() の combine が反応して historyItems が更新される
// 6. ConversationAdapter が DiffUtil で差分更新を実行
```

### ユースケース 2: トピックを解決済みにする

```kotlin
// ConversationAdapter の TopicViewHolder からコールバック
onResolveTopic(topicId = 42L)

// Fragment → ViewModel
viewModel.resolveTopic(42L, resolution = "上司に直接確認して解決")

// ThoughtRepository.resolveTopic() → DB 更新 → loadHistory() combine 反応
// → ConversationDisplayItem.SystemMessage("RESOLVED: ...") が追加される
```

### ユースケース 3: キャリブレーションセットアップ

```kotlin
// パーミッション取得後
setupViewModel.onCameraPermissionResult(granted = true)

// キャリブレーション開始
setupViewModel.onCalibrationStarted()
// UI: CalibrationButtonState.NOW_SETTING → スピナー表示

// 成功コールバック
setupViewModel.onCalibrationSuccess()
// UI: PASS_SETTINGS + showWakeTimeSection = true → 起床時刻入力フォーム表示

// 保存
setupViewModel.saveSetup(
    date = "2026-02-28",
    wakeTimeUnix = 1740700800L,
    autoCalibrate = true
)
// → SetupSettingsStore (DataStore) に永続化
```

### ユースケース 4: ToDo 自動生成

```kotlin
// ThoughtViewHolder の「ToDo 生成」ボタンタップ
onGenerateToDo(thoughtAnalysisItem)

// ViewModel
viewModel.generateToDo(item)
// LLM に仮説リストを投げてタスク文字列を生成
// → repository.insertToDo() で DB 保存
// → loadHistory() が反応して ConversationDisplayItem.ToDoItem が追加される
```
