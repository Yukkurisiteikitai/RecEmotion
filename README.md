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

# version関連情報
### 開発環境
- **Gradle**: 8.13
- **Android Gradle Plugin (AGP)**: 8.13.2
- **Kotlin**: 2.0.21
- **KSP**: 2.0.21-1.0.28
- **Java**: 11 (source/target compatibility)
- **Cargo**: 1.88.0 (873a06493 2025-05-10)

### Android SDK
- **compileSdk**: 35
- **minSdk**: 26
- **targetSdk**: 35
- **NDK**: 29.0.14206865
- **CMake**: 3.22.1
- **Target ABI**: arm64-v8a only (16KB page alignment対応)

### 主要ライブラリ
#### UI & Architecture
- **Jetpack Compose BOM**: 2024.12.01
- **Activity Compose**: 1.9.3
- **Lifecycle Compose**: 2.8.7
- **Lifecycle Runtime/ViewModel**: 2.7.0
- **Material**: 1.10.0
- **AppCompat**: 1.6.1
- **ViewPager2**: 1.1.0

#### DI & Database
- **Hilt**: 2.51.1
- **Room**: 2.6.1
- **DataStore Preferences**: 1.1.1

#### Camera & ML
- **CameraX**: 1.3.1
- **MediaPipe (Vision/GenAI)**: 0.10.+
- **Kuromoji (形態素解析)**: 0.9.0

#### その他
- **WorkManager**: 2.9.0
- **Markwon (Markdown)**: 4.6.2

#### Testing
- **JUnit**: 4.13.2
- **AndroidX JUnit**: 1.3.0
- **Espresso Core**: 3.7.0