# data/db アーキテクチャ解説

対象パス: `app/src/main/java/com/example/recemotion/data/db/`

---

## 1. 実行順序

アプリ起動からDBアクセスまでの流れ：

```
Application 起動
  └─ AppDatabase.getInstance(context)   // シングルトン生成
       └─ Room.databaseBuilder(...)
            └─ RoomDatabase ビルド
                 ├─ ThoughtEntryDao
                 ├─ ThoughtAnalysisDao
                 ├─ ConversationTopicDao
                 ├─ ToDoDao
                 └─ EmotionTimelineDao

データ操作時：
  Repository / ViewModel
    └─ AppDatabase.getInstance(context).xxxDao()
         └─ DAO メソッド呼び出し (suspend / Flow)
              └─ SQLite へのクエリ実行
```

---

## 2. クラスの実行で目的をどう達成しているのか

### AppDatabase
Room の `RoomDatabase` を継承したシングルトン。`getInstance()` により、アプリ全体で1つのDB接続を共有する。`fallbackToDestructiveMigration()` によりスキーマ変更時に旧データを破棄して再構築する。

### Entity 群（テーブル定義）
| Entity | テーブル名 | 役割 |
|---|---|---|
| `ConversationTopicEntity` | `conversation_topics` | ユーザーの悩み・思考ブロックのトピック管理 |
| `ThoughtEntryEntity` | `thought_entries` | ユーザーが入力したテキストとその構文木JSON |
| `ThoughtAnalysisEntity` | `thought_analyses` | LLM によるテキスト分析結果JSON |
| `ToDoEntity` | `todo_items` | トピックに紐づくタスク一覧 |
| `EmotionTimelineEntity` | `emotion_timeline` | 感情スナップショットのタイムライン |

### DAO 群（データアクセス）
各 DAO は対応テーブルへの CRUD と Flow/suspend クエリを提供し、上位レイヤー（Repository）がDBの存在を意識せずにデータを扱えるようにする。

---

## 3. 目的適合の理由

- **Room ORM の採用**：SQLite を直接扱わず、型安全なアノテーションベースのクエリで実装ミスを防ぐ。
- **Flow の活用**：`getAllTopics()` や `getAllEntries()` は `Flow` を返し、データ変更をリアクティブにUIへ伝播する。UI側で明示的なポーリングが不要になる。
- **suspend 関数**：書き込み・単発読み取りは `suspend fun` にしてコルーチン上で非同期実行し、メインスレッドをブロックしない。
- **外部キー制約**：`ThoughtEntryEntity` → `ConversationTopicEntity`（SET_NULL）、`ThoughtAnalysisEntity` → `ThoughtEntryEntity`（CASCADE）、`ToDoEntity` → `ConversationTopicEntity`（CASCADE）によりDB側でデータ整合性を保証する。
- **シングルトンパターン**：`AppDatabase` を1インスタンスに限定することでコネクション競合を防ぐ。

---

## 4. クラスの依存関係

```
AppDatabase
  ├─ ConversationTopicEntity ──── ConversationTopicDao
  │
  ├─ ThoughtEntryEntity ──────── ThoughtEntryDao
  │     └─ FK → ConversationTopicEntity (SET_NULL on delete)
  │
  ├─ ThoughtAnalysisEntity ────── ThoughtAnalysisDao
  │     └─ FK → ThoughtEntryEntity (CASCADE on delete)
  │
  ├─ ToDoEntity ──────────────── ToDoDao
  │     └─ FK → ConversationTopicEntity (CASCADE on delete)
  │
  └─ EmotionTimelineEntity ────── EmotionTimelineDao
        (外部キーなし、独立したタイムライン)
```

`EmotionTimelineEntity` は他エンティティへの依存を持たず、感情ログとして独立している。

---

## 5. クラスの使用例・ユースケース

### ユースケース1: 新しいトピックを作成し、思考エントリを記録する

```kotlin
val db = AppDatabase.getInstance(context)

// トピック作成
val topicId = db.conversationTopicDao().insertTopic(
    ConversationTopicEntity(
        title = "仕事のストレス",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
)

// 思考エントリ保存
val entryId = db.thoughtEntryDao().insertEntry(
    ThoughtEntryEntity(
        topicId = topicId,
        rawText = "上司に怒られた",
        treeJson = "{ ... }",
        createdAt = System.currentTimeMillis()
    )
)
```

### ユースケース2: LLM 分析結果を保存する

```kotlin
db.thoughtAnalysisDao().insert(
    ThoughtAnalysisEntity(
        entryId = entryId,
        analysisJson = """{"emotion": "anger", "distortion": "overgeneralization"}""",
        createdAt = System.currentTimeMillis()
    )
)
```

### ユースケース3: 感情タイムラインをリアルタイム監視する

```kotlin
db.emotionTimelineDao()
    .getByDate("2026-02-28")
    .collect { entries ->
        // UI 更新
    }
```

### ユースケース4: トピックに紐づく ToDo を取得し完了状態を更新する

```kotlin
val dao = db.todoDao()

// Flow で監視
dao.getToDosForTopic(topicId).collect { todos -> /* UI更新 */ }

// 完了フラグ更新
dao.updateToDoStatus(id = todoId, isCompleted = true)
```

### ユースケース5: トピックを解決済みにする

```kotlin
db.conversationTopicDao().resolveTopic(
    id = topicId,
    result = "上司と話し合い、誤解が解けた",
    updatedAt = System.currentTimeMillis()
)
```
