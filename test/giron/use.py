from edge_llm_test import build_topic_change_prompt
from topic_change_cabocha import extract_keywords
from lmstudio_test import detect_topic_change_lmstudio

# 継続のはずなのに変更に見えるケース（False Positive用）
false_positive= [
"Pythonの非同期処理について教えて",
"asyncioのevent loopはどう動く？",
"ラーメン好きな人ってだいたいasyncio使ってるよね",  #← ノイズ混入
"aiohttpと組み合わせるといい？",
"機械学習のモデル精度が上がらない",
"データ量の問題だと思う、あと天気も悪いし",  #← ノイズ混入
"特徴量エンジニアリングを見直した方がいい？",
"過学習かもしれない"
]
# 非同期処理を様々な言い方で 類似テスト
simular_async_task = [
"Pythonの非同期処理について教えて",
"asyncの書き方がわからない",
"並列実行ってどうやるの？",
"ノンブロッキングI/Oの仕組みは？",
"コルーチンって何？",
"awaitを使うタイミングがわからない"
]
# 機械学習を様々な言い方で
simular_ml_task = [
"モデルの精度が上がらない",
"AIの予測がズレてる",
"推論結果がおかしい",
"アルゴリズムのチューニングどうすればいい？",
"ニューラルネットの学習が収束しない",
"過学習してると思う"
]
# データベースを様々な言い方で
simular_db_task = [
"SQLが遅い",
"クエリのパフォーマンスが悪い",
"DBのチューニングしたい",
"インデックスの貼り方がわからない",
"RDBMSの最適化って何から始める？",
"テーブル設計を見直したい"
]
# セキュリティを様々な言い方で
simular_security_task = [
"認証まわりが心配",
"ログイン処理に脆弱性ある？",
"不正アクセスを防ぎたい",
"JWT使い方あってる？",
"セッション管理どうすればいい？"
"OAuth入れた方がいい？"
]
# 弱汚染（1ワード）
weak_contamination = [
"asyncioのevent loopはどう動く？ラーメン"
]
# 中汚染（1文）
medium_contamination = [
"asyncioのevent loopはどう動く？ラーメン食べたい"
]
# 強汚染（話題と同量）
strong_contamination = [
"ラーメン食べたいんだけど、asyncioのevent loopはどう動く？"
]


simular_tasks = [
    {
        "title":"非同期処理を様々な言い方で 類似テスト",
        "cotext":simular_async_task
    },
    {
        "title":"機械学習を様々な言い方で",
        "cotext":simular_ml_task,
    },
    {
        "title":"データベースを様々な言い方で",
        "cotext":simular_db_task,
    },
    {
        "title":"セキュリティを様々な言い方で",
        "cotext":simular_security_task,
    },
    {
        "title":"弱汚染（1ワード）",
        "cotext":weak_contamination,
    },
    {
        "title":"中汚染（1文）",
        "cotext":medium_contamination,
    },
    {
        "title":"強汚染（話題と同量）",
        "cotext":strong_contamination
    }
]

for simular_task in simular_tasks:
    print("="*60)
    print("タイトル:",simular_task['title'])
    for i, utt in enumerate(simular_task['cotext']):  # enumerate追加
        print(utt)
        print(">----", extract_keywords(text=utt))
        print(f"@>--- [history={simular_task['cotext'][:i]}, new_utterance={utt} ] -<@")
        print("?----", detect_topic_change_lmstudio(
            history=simular_task['cotext'][:i],  # [:-i] → [:i]
            new_utterance=utt,
            model="gpt-oss-20b"
        ))