# RecEmotion KSP設定システム - コード流れ

## 概要
SharedPreferencesの直接使用を排除し、KSP（Kotlin Symbol Processing）を用いたコード生成で型安全でリアクティブな設定システムを実装

## システムアーキテクチャ

### 1. 設定定義層 (app module)
- **SetupSettings.kt**: `@SettingsGroup("recemotion_setup")` アノテーション付きインターフェース
  - `@StringSetting` / `@LongSetting` / `@BoolSetting` でプロパティを定義
  - コンパイル時の型チェック + デフォルト値の安全性確保
  - 実装なし（スキーマのみ） → DataStoreの非同期性に対応

### 2. KSP処理層 (settings-processor module)
- **SettingsCodeGenerator.kt**: KSP プロセッサー実装
  - `@SettingsGroup` インターフェースを検出
  - 2ファイル自動生成

#### 生成ファイル①: {Name}Store.kt
```
SetupSettingsStore
├── lastDateFlow: Flow<String>      (DataStore backed)
├── wakeTimeUnixFlow: Flow<Long>
├── autoCalbrateFlow: Flow<Boolean>
├── suspend fun setLastDate(value: String)
├── suspend fun setWakeTimeUnix(value: Long)
└── suspend fun setAutoCalibrate(value: Boolean)
```

#### 生成ファイル②: {Name}Module.kt
```
@Module
@InstallIn(SingletonComponent::class)
object SetupSettingsModule {
  @Provides
  @Singleton
  fun provideSetupSettingsStore(...): SetupSettingsStore
}
```

### 3. 使用層（各コンポーネント）

#### SetupViewModel.kt
```kotlin
@HiltViewModel
class SetupViewModel @Inject constructor(
  private val setupSettings: SetupSettingsStore
) {
  fun saveSetup(date: String, wakeTime: Long, autoCalibrate: Boolean) {
    viewModelScope.launch {
      setupSettings.apply {
        setLastDate(date)
        setWakeTimeUnix(wakeTime)
        setAutoCalibrate(autoCalibrate)
      }
    }
  }

  suspend fun getSavedAutoCalibrate(): Boolean =
    setupSettings.autoCalbrateFlow.first()
}
```

#### SetupFragment.kt
```kotlin
class SetupFragment : Fragment() {
  private val viewModel: SetupViewModel by viewModels()

  fun completeSetup() {
    viewModel.saveSetup(date, wakeTime, isAutoCalibrate)
  }

  fun checkAutoCalibrate() {
    lifecycleScope.launch {
      val saved = viewModel.getSavedAutoCalibrate()
      // UI更新
    }
  }
}
```

#### MainActivity.kt
```kotlin
class MainActivity : AppCompatActivity() {
  @Inject lateinit var setupSettings: SetupSettingsStore

  private fun isFirstLaunchToday(): Boolean = runBlocking {
    setupSettings.lastDateFlow.first() != today
  }
}
```

#### MainScreenFragment.kt
```kotlin
class MainScreenFragment : Fragment() {
  @Inject lateinit var setupSettings: SetupSettingsStore

  private fun checkSetupState() {
    lifecycleScope.launch {
      val lastDate = setupSettings.lastDateFlow.first()
      val wakeTime = setupSettings.wakeTimeUnixFlow.first()
      // 初期化判定
    }
  }
}
```

## 依存関係フロー

```
設定インターフェース定義 (@SettingsGroup)
         ↓ (KSP処理時)
[コンパイル時コード生成]
         ↓
{Name}Store + {Name}Module 生成
         ↓ (ビルド時)
  Hilt DI登録
         ↓ (実行時)
各Componentへ自動Inject
         ↓
DataStore経由で永続化
```

## 重要な設計判断

| 項目 | 決定 | 理由 |
|------|------|------|
| インターフェース実装 | スキーマのみ（実装なし） | DataStoreは非同期 → プロパティゲッターでrunBlocking不可 |
| アノテーション方式 | 型別（String/Long/Bool） | ジェネリック1つより型安全＆コンパイル時デフォルト値チェック |
| Hilt統合 | Moduleクラス自動生成 | DataStore singleton保証 + named qualifier対応 |
| Flow使用 | すべてのプロパティFlow化 | リアクティブ＆observe可能 |
| 移行パターン | ViewModel経由の集約 | Fragment直接アクセス最小化 |

## 現在の実装状態
- ✅ settings-processor モジュール実装完了
- ✅ KSP コード生成ロジック完成
- ✅ SetupViewModel統合
- ✅ SetupFragment, MainActivity, MainScreenFragment 移行完了
- ✅ SharedPreferences完全排除（app層）
