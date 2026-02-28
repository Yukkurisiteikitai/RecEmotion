# Settings モジュール アーキテクチャ

対象パス: `app/src/main/java/com/example/recemotion/settings/`
関連パス: `settings-processor/src/main/kotlin/com/example/settings/`

---

## 1. 実行順序

### ビルド時 (KSP コード生成)

```
SetupSettings.kt (インターフェース定義)
    ↓ KSP スキャン
SettingsProcessor.process()
    ↓ @SettingsGroup アノテーションを検出
    ↓ getAllProperties() で各プロパティの @XxxSetting アノテーションを収集
    ↓ SettingInfo リストを構築
SettingsCodeGenerator.generateStore()    → SetupSettingsStore.kt (生成)
SettingsCodeGenerator.generateModule()  → SetupSettingsModule.kt (生成)
```

### ランタイム (依存注入 → 使用)

```
アプリ起動
    ↓ Hilt が SetupSettingsModule を読み込み
    ↓ provideSetupSettingsDataStore() → Context.recemotionSetupDataStore を提供
    ↓ provideSetupSettingsStore(dataStore) → SetupSettingsStore インスタンスを生成
SetupViewModel(@Inject SetupSettingsStore)
    ↓ Flow プロパティを購読して現在値を取得
    ↓ suspend 関数で値を書き込み
```

---

## 2. クラスの実行で目的をどう達成しているのか

### 目的: セットアップ設定 (最終キャリブレーション日時・起床時刻・自動キャリブレーション有無) の永続化

#### `SetupSettings` インターフェース (スキーマ定義)
アノテーションのみで設定スキーマを宣言する。実装コードはゼロ。

```kotlin
@SettingsGroup("recemotion_setup")   // DataStore ファイル名 = "recemotion_setup"
interface SetupSettings {
    @StringSetting("setup_last_date", "")        // DataStore キー + デフォルト値
    val lastDate: String

    @LongSetting("setup_wake_time_unix", 0L)
    val wakeTimeUnix: Long

    @BoolSetting("setup_auto_calibrate", false)
    val autoCalibrate: Boolean
}
```

#### `SettingsProcessor` (KSP シンボルプロセッサ)
`@SettingsGroup` が付いたインターフェースを走査し、各プロパティのアノテーションを `SettingInfo` に変換する。

```
propName="lastDate", type=STRING, key="setup_last_date", defaultValue=""
propName="wakeTimeUnix", type=LONG,   key="setup_wake_time_unix",  defaultValue=0L
propName="autoCalibrate", type=BOOL,  key="setup_auto_calibrate",  defaultValue=false
```

#### `SettingsCodeGenerator` (コード生成)
`SettingInfo` リストから 2 つのファイルを生成する。

**SetupSettingsStore (生成クラス)**:
- `KEY_LAST_DATE` 等の `Preferences.Key<T>` 定数
- `lastDateFlow: Flow<String>` 等の DataStore 購読プロパティ
- `suspend fun setLastDate(value: String)` 等の書き込み関数

```kotlin
// 生成されるコードのイメージ
class SetupSettingsStore @Inject constructor(
    @Named("recemotion_setup") private val dataStore: DataStore<Preferences>
) {
    private val KEY_LAST_DATE = stringPreferencesKey("setup_last_date")
    val lastDateFlow: Flow<String> = dataStore.data.map { it[KEY_LAST_DATE] ?: "" }
    suspend fun setLastDate(value: String) { dataStore.edit { it[KEY_LAST_DATE] = value } }
    // wakeTimeUnix, autoCalibrate も同様...
}
```

**SetupSettingsModule (生成 Hilt モジュール)**:
- `Context.recemotionSetupDataStore` 拡張プロパティ (DataStore の実体)
- `@Provides @Singleton @Named("recemotion_setup")` で DataStore を提供
- `@Provides @Singleton` で SetupSettingsStore を提供

---

## 3. 目的適合の理由

| 設計選択 | 理由 |
|---|---|
| アノテーション駆動コード生成 | スキーマ定義と実装コードを分離。新設定の追加はアノテーション 1 行で完結し、ボイラープレートを排除 |
| DataStore Preferences (SharedPreferences 非使用) | 型安全 + コルーチン/Flow ネイティブサポート。非同期読み書きで UI スレッドをブロックしない |
| Hilt DI | DataStore インスタンスをシングルトンとして管理。複数 ViewModel からの同時アクセスを安全に制御 |
| `@Named` 修飾子 | 複数の DataStore (例: `SetupSettings`, `AppSettings`) を同一コンテナで管理可能にする |

---

## 4. クラスの依存関係

```
SetupSettings.kt (インターフェース)
├── @SettingsGroup → settings-processor: SettingsGroup.kt (アノテーション)
├── @StringSetting  → StringSetting.kt
├── @LongSetting    → LongSetting.kt
└── @BoolSetting    → BoolSetting.kt

        ↓ KSP ビルド時生成

SetupSettingsStore.kt (generated)
└── androidx.datastore.core.DataStore<Preferences>

SetupSettingsModule.kt (generated)
├── dagger.hilt (@Module, @InstallIn, @Provides, @Singleton)
├── @Named("recemotion_setup")
├── @ApplicationContext Context
└── SetupSettingsStore

        ↓ インジェクト先

SetupViewModel.kt
└── SetupSettingsStore (読み書き)
```

---

## 5. 使用例・ユースケース

### セットアップ完了時に設定を一括保存

```kotlin
// SetupViewModel.kt
fun saveSetup(date: String, wakeTimeUnix: Long, autoCalibrate: Boolean) {
    viewModelScope.launch {
        settings.setLastDate(date)           // "2026-02-28"
        settings.setWakeTimeUnix(wakeTimeUnix)  // 1740700800 (Unix秒)
        settings.setAutoCalibrate(autoCalibrate) // true
    }
}
```

### 自動キャリブレーション設定を一度だけ読み取る

```kotlin
// SetupViewModel.kt
suspend fun getSavedAutoCalibrate(): Boolean = settings.autoCalibrateFlow.first()
```

### 起床時刻を Flow として継続購読する

```kotlin
// 任意のコルーチンスコープ
setupSettingsStore.wakeTimeUnixFlow.collect { unixSec ->
    // 値が変わるたびに呼ばれる
    updateAlarm(unixSec)
}
```

### 新しい設定項目を追加する手順

1. `SetupSettings.kt` にアノテーション付きプロパティを追加するだけ
2. KSP が次のビルドで `SetupSettingsStore` に Flow プロパティと setter を自動生成

```kotlin
// 追加例: セッション間隔(分)
@IntSetting("setup_session_interval_min", 30)
val sessionIntervalMin: Int
```
