# ボタンごとの処理まとめ

---

## MainActivity のボタン

### btnMenu（ハンバーガーアイコン）
`MainActivity.kt:53`

```kotlin
binding.btnMenu.setOnClickListener {
    binding.drawerLayout.openDrawer(GravityCompat.START)
}
```

左からナビゲーションドロワーを開くだけ。

---

### ナビゲーションドロワーのメニュー項目
`MainActivity.kt:57`

```kotlin
binding.navView.setNavigationItemSelectedListener { item ->
    when (item.itemId) {
        R.id.menu_main     -> setScreen(Screen.MAIN)
        R.id.menu_calendar -> setScreen(Screen.CALENDAR)
    }
    binding.drawerLayout.closeDrawer(GravityCompat.START)
    true
}
```

`setScreen()` の中でFragmentManagerを使い、
`MainScreenFragment` と `CalendarFragment` を show/hide で切り替える。
画面を破棄せずに切り替えているのがポイント。

---

### スワイプジェスチャー（ドロワーを開く）
`MainActivity.kt:84〜101`

mainContentエリアへのタッチイベントを `GestureDetector` に渡す。
左→右へのフリック（diffX > 100 かつ velocityX > 100）を検知したらドロワーを開く。
ボタンではなく `setOnTouchListener` で実装されている。

---

## MainScreenFragment のボタン

### btnSetWakeTime（起床時刻の設定）
`MainScreenFragment.kt:178`

```kotlin
binding.btnSetWakeTime.setOnClickListener {
    TimePickerDialog(requireContext(), { _, hour, minute ->
        wakeTimeUnix = newCal.timeInMillis / 1000   // Unix秒に変換
        binding.txtWakeTime.text = "HH:MM"
        MainActivity.initSession(wakeTimeUnix)       // JNI呼び出し → Rust
        Toast.makeText(..., "Session Reset", ...).show()
    }, ...).show()
}
```

流れ:
1. TimePickerDialogを表示
2. 選択した時刻をUnix秒に変換
3. **JNI: `initSession()`** → Rustのグローバルセッション状態をリセット（感情履歴クリア・キャリブレーターリセット）
4. 表示テキストと確認トーストを更新

---

### sliderStress（ストレスレベル 1〜5）
`MainScreenFragment.kt:193`

```kotlin
binding.sliderStress.addOnChangeListener { _, value, _ ->
    MainActivity.updateStressLevel(value.toInt())  // JNI呼び出し → Rust
    // txtStats の "STRESS: N" を正規表現で置換
    binding.txtStats.text = ...replace(Regex("STRESS: \\d+"), "STRESS: ${value.toInt()}")
}
```

スライダー値が変わるたびに:
1. **JNI: `updateStressLevel()`** → Rustのセッション状態のストレス値を更新
2. 画面上のstats表示をリアルタイムで書き換え（正規表現置換）

---

### btnReset（再キャリブレーション）
`MainScreenFragment.kt:200`

```kotlin
binding.btnReset.setOnClickListener {
    MainActivity.initSession(wakeTimeUnix)  // JNI呼び出し → Rust
}
```

現在の `wakeTimeUnix`（起床時刻）でセッションを再初期化するだけ。
顔ランドマークのキャリブレーションが最初からやり直される。
btnSetWakeTime と呼ぶJNI関数は同じ（`initSession`）。

---

### btnSelectModel（LLMモデルファイルの選択）
`MainScreenFragment.kt:205`

```kotlin
binding.btnSelectModel.setOnClickListener {
    openModelFileLauncher.launch(arrayOf("*/*"))  // ファイルピッカーを開く
}
```

ファイルピッカーでのファイル選択後（`openModelFileLauncher` コールバック）:
1. 拡張子チェック（`.bin` または `.task` のみ許可）
2. URIからアプリ内部ストレージへファイルをコピー
3. `model.bin` または `model.task` にリネーム
4. `llmInferenceHelper.initModel()` でLLMを初期化
5. 結果をUIに表示

---

### btnKuromojiTest（論理フロー検証）
`MainScreenFragment.kt:210`

```kotlin
binding.btnKuromojiTest.setOnClickListener {
    showLogicalFlowDialog()
}
```

`showLogicalFlowDialog()` はコルーチンで複数フェーズを順番に実行する:

| フェーズ | 内容 |
|---|---|
| 1 | AlertDialogでテキスト入力を受け取る |
| 2 | `LogicalFlowAnalyzer().analyze(text)` で形態素解析（Kuromoji） |
| 3 | `LogicalFlowQuestionGenerator` で検証質問を生成、ダイアログで提示 |
| 4 | Q&Aをループ（`suspendCancellableCoroutine` でダイアログの結果を同期的に待つ） |
| 5 | 回答を元に最終レポートを生成して `txtResult` に表示 |

JNI呼び出しなし。Kuromojiは純粋なKotlin/Javaライブラリ。

---

### btnAnalyze（LLM思考分析）
`MainScreenFragment.kt:215`

```kotlin
binding.btnAnalyze.setOnClickListener {
    val text = binding.edtReflection.text.toString()
    if (text.isEmpty()) { toast("..."); return@setOnClickListener }

    binding.txtResult.text = "Analyzing...\n"
    thoughtAnalysisViewModel.analyze(text)   // LLM推論を起動

    viewLifecycleOwner.lifecycleScope.launch {
        ParserComparisonLogger.compare(text, kuromojiParser, nativeParser)  // ログ比較（並列）
    }
    // キーボードを閉じる
}
```

流れ:
1. `edtReflection`（テキスト入力欄）の内容を取得
2. **`thoughtAnalysisViewModel.analyze(text)`** → LLM推論を非同期で開始
   - `AnalyzeThoughtUseCase` → `LLMInferenceHelper` → オンデバイスLLM
   - 推論結果はFlowでストリーミングされる
3. 並列でパーサー比較ログ（Kuromoji vs CaboCha）をLogcatへ出力
4. `collectThoughtAnalysisState()` コルーチンが結果を受け取りUIを更新

`txtResult` にはLLMの出力がリアルタイムでストリーミング表示される。

---

## 権限リクエストの結果処理

### カメラ権限
`MainScreenFragment.kt:80`

```kotlin
private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startCamera()
        else Toast.makeText(..., "Permission request denied", ...).show()
    }
```

許可 → `startCamera()` でCameraXを初期化してフレーム処理ループ開始
拒否 → トースト表示のみ

---

### ストレージ権限
`MainScreenFragment.kt:86`

```kotlin
private val requestStoragePermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) checkAndDownloadModel()
        else Toast.makeText(..., "Storage permission denied", ...).show()
    }
```

許可 → `checkAndDownloadModel()` でLLMモデルの確認・ダウンロード
拒否 → トースト表示のみ

---

## まとめ表

| ボタン/UI要素 | リスナーの種類 | JNI呼び出し | コルーチン使用 |
|---|---|---|---|
| btnMenu | setOnClickListener | なし | なし |
| ナビメニュー | setNavigationItemSelectedListener | なし | なし |
| スワイプ | setOnTouchListener (GestureDetector) | なし | なし |
| btnSetWakeTime | setOnClickListener | `initSession()` | なし |
| sliderStress | addOnChangeListener | `updateStressLevel()` | なし |
| btnReset | setOnClickListener | `initSession()` | なし |
| btnSelectModel | setOnClickListener | なし | なし |
| btnKuromojiTest | setOnClickListener | なし | あり（suspendで待機） |
| btnAnalyze | setOnClickListener | なし | あり（Flowでストリーミング） |
| カメラ権限 | ActivityResultContracts | なし | なし |
| ストレージ権限 | ActivityResultContracts | なし | なし |

---

---

## CaboChaの実行について詳細

### 登場する実装が2つある（紛らわしい）

| クラス | 実態 | JNI? |
|---|---|---|
| `NativeCabochaParser` | 本物のCaboCha（C++）をJNI経由で呼ぶ | あり |
| `CabochaDependencyParser` | Kuromojiで近似した偽物。名前にCaboChaとあるが中身はKuromoji | なし |

両者は同じ `DependencyParser` インターフェースを実装している。

---

### NativeCabochaParser の実行フロー（本物）

#### 1. ライブラリのロード
`NativeCabochaParser` クラスが初めてインスタンス化されるとき、
`companion object { init { System.loadLibrary("cabocha_jni") } }` が走る。
`MainScreenFragment.onViewCreated()` の最後で非同期に初期化される。

#### 2. 辞書のインストール（DictionaryManager）
MeCabは辞書ファイルをファイルシステムから `mmap()` で読むため、
assets直接アクセスは不可。初回のみ `assets/ipadic/` の9ファイルを
`filesDir/ipadic/` へコピーする。

```
assets/ipadic/
  char.bin, dicrc, left-id.def, matrix.bin,
  pos-id.def, rewrite.def, right-id.def, sys.dic, unk.dic
         ↓ DictionaryManager.install()
filesDir/ipadic/  ← NativeCabochaParserに渡すパス
```

#### 3. nativeVerify() で動作確認
`initNativeParser()` 内でインストール直後に呼ばれる。

```cpp
// cabocha_jni.cpp:146
// "テスト。" をパースして成功するか確認
// 戻り値: 0=OK, 1=init失敗, 2=parse失敗
```

#### 4. parse() の実行（Dispatchers.Default で動く）

```kotlin
// NativeCabochaParser.kt:43
override suspend fun parse(text: String): CabochaResult =
    withContext(Dispatchers.Default) {
        val json = nativeParse(mecabDicDir, cabochaModelPath, text)  // JNI
        parseJson(json)
    }
```

C++側（`cabocha_jni.cpp`）の処理:

```
1. jstring → const char* に変換
2. CaboCha::Parser::create("-r /dev/null -d <辞書パス>") でパーサー初期化
3. parser->parse(text) で係り受け解析
4. Tree の chunk_size() 個のチャンクをループ
   各チャンクの token_pos〜token_pos+token_size のトークンを取得
   surface（表層形）と feature（品詞情報、カンマ前だけ切り出し）を抽出
5. JSON文字列に組み立てて返す
6. delete parser（Treeも同時に解放される）
```

返ってくるJSON構造:
```json
{
  "chunks": [
    {
      "id": 0,
      "link": 2,
      "tokens": [
        { "surface": "今日", "pos": "名詞" },
        { "surface": "は",   "pos": "助詞" }
      ]
    },
    ...
  ]
}
```

#### 5. JSON → CabochaResult への変換

```kotlin
// chunks配列をループして CabochaChunk を生成
// CabochaChunk.text は tokens の surface をすべて連結したもの
CabochaResult(
    chunks = listOf(
        CabochaChunk(id=0, link=2, tokens=[CabochaToken("今日","名詞"), ...]),
        CabochaChunk(id=1, link=2, tokens=[...]),
        CabochaChunk(id=2, link=-1, tokens=[...])  // link=-1 が根ノード（ROOT）
    )
)
```

---

### CabochaDependencyParser の実行フロー（Kuromoji近似）

JNIなし。KuromojiのIPAdic辞書（JARに内包）を使う。

```
tokenizer.tokenize(text)  → 形態素列
         ↓ buildChunks()
1. 助詞・助動詞・記号の後で文節境界を切る
2. 句読点（。！？.!?）を含む文節で文境界を切る
3. 各文の末尾文節が根ノード（link=-1）
   それ以外の文節はすべて末尾文節へリンク（ハブ&スポーク構造）
```

例: 「今日は良い天気でした。」
```
でした。  link=-1  (ROOT)
├── 今日は  link→でした。
└── 良い天気 link→でした。
```

本物のCaboChaはCRF++モデルで統計的に係り受けを決定するが、
こちらは「とりあえず全部末尾に係る」という言語学的近似。

---

### ParserComparisonLogger（比較ツール）

`btnAnalyze` を押したとき、LLM推論と並列でバックグラウンド実行される。
UIには表示されず**Logcatにのみ**出力する。

```kotlin
// MainScreenFragment.kt - btnAnalyze listener内
viewLifecycleOwner.lifecycleScope.launch {
    ParserComparisonLogger.compare(text, kuromojiParser, nativeParser)
}
```

比較する内容:

| 比較項目 | 内容 |
|---|---|
| チャンク数 | native - kuromoji の差分 |
| リンク一致率 | 同インデックスのchunkのlinkが一致する割合（0〜100%） |
| 実行時間 | 各パーサーの ms |
| チャンク表層の差分 | インデックスごとにKuromoji/Nativeの表層文字列を比較 |

nativeParser が null（辞書未インストール）の場合はKuromojiの結果のみ記録。

---

## JNIを呼ぶボタンと呼ばない分類

**JNIを呼ぶ（Rustに触れる）**:
- `btnSetWakeTime` → `initSession()`
- `sliderStress` → `updateStressLevel()`
- `btnReset` → `initSession()`
- カメラフレームコールバック（ボタンではないが）→ `pushFaceLandmarks()` + `getAnalysisJson()`

**JNIを呼ばない（Kotlin/Java層のみ）**:
- `btnMenu`, ナビメニュー, スワイプ → 画面切り替えだけ
- `btnSelectModel` → ファイルコピーとLLM初期化
- `btnKuromojiTest` → Kuromojiによる形態素解析
- `btnAnalyze` → オンデバイスLLM推論（MediaPipe Tasks経由）
