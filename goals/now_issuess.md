初期化制御の集中管理 - 二重ポップアップ修正

Context

JNIエラーの警告ポップアップがChat画面で二重表示される。原因は以下の2経路が同時に実行されるため:   

経路1 - LLMInferenceServiceImpl Singleton の init { initModel() } が起動時に自動実行    
→ エラー発生時 LlmStage.ERROR を emit → ThoughtAnalysisViewModel.uiState.error に伝播   
→ ChatFragment (表示中・RESUMED) が収集して Toast 表示

経路2 - MainScreenFragment.onViewCreated() が 非表示状態でも実行される
→ 行169: checkAndDownloadModel() が呼ばれる
→ モデル未発見の場合: AlertDialog が Activity に表示（非表示Fragmentからでも表示される）
→ モデル発見の場合: thoughtAnalysisViewModel.initModel() = llmService.reloadModel() を呼び出し    
   (Singleton が既に initJob を走らせている状態での 無駄な二重起動)   

MainActivityのFragmentタグ構成:   
  chatFrag  → ADD (表示) 
  mainFrag  → ADD + HIDE (非表示だがRESUMED状態)
  他Fragment → ADD + HIDE
非表示Fragmentはshow()/hide()パターンではRESUMED状態を維持するため、
StateFlowの収集もActivityレベルDialogの表示も両方可能。

解決方針 

MainScreenFragmentを「LLM初期化UIの唯一の担当画面」として集中管理する。 

1. MainScreenFragment: 非表示時は checkAndDownloadModel() を実行しない（defer）
2. LLMInferenceService: isModelReady: StateFlow<Boolean> を公開し初期化状態を観測可能にする
3. ThoughtAnalysisViewModel: isModelReady を転送（Chat VMとの共通ViewModel境界を維持）  
4. ChatFragment: init フェーズのエラーToastを抑制（モデル未ロード時はMainScreenFragmentが担当）   

変更ファイル一覧 (5ファイル)      

---    
変更1: domain/service/LLMInferenceService.kt 

isModelReady: StateFlow<Boolean> をインターフェースに追加。  

// 追加 (line 17付近):   
/** True when the LLM model has been successfully loaded and is ready for inference. */ 
val isModelReady: StateFlow<Boolean>

---    
変更2: data/llm/LLMInferenceServiceImpl.kt 

_isModelReady MutableStateFlow を追加し、isInitialized の全書き込み箇所に同期更新を追加。

// 追加 (line 43付近、initLock 宣言の直後):
private val _isModelReady = MutableStateFlow(false) 
override val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

isInitialized の全書き込み箇所 (7箇所) に _isModelReady.value = ... を追加:

|行 |現状 |変更後 |
|:---|---|---|
|71 |isInitialized = false |isInitialized = false; _isModelReady.value = false |
|86 |isInitialized = false |isInitialized = false; _isModelReady.value = false |
|96 |isInitialized = false |isInitialized = false; _isModelReady.value = false |
|108 |isInitialized = false |isInitialized = false; _isModelReady.value = false |
|126 |isInitialized = false |isInitialized = false; _isModelReady.value = false |
|131 |isInitialized = true |isInitialized = true; _isModelReady.value = true |
|142 |isInitialized = false |isInitialized = false; _isModelReady.value = false |
|223 (close) |isInitialized = false |isInitialized = false; _isModelReady.value = false |


注意: generateResponse() と analyzeThoughtStructure() の実行時エラーは                  
isInitialized = false をセットしない（モデルはロード済みのまま）。                      
これにより isModelReady は init フェーズ失敗のみ false になり、実行時エラーでは true を維持する。   │
                                                                                        
---                                                                                     
変更3: presentation/ThoughtAnalysisViewModel.kt                                         
                                                                                        
isModelReady を llmService から転送（既存パターン: progress 転送と同じ）。              
                                                                                        
// llmService.progress の転送と同じ場所に追加:                                          
val isModelReady: StateFlow<Boolean> get() = llmService.isModelReady                    
                                                                                        
---                                                                                     
変更4: MainScreenFragment.kt                                                            
                                                                                        
核心の修正: checkAndDownloadModel() を非表示時に実行しない。                            
                                                                                        
4a. フィールド追加 (クラス上部の変数宣言エリア):                                        
                                                                                        
private var pendingModelCheck = false                                                   
                                                                                        
4b. onViewCreated() の行169を置換:                                                      
                                                                                        
// BEFORE:                                                                              
checkAndDownloadModel()                                                                 
                                                                                        
// AFTER:                                                                               
if (isHidden) {                                                                         
    pendingModelCheck = true   // 非表示時はフラグだけ立てて defer                      
} else {                                                                                
    checkAndDownloadModel()    // 表示中なら即実行                                      
}                                                                                       
                                                                                        
4c. onHiddenChanged() (行192) を拡張:                                                   
                                                                                        
// BEFORE:                                                                              
override fun onHiddenChanged(hidden: Boolean) {                                         
    super.onHiddenChanged(hidden)                                                       
    if (hidden) stopCamera() else startCamera()                                         
}                                                                                       
                                                                                        
// AFTER:                                                                               
override fun onHiddenChanged(hidden: Boolean) {                                         
    super.onHiddenChanged(hidden)                                                       
    if (hidden) {                                                                       
        stopCamera()                                                                    
    } else {                                                                            
        startCamera()                                                                   
        if (pendingModelCheck) {                                                        
            pendingModelCheck = false                                                   
            checkAndDownloadModel()   // ユーザーがMainメニューに来た瞬間に初めて実行   
        }                                                                               
    }                                                                                   
}                                                                                       
                                                                                        
効果: MainScreenFragment が非表示の状態（Chat画面など）では AlertDialog も              
reloadModel() の冗長呼び出しも発生しない。                                              
                                                                                        
---                                                                                     
変更5: ChatFragment.kt                                                                  
                                                                                        
init フェーズのエラー Toast を抑制する。                                                
                                                                                        
collectViewModelState() の行299〜302を修正:                                             
                                                                                        
// BEFORE:                                                                              
state.error?.let { error ->                                                             
    binding.chatProgressContainer.visibility = View.GONE                                
    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()                  
}                                                                                       
                                                                                        
// AFTER:                                                                               
state.error?.let { error ->                                                             
    binding.chatProgressContainer.visibility = View.GONE                                
    // init エラー (モデル未ロード) は MainScreenFragment が担当するため Chat では抑制  
    // モデルがロード済みの場合のみ (= 実行時エラー) Toast を表示                       
    if (viewModel.isModelReady.value) {                                                 
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()              
    }                                                                                   
}                                                                                       
                                                                                        
判定ロジック:                                                                           
- isModelReady = false → init フェーズのエラー → Toast 抑制 (MainScreenFragment が担当) 
- isModelReady = true → モデルロード済みの実行時エラー → Toast 表示 (正当なユーザー通知)
                                                                                        
---                                                                                     
変更後の動作フロー                                                                      
                                                                                        
アプリ起動 (ChatFragment が表示)                                                        
  ├─ Singleton init { initModel() }                                                     
  │     モデル未発見 → LlmStage.ERROR + _isModelReady.value = false                     
  │                                                                                     
  ├─ ChatFragment.onViewCreated() → collectViewModelState()                             
  │     state.error あり + isModelReady.value == false → Toast 抑制 ✓                   
  │                                                                                     
  └─ MainScreenFragment.onViewCreated() (非表示) → isHidden == true                     
        → pendingModelCheck = true (defer) → AlertDialog は表示されない ✓               
                                                                                        
ユーザーがMainメニューに移動                                                            
  └─ onHiddenChanged(hidden=false) → pendingModelCheck == true                          
        → checkAndDownloadModel() 実行                                                  
        → AlertDialog "LLM Model Required" が Main 画面に表示 ✓ (1回のみ)               
                                                                                        
---                                                                                     
確認方法                                                                                
                                                                                        
1. ビルド: ./gradlew compileDebugKotlin → BUILD SUCCESSFUL                              
  - LLMInferenceService インターフェース追加により LLMInferenceServiceImpl がコンパイルエラーになる │
→ 変更2で解消されることを確認                                                           
2. 起動テスト (モデル未配置):                                                           
  - アプリ起動 → Chat画面                                                               
  - ポップアップが1つも出ないことを確認                                                 
  - Mainメニューを開く → AlertDialog が1回だけ表示されることを確認                      
3. 起動テスト (モデル配置済み):                                                         
  - アプリ起動 → Chat画面                                                               
  - Toastが出ないことを確認 (isModelReady=false 期間中)                                 
  - モデルロード完了後、Chat画面でテキスト送信 → 正常動作                               
4. Logcat確認:                                                                          
  - [initModel] skipped が非表示Fragment由来で出ないことを確認                          
  - pendingModelCheck ログ (任意で追加) でdeferが機能していることを確認