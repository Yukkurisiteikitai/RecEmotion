============================================================
タイトル: 非同期処理を様々な言い方で 類似テスト
Pythonの非同期処理について教えて
>---- ['Python', '非同期', '処理', '教える']
@>--- [history=[], new_utterance=Pythonの非同期処理について教えて ] -<@
?---- {'is_topic_change': None, 'confidence': 0.0, 'current_theme': 'error', 'reason': 'Connection error.'}
asyncの書き方がわからない
>---- ['async', '書き方', 'わかる']
@>--- [history=['Pythonの非同期処理について教えて'], new_utterance=asyncの書き方がわからない ] -<@
  [raw] {"is_topic_change": false,"confidence": 0.95,"current_theme":"Pythonの非同期処理","rea
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'Pythonの非同期処理', 'reason': '新しい発言は同じテーマ（Pythonのasync書き方）に関する質問であり、逸脱していないため継続と判断しました。'}
並列実行ってどうやるの？
>---- ['並列', '実行', 'やる']
@>--- [history=['Pythonの非同期処理について教えて', 'asyncの書き方がわからない'], new_utterance=並列実行ってどうやるの？ ] -<@
  [raw] {"is_topic_change": false,"confidence": 0.95,"current_theme":"Pythonの非同期処理","rea
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'Pythonの非同期処理', 'reason': '並列実行は非同期/並行処理に関する別側面であり、同じテーマ内の質問として扱えるため。'}
ノンブロッキングI/Oの仕組みは？
>---- ['ブロッキング', 'I', '/', 'O', '仕組み']
@>--- [history=['Pythonの非同期処理について教えて', 'asyncの書き方がわからない', '並列実行ってどうやるの？'], new_utterance=ノンブロッキングI/Oの仕組みは？ ] -<@
  [raw] {"is_topic_change":false,"confidence":0.95,"current_theme":"Pythonの非同期処理","reaso
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'Pythonの非同期処理', 'reason': '新しい発言はノンブロッキングI/Oについてで、既存のテーマ（Pythonのasync/parallel execution）と同じ領域に属するため逸脱ではない。'}
コルーチンって何？
>---- ['コルーチン', '何']
@>--- [history=['Pythonの非同期処理について教えて', 'asyncの書き方がわからない', '並列実行ってどうやるの？', 'ノンブロッキングI/Oの仕組みは？'], new_utterance=コルーチンって何？ ] -<@
  [raw] {"is_topic_change":false,"confidence":0.95,"current_theme":"Pythonの非同期処理","reaso
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'Pythonの非同期処理', 'reason': '新しい発言はコルーチンに関する質問であり、既存のテーマ（async/await構文・並列実行・ノンブロッキングI/O）と直接関連しているため逸脱ではない。'}
awaitを使うタイミングがわからない
>---- ['await', '使う', 'タイミング', 'わかる']
@>--- [history=['Pythonの非同期処理について教えて', 'asyncの書き方がわからない', '並列実行ってどうやるの？', 'ノンブロッキングI/Oの仕組みは？', 'コルーチンって何？'], new_utterance=awaitを使うタイミングがわからない ] -<@
  [raw] {"is_topic_change": false,"confidence": 0.95,"current_theme":"Pythonの非同期プログラミング"
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'Pythonの非同期プログラミング', 'reason': '新しい発言はawaitの使い方に関する質問で、既存のasync/await・並列実行・ノンブロッキングI/O・コルーチンというテーマと直接関連しているため、逸脱ではなく同一テーマの深掘りとして扱われる。'}
============================================================
タイトル: 機械学習を様々な言い方で
モデルの精度が上がらない
>---- ['モデル', '精度', '上がる']
@>--- [history=[], new_utterance=モデルの精度が上がらない ] -<@
  [raw] {"is_topic_change": true,"confidence": 0.7,"current_theme":"モデルの精度が上がらない","reaso
?---- {'is_topic_change': True, 'confidence': 0.7, 'current_theme': 'モデルの精度が上がらない', 'reason': '会話履歴に既存テーマが存在しないため、新しい発言は新規テーマと判断'}
AIの予測がズレてる
>---- ['AI', '予測', 'ズレる', 'てる']
@>--- [history=['モデルの精度が上がらない'], new_utterance=AIの予測がズレてる ] -<@
  [raw] {
  "is_topic_change": false,
  "confidence": 0.95,
  "current_theme": "モデル精度・予測
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'モデル精度・予測誤差', 'reason': '新しい発言は「AIの予測がズレてる」という表現で、既存テーマ「モデルの精度が上がらない」と同じ問題領域（機械学習モデルの性能低下）を指しているため、逸脱ではなく継続と判断できる。'}
推論結果がおかしい
>---- ['推論', '結果', 'おかしい']
@>--- [history=['モデルの精度が上がらない', 'AIの予測がズレてる'], new_utterance=推論結果がおかしい ] -<@
  [raw] {"is_topic_change":false,"confidence":0.95,"current_theme":"AIモデルの精度や予測結果のズレ","r
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'AIモデルの精度や予測結果のズレ', 'reason': '新しい発言は「推論結果がおかしい」と述べており、既存のテーマであるモデル精度向上と予測ズレに関する問題を継続しているため逸脱ではない'}
アルゴリズムのチューニングどうすればいい？
>---- ['アルゴリズム', 'チューニング', 'いい']
@>--- [history=['モデルの精度が上がらない', 'AIの予測がズレてる', '推論結果がおかしい'], new_utterance=アルゴリズムのチューニングどうすればいい？ ] -<@
  [raw] {"is_topic_change": false,"confidence": 0.95,"current_theme":"AIモデルの精度向上と予測誤差対策"
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'AIモデルの精度向上と予測誤差対策', 'reason': '新発言は「アルゴリズムのチューニング」について尋ねており、既存のテーマであるモデル精度・推論結果の問題に直接関連しているため、逸脱ではなく同一テーマ内の深掘りと判断した。'}
ニューラルネットの学習が収束しない
>---- ['ニューラルネット', '学習', '収束']
@>--- [history=['モデルの精度が上がらない', 'AIの予測がズレてる', '推論結果がおかしい', 'アルゴリズムのチューニングどうすればいい？'], new_utterance=ニューラルネットの学習が収束しない ] -<@
  [raw] {
  "is_topic_change": false,
  "confidence": 0.95,
  "current_theme": "機械学習モデルの
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': '機械学習モデルの性能・トラブルシューティング', 'reason': 'ニューラルネットの学習が収束しないという発言は、モデル精度向上や予測ズレ、推論結果の問題と同じくMLモデルの訓練・チューニングに関する内容であり、テーマから逸脱していません。'}
過学習してると思う
>---- ['学習', 'てる', '思う']
@>--- [history=['モデルの精度が上がらない', 'AIの予測がズレてる', '推論結果がおかしい', 'アルゴリズムのチューニングどうすればいい？', 'ニューラルネットの学習が収束しない'], new_utterance=過学習してると思う ] -<@
  [raw] {
  "is_topic_change": false,
  "confidence": 0.95,
  "current_theme": "機械学習モデルの
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': '機械学習モデルのトレーニングと予測精度に関する問題', 'reason': '新しい発言は過学習という既存テーマ（モデルの不良な推論や収束しない学習）に関連する別側面であり、全体的な議題から逸脱していない。'}
============================================================
タイトル: データベースを様々な言い方で
SQLが遅い
>---- ['SQL', '遅い']
@>--- [history=[], new_utterance=SQLが遅い ] -<@
  [raw] {"is_topic_change":true,"confidence":0.95,"current_theme":"SQLパフォーマンス","reason":
?---- {'is_topic_change': True, 'confidence': 0.95, 'current_theme': 'SQLパフォーマンス', 'reason': '会話の開始時点でテーマが設定されていないため、最初の発言は新しいテーマとして扱われる。'}
クエリのパフォーマンスが悪い
>---- ['クエリ', 'パフォーマンス', '悪い']
@>--- [history=['SQLが遅い'], new_utterance=クエリのパフォーマンスが悪い ] -<@
  [raw] {
  "is_topic_change": false,
  "confidence": 0.95,
  "current_theme": "SQLパフォーマ
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'SQLパフォーマンス改善', 'reason': '新しい発言は「クエリのパフォーマンスが悪い」という表現で、既存テーマ『SQLが遅い』と同一の問題を再度述べているだけ。内容の方向性に変化はないため継続と判断した。'}
DBのチューニングしたい
>---- ['DB', 'チューニング']
@>--- [history=['SQLが遅い', 'クエリのパフォーマンスが悪い'], new_utterance=DBのチューニングしたい ] -<@
  [raw] {"is_topic_change":false,"confidence":0.95,"current_theme":"データベース性能最適化","reason
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'データベース性能最適化', 'reason': '新しい発言は「DBのチューニングしたい」とあり、既存のテーマであるSQLやクエリパフォーマンス改善と同一領域を指しているため、逸脱ではなく継続と判断できる。'}
インデックスの貼り方がわからない
>---- ['インデックス', '貼る', '方', 'わかる']
@>--- [history=['SQLが遅い', 'クエリのパフォーマンスが悪い', 'DBのチューニングしたい'], new_utterance=インデックスの貼り方がわからない ] -<@
  [raw] {"is_topic_change": false,"confidence": 0.95,"current_theme":"DBパフォーマンスチューニング","
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'DBパフォーマンスチューニング', 'reason': 'インデックスの貼り方はDB性能向上に関する具体的な手法であり、既存テーマから逸脱していないため継続と判断した。'}
RDBMSの最適化って何から始める？
>---- ['RDBMS', '最適', '化', '何', '始める']
@>--- [history=['SQLが遅い', 'クエリのパフォーマンスが悪い', 'DBのチューニングしたい', 'インデックスの貼り方がわからない'], new_utterance=RDBMSの最適化って何から始める？ ] -<@
  [raw] {"is_topic_change": false,"confidence": 0.95,"current_theme":"データベースのパフォーマンス最適化"
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'データベースのパフォーマンス最適化', 'reason': '新しい発言はRDBMSの最適化に関する質問であり、既存のテーマ（SQL遅延・クエリ性能改善・インデックス貼り方）と同一領域を扱っているため逸脱ではない。'}
テーブル設計を見直したい
>---- ['テーブル', '設計', '見直す']
@>--- [history=['SQLが遅い', 'クエリのパフォーマンスが悪い', 'DBのチューニングしたい', 'インデックスの貼り方がわからない', 'RDBMSの最適化って何から始める？'], new_utterance=テーブル設計を見直したい ] -<@
  [raw] {
  "is_topic_change": false,
  "confidence": 0.95,
  "current_theme": "データベースパフ
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': 'データベースパフォーマンス最適化', 'reason': 'テーブル設計の見直しはDBチューニングやインデックス設計と同じテーマ内であり、議題から逸脱していない。'}
============================================================
タイトル: セキュリティを様々な言い方で
認証まわりが心配
>---- ['認証', 'まわり', '心配']
@>--- [history=[], new_utterance=認証まわりが心配 ] -<@
  [raw] {
  "is_topic_change": true,
  "confidence": 0.3,
  "current_theme": "未定",
  "re
?---- {'is_topic_change': True, 'confidence': 0.3, 'current_theme': '未定', 'reason': '会話履歴がないため、現在のテーマを把握できず、新しい発言は新たなトピックと判断した。'}
ログイン処理に脆弱性ある？
>---- ['ログイン', '処理', '脆弱', '性']
@>--- [history=['認証まわりが心配'], new_utterance=ログイン処理に脆弱性ある？ ] -<@
  [raw] {"is_topic_change": false,"confidence": 0.95,"current_theme":"認証の安全性","reason":"
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': '認証の安全性', 'reason': '新しい発言はログイン処理に関する質問であり、既存の「認証まわりが心配」というテーマから逸脱していない。'}
不正アクセスを防ぎたい
>---- ['不正', 'アクセス', '防ぐ']
@>--- [history=['認証まわりが心配', 'ログイン処理に脆弱性ある？'], new_utterance=不正アクセスを防ぎたい ] -<@
  [raw] {
  "is_topic_change": false,
  "confidence": 0.95,
  "current_theme": "認証とログインの
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': '認証とログインのセキュリティ', 'reason': '新しい発言は不正アクセス防止という観点で、既存の認証・脆弱性に関する議題を継続しているため逸脱ではない。'}
JWT使い方あってる？
>---- ['JWT', '使い方', 'てる']
@>--- [history=['認証まわりが心配', 'ログイン処理に脆弱性ある？', '不正アクセスを防ぎたい'], new_utterance=JWT使い方あってる？ ] -<@
  [raw] {"is_topic_change":false,"confidence":0.95,"current_theme":"認証とアクセス制御のセキュリティ","r
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': '認証とアクセス制御のセキュリティ', 'reason': 'JWTは認証に関する技術であり、ログイン処理や不正アクセス防止という既存テーマの一部として扱えるため、逸脱ではない。'}
セッション管理どうすればいい？OAuth入れた方がいい？
>---- ['セッション', '管理', 'いい', 'OAuth', '入れる', '方', 'いい']
@>--- [history=['認証まわりが心配', 'ログイン処理に脆弱性ある？', '不正アクセスを防ぎたい', 'JWT使い方あってる？'], new_utterance=セッション管理どうすればいい？OAuth入れた方がいい？ ] -<@
  [raw] {"is_topic_change":false,"confidence":0.95,"current_theme":"認証・ログインセキュリティ","reas
?---- {'is_topic_change': False, 'confidence': 0.95, 'current_theme': '認証・ログインセキュリティ', 'reason': '新発言はセッション管理やOAuthなど、既存の認証とアクセス制御に関する話題を深掘りしているため、テーマから逸脱していない。'}
============================================================
タイトル: 弱汚染（1ワード）
asyncioのevent loopはどう動く？ラーメン
>---- ['asyncio', 'event', 'loop', '動く', 'ラーメン']
@>--- [history=[], new_utterance=asyncioのevent loopはどう動く？ラーメン ] -<@
  [raw] {"is_topic_change": true,"confidence": 0.95,"current_theme":"asyncio event loop"
?---- {'is_topic_change': True, 'confidence': 0.95, 'current_theme': 'asyncio event loop', 'reason': '新しい発言は会話の開始時点で既存テーマが無いため、全く新しいトピックとして扱われる。'}
============================================================
タイトル: 中汚染（1文）
asyncioのevent loopはどう動く？ラーメン食べたい
>---- ['asyncio', 'event', 'loop', '動く', 'ラーメン', '食べる']
@>--- [history=[], new_utterance=asyncioのevent loopはどう動く？ラーメン食べたい ] -<@
  [raw] {"is_topic_change": true, "confidence": 0.95, "current_theme": "新規テーマ", "reason"
?---- {'is_topic_change': True, 'confidence': 0.95, 'current_theme': '新規テーマ', 'reason': '会話履歴に前のテーマがないため、今回の発言は全く新しいトピックとして扱われる。'}
============================================================
タイトル: 強汚染（話題と同量）
ラーメン食べたいんだけど、asyncioのevent loopはどう動く？
>---- ['ラーメン', '食べる', 'ん', 'asyncio', 'event', 'loop', '動く']
@>--- [history=[], new_utterance=ラーメン食べたいんだけど、asyncioのevent loopはどう動く？ ] -<@
  [raw] {"is_topic_change": true, "confidence": 0.95, "current_theme": "asyncio", "reaso
?---- {'is_topic_change': True, 'confidence': 0.95, 'current_theme': 'asyncio', 'reason': '新しい発言は会話の初めであり、以前に設定されたテーマがないため、新たなトピックとして扱われる。'}
