# Data Serialization Architecture

対象: `app/src/main/java/com/example/recemotion/data/serialization/`

---

## 1. 実行順序

```
呼び出し元 (Repository など)
  └── ThoughtStructureJsonAdapter.toJson(structure)
        └── nodesToJson(structure.roots)          // ルートノード一覧をJSONArrayに変換
              └── nodesToJson(node.children)       // 子ノードを再帰的に変換
                    └── nodesToJson(...)           // 末端ノードに達するまで繰り返し
```

---

## 2. クラスの実行で目的をどう達成しているか

### `ThoughtStructureJsonAdapter`

`ThoughtStructure`（思考ツリー全体）をJSON文字列に変換し、永続化（DBやファイル保存）を可能にする。

- `toJson(structure)` : ツリーのエントリポイント。ルートに `"roots"` キーを持つJSONObjectを生成し、文字列として返す。
- `nodesToJson(nodes)` : `List<ThoughtNode>` を JSONArray に変換する再帰関数。各ノードについて `id`, `text`, `children` の3フィールドを持つJSONObjectを構築し、`children` に対して自身を再帰呼び出しすることでツリー構造を表現する。

再帰終了条件は `node.children` が空リストになること（末端ノード）。

---

## 3. 目的適合の理由

| 設計選択 | 理由 |
|---|---|
| 標準ライブラリ `org.json` を使用 | 外部シリアライザ (Gson, Moshi) への依存を避け、軽量に保つ |
| 再帰によるツリー変換 | `ThoughtNode` がネストした木構造であるため、反復より再帰が自然かつ簡潔 |
| `@Inject constructor()` | Hilt によるDI対応。Repository から注入して利用でき、テスト時にモック置換も容易 |
| シリアライズのみ実装 (デシリアライズなし) | 現時点でDBからの復元が不要であることを示す。YAGNI原則に従った最小実装 |

---

## 4. クラスの依存関係

```
ThoughtStructureJsonAdapter
  ├── (入力) ThoughtStructure       // domain/model - ルートノードのリストを保持
  ├── (入力) ThoughtNode            // domain/model - id, text, children を持つ木構造ノード
  └── (出力) String (JSON)
```

- `ThoughtStructure` と `ThoughtNode` はドメインモデル層のクラス。アダプターはデータ層に属し、ドメイン→データの変換を担う。
- `javax.inject.Inject` のみインポートし、フレームワーク依存は最小限。

---

## 5. 使用例・ユースケース

### 典型的な呼び出し (Repository 内)

```kotlin
class ThoughtRepositoryImpl @Inject constructor(
    private val adapter: ThoughtStructureJsonAdapter,
    private val dao: ThoughtDao
) {
    suspend fun save(structure: ThoughtStructure) {
        val json = adapter.toJson(structure)  // ドメインモデル → JSON文字列
        dao.insert(ThoughtEntity(data = json))
    }
}
```

### 変換例

入力:
```
ThoughtStructure(
  roots = [
    ThoughtNode(id=1, text="仕事が辛い", children=[
      ThoughtNode(id=2, text="上司が怖い", children=[])
    ])
  ]
)
```

出力JSON:
```json
{
  "roots": [
    {
      "id": 1,
      "text": "仕事が辛い",
      "children": [
        {
          "id": 2,
          "text": "上司が怖い",
          "children": []
        }
      ]
    }
  ]
}
```
