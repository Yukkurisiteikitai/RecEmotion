# RecEmotion

RecEmotionは、リアルタイムの表情解析とオンデバイスLLM推論を組み合わせて、短い振り返りフィードバックを生成するAndroidアプリです。

## Quick Setup

1. 依存関係を取得
	- Android Studioでプロジェクトを開き、Gradle同期を実行します。
2. LLMモデルを配置
	- 次のいずれかを用意して、端末のDownloadsまたはアプリ内部ストレージに配置します。
	  - model.task
	  - model.bin
3. ビルドと実行
	- デバッグビルドを作成して端末にインストールします。
	  - ./gradlew :app:assembleDebug
4. アプリ内でモデル選択
	- SELECT MODELからファイルを選択し、読み込みが完了したらANALYZEを実行します。

## アーキテクチャ

- CameraXでフロントカメラのフレームを取得
- MediaPipe Face Landmarkerで顔ランドマークをオンデバイス抽出
- Rust JNIコアがランドマークを集計し、JSONコンテキスト(energy, stress, emotion)を構築
- MediaPipe LLM(Tasks GenAI)がJSONプロンプトを入力してレスポンスをストリーミング生成
- Kotlinが全体の制御とUI状態を管理

## Thought Structuring Engine

ユーザー入力のテキストを構造化して認知的な分析を行う新機能です。チャットではなく、思考構造の解析に特化します。

フロー:

ユーザー入力
→ 係り受け解析(CaboCha/JNI)
→ ThoughtTreeへ変換
→ 構造化テキストをLLMへ入力
→ 厳密なJSONを受信
→ UI状態を更新

追加ルール:

- 既存の感情解析パイプラインは維持し、追加機能として共存させる
- ViewModel + StateFlowを採用し、UIロジックは推論層に置かない
- 重い処理はメインスレッドで実行しない

## モデル入力

LLMモデルはアプリストレージから次の形式で読み込みます。
- model.task
- model.bin

内部ストレージを優先し、見つからなければDownloadsを参照します。

## 実行メモ

- LLMはオンデバイス推論で、ネットワーク通信は不要です。
- Face Landmarkerはカメラプレビュー中に継続実行されます。
- 生成中はLLMの進行状況がオーバーレイ表示されます。
