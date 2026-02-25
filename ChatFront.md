ChatFragment 実装プラン                                                                                                         
                                                                                                                               
 Context                                                                                                                       

 ui_goal.md の「2. chat画面メニュー」を実装する。
 現在の MainScreenFragment を参考ベースとしつつ、新規 ChatFragment を作成する。
 要件: 感情カーソル・Markdownレンダリング・感情タイムラインDB・コピー機能・履歴保存。

 ---
 実装ステップ

 Step 1: Markwon 追加

 ファイル: app/build.gradle.kts
 implementation("io.noties.markwon:core:4.6.2")

 ---
 Step 2: 感情タイムライン DB

 新規作成: data/db/EmotionTimelineEntity.kt
 @Entity(tableName = "emotion_timeline")
 data class EmotionTimelineEntity(
     @PrimaryKey(autoGenerate = true) val id: Long = 0,
     val timestamp: Long = System.currentTimeMillis(),
     val emotion: String,       // Rust JNI の current_emotion
     val stressLevel: Int,      // 1-5
     val energyLevel: Int,      // Rust context の energy_level
     val sessionDate: String,   // yyyy-MM-dd
     val trigger: String        // "analysis" | "periodic"
 )

 新規作成: data/db/EmotionTimelineDao.kt
 - insert(entity) suspend
 - getByDate(date): Flow<List<...>>
 - getAroundTime(start, end): List<...> - 思考エントリの時刻周辺を取得

 修正: data/db/AppDatabase.kt
 - entities に EmotionTimelineEntity::class 追加
 - version を +1 (マイグレーション Migration(oldV, newV) で emotion_timeline テーブル作成)
 - emotionTimelineDao() abstract 関数追加

 ---
 Step 3: EmotionCursorDrawable

 新規作成: ui/EmotionCursorDrawable.kt
 - Drawable を継承、draw() で縦線を描画
 - updateEmotion(emotion: String, stress: Int) で色を更新
 - 感情→色マッピング:
   - HAPPY → #4CAF50 (緑)
   - SAD → #2196F3 (青)
   - ANGRY → #F44336 (赤)
   - FEARFUL → #FF9800 (橙)
   - DISGUSTED → #9C27B0 (紫)
   - SURPRISED → #FFEB3B (黄)
   - NEUTRAL / その他 → #FFFFFF (白)
 - ストレスレベルで alpha を変化 (stress=5 → alpha=255, stress=1 → alpha=128)
 - invalidateSelf() で再描画トリガー

 使用側で editText.setTextCursorDrawable(emotionCursorDrawable) (API 29+)
 API 28以下は textCursorDrawable xml attribute でフォールバック

 ---
 Step 4: Chat アイテムレイアウト

 新規: res/layout/item_chat_message.xml (ユーザー入力バブル)
 ┌─ 感情カラーバー (4dp height, 全幅) ─────────────────┐
 │ 入力テキスト (右揃えバブル)               [タイムスタンプ] │
 └─────────────────────────────────────────────────────┘
 - View (emotionBar) - emotion 色 + alpha
 - TextView (txtInput) - 14sp, 右揃え
 - TextView (txtTimestamp) - 10sp, グレー

 新規: res/layout/item_chat_output.xml (LLM 出力)
 ┌─ 感情カラーバー (4dp height, 全幅) ─────────────────┐
 │ LLM 出力テキスト (Markwon レンダリング)   [コピーBtn] │
 └─────────────────────────────────────────────────────┘
 - View (emotionBar) - emotion 色
 - TextView (txtOutput) - Markwon で markdown レンダリング
 - ImageButton (btnCopy) - コピーアイコン

 ---
 Step 5: ChatAdapter

 新規作成: presentation/ChatAdapter.kt
 sealed class ChatDisplayItem:
 - UserMessage(id, text, emotion, stressLevel, timestamp)
 - AssistantOutput(id, markdownText, emotion, timestamp)
 - TopicDivider(id, title) - 既存 TopicHeader を流用
 - SystemNotice(id, message, isError) - 既存 SystemMessage を流用

 ViewHolder:
 - UserMessageViewHolder - カラーバー + テキスト表示
 - AssistantOutputViewHolder - Markwon でレンダリング + コピーボタン
 - TopicDividerViewHolder
 - SystemNoticeViewHolder

 ---
 Step 6: fragment_chat.xml レイアウト

 MainScreenFragment の構成を参考に:
 ┌─ PreviewView (カメラ、フルスクリーン背景) ──────────┐
 │ ┌─ Top HUD: emotion/stress リアルタイム表示 ──────┐ │
 │ │ HAPPY ●  STRESS ■■■□□                        │ │
 │ └──────────────────────────────────────────────┘ │
 │                                                   │
 │ ┌─ RecyclerView (チャット履歴) ──────────────────┐ │
 │ │ [UserMessage] [AssistantOutput] ...           │ │
 │ └──────────────────────────────────────────────┘ │
 │                                                   │
 │ ┌─ Bottom Input Card ────────────────────────────┐ │
 │ │ [EditText with emotion cursor] [ANALYZE btn]  │ │
 │ │ (カーソル色 = 感情色)                           │ │
 │ └──────────────────────────────────────────────┘ │
 │ ┌─ Progress overlay ─────────────────────────────┐ │
 │ └──────────────────────────────────────────────┘ │
 └───────────────────────────────────────────────────┘

 要素:
 - @+id/viewFinder - PreviewView
 - @+id/cardTopHud - 感情/ストレスリアルタイム表示 (CardView)
   - txtCurrentEmotion - 現在の感情文字
   - txtStressBar - ストレスレベルテキスト or 独自View
 - @+id/recyclerChat - LinearLayoutManager (reverseLayout=false)
 - @+id/edtChatInput - EditText (カーソルのみ感情色)
 - @+id/btnSendAnalyze - FAB or Button
 - @+id/progressContainer + @+id/progressBar
 - @+id/overlayCalibration - MainScreenFragment から流用

 ---
 Step 7: ChatFragment.kt

 新規作成、主要実装:

 1. カメラ: MainScreenFragment から startCamera() / stopCamera() をほぼ流用
 2. FaceLandmarkerHelper: 同様に流用
 3. onResults() での処理:
   - MainActivity.pushFaceLandmarks(flattened) を呼ぶ
   - getAnalysisJson("") で emotion/stress を取得
   - emotionCursorDrawable.updateEmotion(emotion, stress) でカーソル色更新
   - HUD の感情テキスト更新
   - 定期的 (30秒ごと) に EmotionTimeline に記録
 4. 解析ボタン:
   - 送信時に emotionTimelineDao.insert(trigger="analysis") でスナップショット
   - thoughtAnalysisViewModel.analyze(text) 呼び出し
 5. ViewModel 収集:
   - historyItems → ChatAdapter に変換して表示
   - progress → progressBar 制御
   - uiState.partialStreamingText → リアルタイム表示 (既存パターン流用)

 ---
 Step 8: ThoughtPromptBuilder 拡張

 修正: data/llm/ThoughtPromptBuilder.kt
 - buildPromptWithEmotionContext(thought, emotionTimeline) を追加
 - emotionTimeline (List) を「感情状態ログ」としてプロンプトに追記:
 ## Emotion State Log (at analysis time)
 - 14:32: HAPPY (stress=2, energy=4)
 - 14:45: NEUTRAL (stress=3, energy=3)

 修正: presentation/ThoughtAnalysisViewModel.kt
 - EmotionTimelineDao を DI で注入
 - analyzeWithEmotion(text, emotionTimeline) メソッド追加 (or 既存 analyze() に emotion context を渡す)

 ---
 Step 9: Navigation 統合

 修正: res/menu/nav_drawer_menu.xml
 - menu_chat アイテムを追加 (iconはメッセージアイコン)

 修正: MainActivity.kt
 - Screen enum に CHAT 追加
 - ChatFragment を TAG_CHAT で追加・管理
 - menu_chat → setScreen(Screen.CHAT)
 - onSetupComplete() の遷移先を Screen.CHAT に変更 (メインがChatに)

 ---
 変更ファイル一覧

 ┌──────────────────────────────────────────┬───────────────────────────────┐
 │                 ファイル                 │             種別              │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ app/build.gradle.kts                     │ 修正 (Markwon追加)            │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ data/db/EmotionTimelineEntity.kt         │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ data/db/EmotionTimelineDao.kt            │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ data/db/AppDatabase.kt                   │ 修正 (entity追加 + version+1) │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ ui/EmotionCursorDrawable.kt              │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ res/layout/item_chat_message.xml         │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ res/layout/item_chat_output.xml          │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ presentation/ChatAdapter.kt              │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ res/layout/fragment_chat.xml             │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ ChatFragment.kt                          │ 新規                          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ data/llm/ThoughtPromptBuilder.kt         │ 修正 (emotion context追加)    │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ presentation/ThoughtAnalysisViewModel.kt │ 修正 (EmotionTimelineDao注入) │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ res/menu/nav_drawer_menu.xml             │ 修正 (chat item追加)          │
 ├──────────────────────────────────────────┼───────────────────────────────┤
 │ MainActivity.kt                          │ 修正 (ChatFragment追加)       │
 └──────────────────────────────────────────┴───────────────────────────────┘

 ---
 検証方法

 1. Build成功 → APK インストール
 2. ChatFragment が nav drawer から開けること
 3. カメラ起動 → キャリブレーション完了後、入力フィールドのカーソル色が感情に応じて変化すること (logcat で emotion 確認)
 4. テキスト入力 → ANALYZE → LLM 出力が Markdown 形式でレンダリングされること (# ヘッダー、コード、太字 など)
 5. コピーボタン → クリップボードにコピーされること
 6. 感情タイムライン → DB Inspector で emotion_timeline テーブルにレコードが追加されていること
 7. nav drawer の "Chat" と既存メニューで画面切り替えができること