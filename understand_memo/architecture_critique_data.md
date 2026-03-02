アーキテクチャ Issues 調査結果

🔴 Critical（即時対応が必要） 
1. Domain層がData EntityをImport（Clean Architecture 根本違反）

domain/repository/ThoughtRepository.kt
import com.example.recemotion.data.db.ThoughtEntryEntity  // ❌ data層参照
import com.example.recemotion.data.db.ThoughtAnalysisEntity

interface ThoughtRepository {
    suspend fun getEntryById(id: Long): ThoughtEntryEntity?  // ❌ DB
Entityを返す
}
Domain層の「純粋性」が崩壊している。DBスキーマ変更がDomain層を直撃する。

2. ViewModelがDAOを直接注入

presentation/ThoughtAnalysisViewModel.kt:37-44
@HiltViewModel
class ThoughtAnalysisViewModel @Inject constructor(
    private val repository: ThoughtRepository,
    private val emotionTimelineDao: EmotionTimelineDao  // ❌ DAO直接注入
)
ViewModel → Repository → DAO の層構造が完全に破れている。

3. fallbackToDestructiveMigration() が本番コードに存在

data/db/AppDatabase.kt:30-40
Room.databaseBuilder(...)
    .fallbackToDestructiveMigration()  // ❌
スキーマ変更でユーザーデータ全削除
    .build()
本番リリース後にDBスキーマを変更すると全ユーザーのデータが消滅する。

---
🟠 High（設計上の重大な問題）

4. TestLLMInference が本番コードにフォールバックとして混入

data/llm/LLMInferenceServiceImpl.kt:155-176
if (!isInitialized || inference == null) {
    com.example.recemotion.TestLLMInference  // ❌
テストコードをproductionで使用
        .analyzeThoughtStructure(prompt).collect { emit(it) }
}
// エラー時も同様にフォールバック
本番環境でモデル未初期化またはエラー時、ユーザーにテストダミーデータが返される
。

5. 旧 data/repository/ThoughtRepository.kt が残存

同名の旧具体クラス（インターフェースなし）と新実装クラス ThoughtRepositoryImpl
 が共存。命名衝突・混乱の原因。現在はデッドコード。

---
🟡 Medium（技術的負債）

6. ThoughtStructureJsonAdapter にデシリアライズが未実装

class ThoughtStructureJsonAdapter {
    fun toJson(structure: ThoughtStructure): String { ... }
    // fromJson() が存在しない → DBからThoughtStructureを復元できない
}
書き込みはできるが読み戻しができないため、保存データが実質的に死んでいる可能性
がある。

7. nativeParser が var で公開されている

data/parser/LogicalFlowAnalyzerImpl.kt:30
var nativeParser: NativeCabochaParser? = null  // ❌ public mutable
インターフェース（LogicalFlowService）越しにアクセスするには as
LogicalFlowAnalyzerImpl のキャストが必要で、抽象化が破れている。

8. data/llm の typealias ファイルが残存（デッドレイヤー）

InferenceProgress.kt と LlmStreamEvent.kt は domain.model への typealias
のみを定義した後方互換レイヤー。削除すべきデッドコード。

---
🔵 Low（改善推奨）

9. トークン制限のマジックナンバー

data/llm/LLMInferenceServiceImpl.kt
private const val MAX_TOTAL_TOKENS = 1024      // ❌
private const val OUTPUT_TOKENS_RESERVE = 256  // ❌
ビジネスロジックがデータ層にハードコードされており、複数モデル対応時に問題にな
る。

10. DAOに @Singleton スコープなし

@Provides  // @Singleton なし → 注入のたびに新インスタンス生成
fun provideThoughtEntryDao(db: AppDatabase): ThoughtEntryDao =
db.thoughtEntryDao()

---
優先度マトリクス

┌────────┬────────────────────────────────┬──────────────────────────┐
│ 優先度 │             Issue              │           影響           │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P0     │ fallbackToDestructiveMigration │ データ消失リスク         │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P0     │ Domain層がDB Entityに依存      │ 全層への変更波及         │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P1     │ ViewModelがDAO直接注入         │ テスト不可能なコード     │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P1     │ TestLLMInference本番混入       │ 本番でダミーデータ返却   │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P2     │ デシリアライズ未実装           │ 保存データが読み戻し不能 │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P2     │ 旧ThoughtRepositoryの残存      │ 混乱・命名衝突           │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P3     │ nativeParser mutable公開       │ 抽象化の破壊             │
├────────┼────────────────────────────────┼──────────────────────────┤
│ P3     │ typealias残存                  │ デッドコード             │
└────────┴────────────────────────────────┴──────────────────────────┘

最も根本的な問題は「Domain層がDB
Entityに直接依存している」点です。これを修正するためには Domain
Model（純粋なKotlinクラス）を定義し、Repository層でEntityとのマッピングを担う
設計に移行する必要があります。