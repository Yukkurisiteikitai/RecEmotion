/RecEmotion/app
アプリのディレクトリだね

/.cxx
?5
C/C++ build cache directoryって言う名前のcahceディレクトリっぽい
なんかCとかC++のcabocha関連のディレクトリで問題が発生した場合はここを見よう

/.gitignore
いつもの

/build
```
tree build
~~~~~
            │           ├── SetupFragment$special$$inlined$viewModels$default$5.class
            │           ├── SetupFragment$startWakeTimeCountdown$1.class
            │           ├── SetupFragment$WhenMappings.class
            │           ├── TestLLMInference.class
            │           ├── TestLLMInference$analyzeThoughtStructure$1.class
            │           └── ui
            │               ├── EmotionCursorDrawable.class
            │               ├── EmotionCursorDrawable$Companion.class
            │               └── SettingsScreenKt.class
            └── META-INF
                └── app_debug.kotlin_module

653 directories, 2584 files
```
おそらくandroidのkotlinをjavaに変換して最終的にjavaでのbuildを走らせることでbuildをしているディレクトリ

/build.gradle.kts
?3
androidのbuild設定用のファイル
Gradleのビルド設定ファイル
そういえばGradleのビルドファイルって言ってはいるけど実際、androidのjitコードを作るとかを設定していたな

/proguard-rules.pro
?5
うーん中身を作っていた?

/src
/src/androidTest
    /androidTest/java/com/example/recemotion/ExampleInstrumentedTest.kt
    import/app contextの命名ミスがないからをテスト

/src/test
名前が悪い、実験用の場所として用意しなさい。
pythonでcabochaのコンテキストをどうやって管理したらいいのかを管理するためのデレぃくとり
だめだな

/src/main
はい、いつものですね

/AndroidManifest.xml
/ic_launcher-playstore.png

/res
レイアウトとかアイコン画像とかが入っているやつ、
    /layout
        activity_main.xml
        fragment_calendar.xml
        fragment_chat.xml
        fragment_main_screen.xml
        fragment_settings.xml
        fragment_setup.xml
        item_chat_message.xml
        item_chat_output.xml
        item_system_message.xml
        item_thought_analysis.xml
        item_todo.xml
        item_topic_header.xml
    メニューの感覚が
        /chat/main/calendar/settings
        という３つでできていて、todo-> chatのtopicみたいな扱いになってるあから面倒な形式になっていそうな気がする。
# メニュー別レイアウト構成表
|メニュー |ファイル |構造 |主要コンポーネント |機能|
|:--|:--|:--|:--|:--|
|chat |fragment_chat.xml |ConstraintLayout |• カメラプレビュー (フルスクリーン)<br>• 感情/ストレスHUD<br>• チャット履歴 (RecyclerView)<br>• 入力エリア (EditText + SEND Button)<br>• LLM処理中オーバーレイ<br>• キャリブレーション待ちオーバーレイ |テキスト入力による感情分析<br>リアルタイム感情/ストレス表示<br>処理中の進捗表示|
|main |fragment_main_screen.xml |ConstraintLayout |• カメラプレビュー (フルスクリーン)<br>• 情報HUD (感情/エネルギー/ストレス)<br>• 下部コントロールエリア:<br>   - 起床時間設定 (SET Button)<br>   - ストレスレベル (Slider)<br>   - 内省入力 (EditText)<br>   - アクションボタン (SELECT MODEL / RE-CALIBRATE / ANALYZE)<br>   - フロー検証 (FLOW VERIFY)<br>• 結果表示 (RecyclerView)<br>• LLM進捗オーバーレイ<br>• キャリブレーション待ちオーバーレイ |総合的な感情分析<br>起床時間管理<br>ストレスレベル調整<br>日報/内省記録<br>モデル選択と検証|
|calendar |fragment_calendar.xml |ConstraintLayout + LinearLayout |• ヘッダー (タイトル + 月)<br>• カレンダーグリッド:<br>   - 曜日ヘッダー (M-S)<br>   - 日付セル (1-7)<br>   - カラー付きインジケーター<br>• ジャーナルセクション:<br>   - エントリ１<br>   - エントリ２<br>   - "+3 more" |月別カレンダー表示<br>感情状態の色分け表示<br>ジャーナル抄録表示|
|settings |fragment_settings.xml |ComposeView |Compose UI (実装中) |設定画面 (Compose対応)|
|レイアウト設計パターン分析|
|項目 |chat |main |calendar |settings|
|背景色 |#000000 |#000000 |#11151B (濃いグレー) |-|
|カメラ統合 |✅ |✅ |❌ |❌|
|オーバーレイ |LLM処理 + キャリブレーション |LLM処理 + キャリブレーション |❌ |❌|
|入力方式 |テキスト + 送信ボタン |テキスト + スライダー + 複数ボタン |カレンダー選択 |設定項目|
|表示形式 |RecyclerView (チャット) |RecyclerView (結果) |GridLayout + LinearLayout |ComposeView|
|UI フレームワーク |XML (Traditional) |XML (Traditional) |XML (Traditional) |Compose|


    /mipmap-*
        ic_launcher_foreground.webp 
        ic_launcher_round.webp      
        ic_launcher.webp
    androidのアイコン画像で出てくるやつシリーズ



/assets
cabochaとmecabの辞書としてipaic辞書をデータとして保持しているのでそのモデルファイルたち
.
├── cabocha_model <>
│   ├── chunk.ipa.model
│   ├── dep.ipa.model
│   └── ne.ipa.model
├── face_landmarker.task
└── ipadic
    ├── char.bin
    ├── dicrc
    ├── left-id.def
    ├── matrix.bin
    ├── pos-id.def
    ├── rewrite.def
    ├── right-id.def
    ├── sys.dic
    └── unk.dic


/jniLibs
jniで使うコード成果物 
    /librecemotion.so
    感情分析用の実行可能ライブラリ
/java
    /com/example/recemotion
.
├── CalendarFragment.kt <- Calendarのフラグメント
├── ChatFragment.kt <- 
├── data
│   ├── db
│   │   ├── AppDatabase.kt
│   │   ├── ConversationTopicDao.kt
│   │   ├── ConversationTopicEntity.kt
│   │   ├── EmotionTimelineDao.kt
│   │   ├── EmotionTimelineEntity.kt
│   │   ├── ThoughtAnalysisDao.kt
│   │   ├── ThoughtAnalysisEntity.kt
│   │   ├── ThoughtEntryDao.kt
│   │   ├── ThoughtEntryEntity.kt
│   │   ├── ToDoDao.kt
│   │   └── ToDoEntity.kt
│   ├── di
│   │   ├── DatabaseModule.kt
│   │   ├── ParserModule.kt
│   │   ├── RepositoryModule.kt
│   │   └── ServiceModule.kt
│   ├── llm
│   │   ├── InferenceProgress.kt
│   │   ├── LLMInferenceServiceImpl.kt
│   │   ├── LlmStreamEvent.kt
│   │   ├── ThoughtAnalysisJsonParser.kt
│   │   └── ThoughtPromptBuilder.kt
│   ├── parser
│   │   ├── CabochaDependencyParser.kt
│   │   ├── CabochaModelManager.kt
│   │   ├── CabochaResult.kt
│   │   ├── CabochaThoughtMapper.kt
│   │   ├── DependencyParser.kt
│   │   ├── DictionaryManager.kt
│   │   ├── LogicalFlowAnalyzer.kt
│   │   ├── LogicalFlowAnalyzerImpl.kt
│   │   ├── LogicalFlowQuestionGenerator.kt
│   │   ├── LogicalFlowReportBuilder.kt
│   │   ├── MorphemeAnalyzer.kt
│   │   ├── NativeCabochaParser.kt
│   │   ├── ParserComparisonLogger.kt
│   │   ├── RelationDetector.kt
│   │   ├── SentenceTokenizer.kt
│   │   ├── TopicChangeDetector.kt
│   │   └── TopicChangeDetectorImpl.kt
│   ├── repository
│   │   ├── ThoughtRepository.kt
│   │   └── ThoughtRepositoryImpl.kt
│   └── serialization
│       └── ThoughtStructureJsonAdapter.kt
├── domain
│   ├── model
│   │   ├── AnalysisUpdate.kt
│   │   ├── BiasDetection.kt
│   │   ├── DiagnosticMessage.kt
│   │   ├── InferenceProgress.kt
│   │   ├── LlmStreamEvent.kt
│   │   ├── LogicalFlowModels.kt
│   │   ├── MissingPerspective.kt
│   │   ├── ThoughtAnalysisResult.kt
│   │   ├── ThoughtNode.kt
│   │   └── ThoughtStructure.kt
│   ├── repository
│   │   └── ThoughtRepository.kt
│   ├── service
│   │   ├── LLMInferenceService.kt
│   │   ├── LogicalFlowService.kt
│   │   └── TopicChangeService.kt
│   └── usecase
│       ├── AnalyzeThoughtUseCase.kt
│       ├── ManageConversationUseCase.kt
│       └── SystemDiagnosticUseCase.kt
├── FaceLandmarkerHelper.kt
├── LLMInferenceHelper.kt
├── MainActivity.kt
├── MainScreenFragment.kt
├── ModelDownloadHelper.kt
├── presentation
│   ├── ChatAdapter.kt
│   ├── ConversationAdapter.kt
│   ├── ConversationDisplayItem.kt
│   ├── SettingsViewModel.kt
│   ├── SetupViewModel.kt
│   ├── ThoughtAnalysisUiState.kt
│   └── ThoughtAnalysisViewModel.kt
├── RecEmotionApplication.kt
├── settings
│   └── SetupSettings.kt
├── SettingsFragment.kt
├── SetupFragment.kt
├── TestLLMInference.kt
└── ui
    ├── EmotionCursorDrawable.kt
    ├── SettingData.java
    └── SettingsScreen.kt

16 directories, 79 files


# ライブラリ関係
/cpp
/rust/src
感情分析用のライブラリコードですね