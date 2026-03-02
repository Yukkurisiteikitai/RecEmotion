# Data Repository ドキュメント

`app/src/main/java/com/example/recemotion/data/repository/`

---

## 概要

このパッケージは、**永続化操作の窓口**を担う。
UseCase 層はインターフェース（`domain/repository/ThoughtRepository`）にのみ依存し、
Room DAO の具体実装を知らない。その橋渡しをするのが本パッケージの `ThoughtRepositoryImpl` である。

| ファイル | 役割 |
|---|---|
| `ThoughtRepository.kt` | （旧実装・暫定クラス）インターフェースなしで DAO を直接利用していた時代の残骸 |
| `ThoughtRepositoryImpl.kt` | `domain/repository/ThoughtRepository` インターフェースの **唯一の具体実装** |

> **注意：** `data/repository/ThoughtRepository.kt` は `domain/repository/ThoughtRepository.kt`（interface）とは別物。
> 前者は Clean Architecture 導入前の旧実装クラス。現在の主体は `ThoughtRepositoryImpl` である。

---

## 1. 実行順序

```
UseCase（例: AnalyzeThoughtUseCase）
    │  domain/repository/ThoughtRepository を呼ぶ
    ▼
ThoughtRepositoryImpl（DI で注入）
    │  各操作を対応する DAO へ委譲
    ├─ entryDao   → ThoughtEntryDao
    ├─ analysisDao → ThoughtAnalysisDao
    ├─ topicDao   → ConversationTopicDao
    └─ todoDao    → ToDoDao
         │
         ▼
       Room Database（SQLite）
```

---

## 2. クラスの実行で目的をどう達成しているか

### ThoughtRepositoryImpl

4 つの DAO を受け取り、UseCase が必要とする操作を **ドメイン語彙** で提供する。

#### Entry 操作

| メソッド | 内部操作 |
|---|---|
| `storeEntry(topicId, rawText, treeJson, timestamp)` | `ThoughtEntryEntity` を組み立てて `entryDao.insertEntry()` |
| `updateEntry(id, treeJson)` | 既存エンティティを取得し `copy(treeJson=...)` で更新 |
| `getEntryById(id)` | `entryDao.getEntryById()` をそのまま移譲 |
| `getLatestEntryForTopic(topicId)` | `entryDao.getEntriesByTopic()` の Flow を `first()` で一度だけ取得し先頭要素を返す |
| `getEntriesByTopic(topicId)` | Flow をそのまま返す（観察用） |
| `getAllEntries()` | 同上 |

**具体例：**
```kotlin
// UseCase 内部
val entryId = repository.storeEntry(
    topicId   = 1L,
    rawText   = "今日は疲れた",
    treeJson  = """{"roots":[...]}""",
    timestamp = System.currentTimeMillis()
)
// → entryId = 42L (Room が自動採番した行ID)
```

#### Analysis 操作

| メソッド | 内部操作 |
|---|---|
| `storeAnalysis(entryId, analysisJson, timestamp)` | `ThoughtAnalysisEntity` を組み立てて `analysisDao.insert()` |
| `getAnalysisForEntry(entryId)` | `analysisDao.getAnalysisForEntry()` を移譲 |

**具体例：**
```kotlin
val analysisId = repository.storeAnalysis(
    entryId      = 42L,
    analysisJson = """{"premises":["疲れた"],"emotions":["不安"],...}""",
    timestamp    = System.currentTimeMillis()
)
```

#### Topic 操作

| メソッド | 内部操作 |
|---|---|
| `getActiveTopic()` | `topicDao.getActiveTopic()` を移譲 |
| `insertTopic(title, timestamp)` | `createdAt` と `updatedAt` を同値で `ConversationTopicEntity` を生成して挿入 |
| `getTopicById(id)` | `topicDao.getTopicById()` を移譲 |
| `updateTopicTimestamp(id, timestamp)` | 既存エンティティを取得し `copy(updatedAt=...)` で更新 |
| `resolveTopic(id, result, timestamp)` | `topicDao.resolveTopic()` を移譲（専用クエリに任せる） |
| `getAllTopics()` | Flow をそのまま返す |

**具体例（会話トピック作成から解決まで）：**
```kotlin
val topicId = repository.insertTopic("仕事のストレスについて", now)
// → topicId = 3L

repository.updateTopicTimestamp(topicId, now + 60_000)
// → updatedAt のみ更新

repository.resolveTopic(topicId, "感情整理完了", now + 300_000)
// → resolvedResult と resolvedAt がセットされ、isActive = false になる
```

#### ToDo 操作

| メソッド | 内部操作 |
|---|---|
| `getAllToDos()` | `todoDao.getAllToDos()` を移譲（Flow） |
| `insertToDo(topicId, description, timestamp)` | `ToDoEntity` を生成して挿入 |
| `updateToDoStatus(id, isCompleted)` | `todoDao.updateToDoStatus()` を移譲 |

**具体例：**
```kotlin
val todoId = repository.insertToDo(topicId = 3L, "実際にフィードバックを確認する", now)
repository.updateToDoStatus(todoId, isCompleted = true)
```

---

## 3. 目的適合の理由

### なぜ Repository パターンか

UseCase が直接 DAO を呼ぶと以下の問題が生じる：

- DAO は Room の `@Dao` アノテーション依存なので、テスト時に Android フレームワークが必要
- DAO の型（`ThoughtEntryEntity`）がドメイン層に漏れ、DB スキーマ変更が UseCase を壊す

`ThoughtRepository` インターフェースを挟むことで：

```
UseCase  ─── interface ThoughtRepository ─── ThoughtRepositoryImpl ─── DAO
          テストではモックを注入可能       本番実装は DI で差し替え
```

### なぜ DAO を薄くラップするだけか

本アプリでは Domain Entity と DB Entity が分離されていないため、
Repository が行う変換は最小限（Entity の組み立て・`copy()` による部分更新）に留める。
変換ロジックが増えた場合は Mapper クラスへの切り出しが推奨される。

---

## 4. クラスの依存関係

```
domain/repository/ThoughtRepository (interface)
    ▲ implements
    │
ThoughtRepositoryImpl
    ├── ThoughtEntryDao      (Room @Dao)
    ├── ThoughtAnalysisDao   (Room @Dao)
    ├── ConversationTopicDao (Room @Dao)
    └── ToDoDao              (Room @Dao)
         │
         ▼
    RecEmotionDatabase (Room @Database)
         │
         ▼
    SQLite ファイル (data/data/com.example.recemotion/databases/)
```

`@Inject constructor` により Hilt が DAO を自動注入する。
UseCase は `ThoughtRepository` インターフェース型で受け取るため、
`ThoughtRepositoryImpl` の存在を知らない。

### 旧クラス `data/repository/ThoughtRepository.kt` との関係

```
ThoughtRepository.kt (旧・具体クラス)
    ├── ThoughtEntryDao
    ├── ThoughtAnalysisDao
    └── ConversationTopicDao
    ※ インターフェースなし・ToDoDao なし・インターフェース非実装
    → 現在は ThoughtRepositoryImpl に機能を移行済み
```

---

## 5. 使用例・ユースケース

### UseCase からの典型的な呼び出しフロー

```kotlin
// AnalyzeThoughtUseCase 内（疑似コード）
class AnalyzeThoughtUseCase @Inject constructor(
    private val repository: ThoughtRepository  // ← インターフェースのみ知っている
) {
    suspend operator fun invoke(text: String): Flow<AnalysisUpdate> = flow {
        // 1. アクティブなトピックを取得（なければ作成）
        val topic = repository.getActiveTopic()
            ?: repository.insertTopic("新しいトピック", System.currentTimeMillis()).let {
                repository.getTopicById(it)!!
            }

        // 2. Entry を保存（まず空の treeJson で）
        val entryId = repository.storeEntry(
            topicId   = topic.id,
            rawText   = text,
            treeJson  = "{}",
            timestamp = System.currentTimeMillis()
        )

        // 3. LLM 解析を実行（省略）...

        // 4. 解析結果を保存
        repository.storeAnalysis(
            entryId      = entryId,
            analysisJson = analysisResult.toJson(),
            timestamp    = System.currentTimeMillis()
        )

        // 5. Entry の treeJson を更新
        repository.updateEntry(entryId, treeJson = treeJson)

        emit(AnalysisUpdate.Complete(structure, fullText, analysisResult))
    }
}
```

### Flow による観察パターン

```kotlin
// ViewModel での使用例
viewModelScope.launch {
    repository.getAllTopics().collect { topics ->
        _uiState.update { it.copy(topics = topics) }
    }
}
```

Flow を返すメソッド（`getAllEntries`, `getEntriesByTopic`, `getAllTopics`, `getAllToDos`）は
Room の LiveData 相当であり、DB が更新されると自動で最新値が再配信される。

---

## データの流れ（全体像）

```
UseCase（ドメイン層）
    │  suspend / Flow を呼ぶ
    ▼
ThoughtRepository (interface)
    │
ThoughtRepositoryImpl（data 層）
    │  Entity を組み立てて DAO に渡す
    ▼
Room DAO
    │  SQL クエリを生成・実行
    ▼
SQLite DB
    │  更新があれば Flow で再配信
    ▲
    └─ ViewModel → UI（Compose / View）
```
