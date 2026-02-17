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

## モデル入力

LLMモデルはアプリストレージから次の形式で読み込みます。
- model.task
- model.bin

内部ストレージを優先し、見つからなければDownloadsを参照します。

## 実行メモ

- LLMはオンデバイス推論で、ネットワーク通信は不要です。
- Face Landmarkerはカメラプレビュー中に継続実行されます。
- 生成中はLLMの進行状況がオーバーレイ表示されます。
