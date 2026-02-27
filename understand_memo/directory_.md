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
    

/src/main

/src/test

