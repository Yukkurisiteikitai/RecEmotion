# UI モジュール アーキテクチャ

対象パス: `app/src/main/java/com/example/recemotion/ui/`

---

## 1. 実行順序

```
[SettingsFragment が ComposeView をホスト]
        ↓
[SettingsViewModel → SettingsUiState を Flow で発行]
        ↓
[SettingsScreen(uiState, onAutoCalibrateChanged) 呼び出し]
        ├─ SettingToggleRow  (Auto Calibrate スイッチ)
        └─ SettingDisplayRow (Wake Time / Last Setup Date 表示)

[MainScreenFragment / EditText 初期化時]
        ↓
[EmotionCursorDrawable() インスタンス生成]
        ↓
[editText.setTextCursorDrawable(drawable)]
        ↓
[LLM 感情推定結果受信 → drawable.updateEmotion(emotion, stressLevel)]
        ↓
[カーソル再描画 (invalidateSelf)]
```

---

## 2. クラスの目的と達成方法

### `SettingsScreen.kt`
**目的**: アプリ設定をユーザーが閲覧・変更するための Compose UI 画面を提供する。

- `SettingsScreen` は `SettingsUiState`（データ）と `onAutoCalibrateChanged`（イベント）を受け取るステートレス Composable。
- `SettingToggleRow` で Boolean 設定（Auto Calibrate）をトグルスイッチとして表示し、変更をコールバックで上位へ伝搬する。
- `SettingDisplayRow` で読み取り専用の値（Wake Time、Last Setup Date）をラベル＋値ペアで表示する。
- `formatWakeTime` が Unix タイムスタンプを `HH:mm` 文字列へ変換し、未設定時は `—` を返す。

### `EmotionCursorDrawable.kt`
**目的**: EditText のカーソルを感情状態に応じた色と透明度でリアルタイム描画し、ユーザーの現在感情を視覚的にフィードバックする。

- `Drawable` を継承し、`draw()` でカーソル幅中央に縦線を描画する。
- `updateEmotion(emotion, stressLevel)` を外部から呼ぶことで色・alpha を更新し `invalidateSelf()` で再描画をトリガーする。
- ストレスレベル 1→alpha 128、5→alpha 255 の線形マッピングで、感情の強度をカーソルの不透明度として表現する。
- `emotionToColor` はコンパニオンオブジェクトに定義され、他コンポーネントからも感情色変換として再利用可能。

### `SettingData.java`
**目的**: 現時点では空のスタブクラス。将来的な設定データ保持用のプレースホルダーとして存在する。

---

## 3. 目的適合の理由

| クラス | 設計判断 | 理由 |
|--------|----------|------|
| `SettingsScreen` | ステートレス Composable + コールバック設計 | ViewModel の状態を UI に一方向で流し込む Unidirectional Data Flow を維持するため |
| `SettingsScreen` | `formatWakeTime` をプライベート関数として分離 | 表示変換ロジックを Composable 本体と分離し、単体テストを容易にするため |
| `EmotionCursorDrawable` | `Drawable` 継承（View 継承でなく） | `setTextCursorDrawable()` API の要件に合わせ、EditText 内部描画システムと統合するため |
| `EmotionCursorDrawable` | `emotionToColor` をコンパニオンに配置 | カーソル以外の UI 要素（グラフ、背景等）でも感情色を統一参照できる共通マッピングとして機能させるため |

---

## 4. クラスの依存関係

```
SettingsScreen.kt
    ├─ 依存: SettingsUiState (presentation層)
    ├─ 依存: androidx.compose.material3.*
    └─ 依存: java.text.SimpleDateFormat (wakeTime フォーマット)

EmotionCursorDrawable.kt
    ├─ 依存: android.graphics.* (Canvas, Paint, Color, Drawable)
    └─ 参照元: MainScreenFragment (EditText カーソル設定)
               感情推定結果受信コールバック (updateEmotion 呼び出し元)

SettingData.java
    └─ 依存なし（空スタブ）
```

---

## 5. ユースケースと使用例

### SettingsScreen — 設定画面表示

```kotlin
// SettingsFragment.kt 内
composeView.setContent {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onAutoCalibrateChanged = { viewModel.setAutoCalibrate(it) }
    )
}
```

### EmotionCursorDrawable — 感情連動カーソル

```kotlin
// EditText 初期化時
val cursorDrawable = EmotionCursorDrawable()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    editText.setTextCursorDrawable(cursorDrawable)
}

// LLM 感情推定結果を受け取るたびに更新
cursorDrawable.updateEmotion("HAPPY", stressLevel = 3)
// → カーソルが緑色 (alpha ≈ 191) で描画される

// 感情色を単独で取得する場合
val color = EmotionCursorDrawable.emotionToColor("SAD") // → #2196F3
```

### 感情→色マッピング早見表

| 感情 | カラーコード | 色 |
|------|-------------|-----|
| HAPPY | `#4CAF50` | 緑 |
| SAD | `#2196F3` | 青 |
| ANGRY | `#F44336` | 赤 |
| FEARFUL | `#FF9800` | 橙 |
| DISGUSTED | `#9C27B0` | 紫 |
| SURPRISED | `#FFEB3B` | 黄 |
| NEUTRAL / その他 | `#FFFFFF` | 白 |
