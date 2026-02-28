# data/di アーキテクチャ解説

## 対象パス
`app/src/main/java/com/example/recemotion/data/di/`

---

## 1. 実行順序

アプリ起動時、Hiltが以下の順序でDIグラフを構築する。

```
アプリ起動 (Application.onCreate)
  └─ Hilt がすべての @Module を収集
       ├─ DatabaseModule       → AppDatabase (Singleton) → 各 DAO
       ├─ ParserModule         → CabochaDependencyParser → DependencyParser
       ├─ RepositoryModule     → ThoughtRepositoryImpl   → ThoughtRepository
       └─ ServiceModule        → LLMInferenceServiceImpl → LLMInferenceService
                                  LogicalFlowAnalyzerImpl  → LogicalFlowService
                                  TopicChangeDetectorImpl  → TopicChangeService
```

各モジュールは `SingletonComponent` にインストールされるため、
すべての提供オブジェクトはアプリのライフタイム全体で同一インスタンスを共有する。

---

## 2. クラスの実行で目的をどう達成しているのか

### DatabaseModule
- `provideAppDatabase` で `AppDatabase.getInstance(context)` を呼び出し、Roomデータベースのシングルトンを生成する
- 5つのDAO (`ThoughtEntryDao`, `ThoughtAnalysisDao`, `ConversationTopicDao`, `ToDoDao`, `EmotionTimelineDao`) をそれぞれ `@Provides` メソッドで公開する
- DAOはスコープなし（リクエストごとに取得）だが、データベースは `@Singleton` で共有されるため実質的に同じインスタンスが渡される

### ParserModule
- `@Binds` で `CabochaDependencyParser`（実装）を `DependencyParser`（インターフェース）にバインドする
- 依存要求側はインターフェース型で注入を受けるため、実装詳細から切り離される

### RepositoryModule
- `@Binds` で `ThoughtRepositoryImpl` を `ThoughtRepository` にバインドする
- データ層の実装をドメイン層のインターフェースに接続する唯一の定義点

### ServiceModule
- LLM推論・論理フロー解析・トピック変化検出の3サービスをそれぞれバインドする
- 実装クラスを変更しても、この1ファイルを修正するだけで全注入箇所に反映される

---

## 3. 目的適合の理由

| 目的 | 適合理由 |
|------|----------|
| データベースの一元管理 | `AppDatabase` を `@Singleton` で提供し、複数箇所から同じDBインスタンスを共有 |
| テスト容易性 | インターフェースバインディングにより、テスト時はモック実装に差し替え可能 |
| ドメイン層の純粋性 | ドメイン層がデータ層の実装クラスを直接参照しない構造を維持 |
| モジュール分離 | 関心事ごと(DB/Parser/Repository/Service)にモジュールを分割し、変更影響範囲を限定 |
| 依存逆転の原則 | 上位層(Domain)が下位層(Data)の実装ではなく抽象(Interface)に依存する |

---

## 4. クラスの依存関係

```
data/di/
├── DatabaseModule
│     depends on: AppDatabase, 各DAO
│     provides to: ThoughtRepositoryImpl, MainScreenFragment, etc.
│
├── ParserModule
│     binds: CabochaDependencyParser → DependencyParser
│     provides to: LogicalFlowAnalyzerImpl (domain service impl)
│
├── RepositoryModule
│     binds: ThoughtRepositoryImpl → ThoughtRepository
│     provides to: AnalyzeThoughtUseCase, ManageConversationUseCase
│
└── ServiceModule
      binds:
        LLMInferenceServiceImpl    → LLMInferenceService
        LogicalFlowAnalyzerImpl    → LogicalFlowService
        TopicChangeDetectorImpl    → TopicChangeService
      provides to: AnalyzeThoughtUseCase, ManageConversationUseCase
```

**ドメイン層との境界**:
```
domain/
  ├── repository/ThoughtRepository         ← RepositoryModule が接続
  ├── service/LLMInferenceService          ← ServiceModule が接続
  ├── service/LogicalFlowService           ← ServiceModule が接続
  ├── service/TopicChangeService           ← ServiceModule が接続
  └── (DependencyParser は data 層内で完結)
```

---

## 5. 使用例・ユースケース

### ユースケース1: UseCase への注入
```kotlin
// AnalyzeThoughtUseCase はインターフェース型で宣言
@HiltViewModel
class SomeViewModel @Inject constructor(
    private val analyzeThought: AnalyzeThoughtUseCase // ← UseCase が Repository/Service を注入済み
) : ViewModel()
```
DIグラフが `ThoughtRepository → ThoughtRepositoryImpl`、
`LLMInferenceService → LLMInferenceServiceImpl` を自動解決する。

### ユースケース2: テスト時の差し替え
```kotlin
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ServiceModule::class])
abstract class FakeServiceModule {
    @Binds
    abstract fun bindLLMService(impl: FakeLLMServiceImpl): LLMInferenceService
}
```
`ServiceModule` を `FakeServiceModule` で置き換えるだけで、
LLM呼び出しをモックに差し替えたテストが実行できる。

### ユースケース3: DAOの直接注入
```kotlin
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val thoughtEntryDao: ThoughtEntryDao // DatabaseModule が提供
) : ViewModel()
```
