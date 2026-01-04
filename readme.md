# Simple Pinball

Android用のシンプルなピンボールデモです。`MainActivity.kt` だけで盤面描画や簡単な物理挙動、パドル・プランジャー操作を実装しています。

## 構成
- Jetpack Compose でキャンバスを描画
- 左右のタップでパドルを開閉
- 右下のプランジャーをドラッグして引き、離すとボールを発射

## ビルド
```
./gradlew assembleDebug
```
Android Studio で開いて実行することもできます。

> 注: この環境では `gradle-wrapper.jar` をダウンロードできなかったため空ファイルになっています。お手元で `./gradlew wrapper` を実行するか、Gradle 公式配布物から wrapper JAR を取得して差し替えてください。
