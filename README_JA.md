# ServerChan

[![Server Tests](https://github.com/himekifee/ServerChan/actions/workflows/server-test.yml/badge.svg)](https://github.com/himekifee/ServerChan/actions/workflows/server-test.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Modrinth](https://img.shields.io/modrinth/dt/frZpQL6O?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/serverchan)
[![CurseForge](https://img.shields.io/curseforge/dt/1393571?logo=curseforge&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/serverchan)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.12--1.21-green.svg)](https://minecraft.net)
[![Discord](https://img.shields.io/discord/1450587683176054796?logo=discord&label=Discord)](https://discord.gg/NuzHC7BCDc)

<p align="center">
  <img src="https://raw.githubusercontent.com/himekifee/ServerChan/main/common/src/main/resources/assets/serverchan/icon.png" alt="ServerChan Modアイコン" width="200" />
</p>

[English](README.md) | [简体中文](README_CN.md) | **[日本語](README_JA.md)**

ServerChanは、MinecraftサーバーにAIアシスタントを導入するModです。チャットを理解し、ゲーム内イベントに反応し、権限に応じてコマンドを実行することもできます。小規模なSMPから大規模な公開サーバーまで、スパムなしで会話を盛り上げます。

## 主な機能

- **AIチャット統合** - OpenAI互換API（OpenAI、Azure、ローカルLLMなど）を使用したインテリジェントな会話
- **スマート応答システム** - オプションの意図チェッカーでAIが応答すべきタイミングを判断し、スパムを防止
- **ゲームイベント認識** - プレイヤーの参加/退出、死亡などのサーバーイベントに反応
- **コマンド実行** - 関数呼び出しによるMinecraftコマンドの実行（権限制御付き）
- **マルチローダー対応** - Fabric、Forge、NeoForge、Spigot/Paperで動作
- **マルチバージョン対応** - Minecraft 1.12 - 1.21に対応
- **高度な設定** - プロンプト、モデル、応答動作などをカスタマイズ可能
- **多言語対応** - 英語、中国語、日本語のi18nサポート内蔵

## ローダー互換性マトリックス

| バージョン | Java | Fabric | Forge | NeoForge | Spigot/Paper |
|-----------|------|--------|-------|----------|--------------|
| 1.12.x    | 8    | —      | ❌    | —        | ✅           |
| 1.13.x    | 8    | —      | ❌    | —        | ✅           |
| 1.14.x    | 8    | ✅     | ❌    | —        | ✅           |
| 1.15.x    | 8    | ✅     | ❌    | —        | ✅           |
| 1.16.x    | 8    | ✅     | ✅    | —        | ✅           |
| 1.17.x    | 16   | ✅     | ✅    | —        | ✅           |
| 1.18.x    | 17   | ✅     | ✅    | —        | ✅           |
| 1.19.x    | 17   | ✅     | ✅    | —        | ✅           |
| 1.20.x    | 21   | ✅     | ✅    | ✅       | ✅           |
| 1.21.x    | 21   | ✅     | ✅    | ✅       | ✅           |

`—` は該当バージョンでローダーが存在しないことを示します（Fabricは1.14から、NeoForgeは1.20から）。

## インストール

1. [Modrinth](https://modrinth.com/mod/serverchan) または [CurseForge](https://www.curseforge.com/minecraft/mc-mods/serverchan) からローダーに対応したjarをダウンロード
2. `mods/`フォルダ（Spigot/Paperの場合は`plugins/`）にjarを配置
3. サーバーを起動して設定ファイルを生成
4. APIキーと必要な設定を追加（下記の設定セクションを参照）
5. サーバーを再起動または`/reload`でチャット開始

## 設定

設定ファイルの場所：
- **Fabric/Forge/NeoForge**: `config/serverchan.yaml`
- **Spigot**: `plugins/ServerChan/config.yml`

### 必須設定

| オプション | 説明 |
|-----------|------|
| `openaiApiKey` | OpenAI APIキー（または互換プロバイダー） |
| `openaiBaseUrl` | APIベースURL（デフォルト: `https://api.openai.com/v1`）**重要: `/v1`パスを含める必要があります** |

### オプション設定

| オプション | デフォルト | 説明 |
|-----------|----------|------|
| `model` | `gpt-5.1` | 応答に使用するモデル |
| `temperature` | `1.0` | 応答のランダム性（0.0 - 2.0） |
| `contextSize` | `20` | コンテキストに保持するメッセージ数 |
| `botColor` | `b` | ボットチャットのMinecraftカラーコード |
| `timeZone` | `UTC` | メッセージタイムスタンプのタイムゾーン |
| `locale` | `ja` | ボットメッセージの言語 |

### 意図チェッカー設定

意図チェッカーは、小型/高速モデルを使用してAIが応答すべきかを判断します。

| オプション | デフォルト | 説明 |
|-----------|----------|------|
| `useIntentionChecker` | `true` | スマート応答フィルタリングを有効化 |
| `intentionCheckerModel` | `qwen3-235b-a22b-2507` | 意図チェック用モデル |
| `responseProbabilityThreshold` | `0.5` | 応答をトリガーする最小確率 |
| `useFastPathIntentionChecker` | `false` | 応答生成を早期開始 |
| `intentionCheckerApiKey` | (空) | 別のAPIキー（空の場合はメインキーを使用） |
| `intentionCheckerBaseUrl` | (空) | 別のベースURL（空の場合はメインURLを使用）**設定する場合は`/v1`パスを含める必要があります** |

### イベント設定

| オプション | デフォルト | 説明 |
|-----------|----------|------|
| `enableGameEvents` | `true` | ゲームイベントに反応 |
| `enableJoinLeaveEvents` | `true` | プレイヤーの参加/退出に反応 |
| `enableDeathEvents` | `true` | プレイヤーの死亡に反応 |

### 権限設定

| オプション | デフォルト | 説明 |
|-----------|----------|------|
| `inheritCmdSourcePermission` | `true` | AIがトリガーしたプレイヤーの権限を継承してコマンドを実行 |

### カスタムプロンプト

サーバーの雰囲気に合わせてシステムプロンプトをカスタマイズできます：
- `intentionCheckingSystemMessage` - AIが応答するタイミングを制御
- `responseGenerationSystemMessage` - AIの性格と動作を制御

### プロンプト例

`example/`フォルダにサンプルプロンプトファイルがあります。例えば、`example/OnlyMyRedstone-system-prompt.txt`にはOnlyMyRedstoneコミュニティサーバーで使用されている完全な応答生成設定が含まれています。これらのファイルを複製して、あなたのサーバーに合わせてアレンジしてください。

## コマンド

すべてのコマンドにはオペレーター権限（レベル4）が必要です。

| コマンド | 説明 |
|---------|------|
| `/serverchan reload` | 設定をリロード |
| `/serverchan reset` | メッセージコンテキスト/メモリをクリア |
| `/serverchan kill` | OpenAIクライアント接続をリセット |
| `/serverchan disable` | ServerChanの応答を一時停止（メッセージは処理されません） |
| `/serverchan enable` | ServerChanの応答を再開 |

## 動作の仕組み

1. **プレイヤーがチャットでメッセージを送信**
2. **意図チェッカー**（有効な場合）が応答が適切かどうかを評価
3. 応答が必要な場合、**メインモデルが返答を生成**
4. AIはオプションで関数呼び出しによる**コマンド実行**が可能
5. 応答が**全プレイヤーにブロードキャスト**される

AIは会話コンテキストを維持し、設定されたコンテキストサイズ内の以前のメッセージを参照できます。

## 動作要件

- Minecraft Server 1.12 - 1.21（[互換性マトリックス](#ローダー互換性マトリックス)を参照）
- Fabric、Forge、NeoForge、またはSpigot/Paperのいずれか（バージョンにより利用可能性が異なります）
- OpenAI APIキー（またはAzure OpenAI、Ollamaなどの互換プロバイダー）

## ソースからのビルド

```bash
# リポジトリをクローン
git clone https://github.com/himekifee/ServerChan.git
cd ServerChan

# 特定のMinecraftバージョン用にビルド
./gradlew build -PmcVer=1.21

# マージjar（全ローダーを1つに）をビルド
./gradlew build mergeJars -PmcVer=1.21
```

ビルドされたjarは`build/libs/`（またはマージjarの場合は`build/forgix/`）にあります。

### 開発テスト

ローカル開発用に、Modをビルドして実際のMinecraftサーバーを起動し、正しくロードされることを確認するテストスクリプトが提供されています：

```bash
# 全プラットフォームでビルドとテスト（Dockerが必要）
./dev-test.sh 1.21

# ビルドのみ、サーバーテストをスキップ
./dev-test.sh --build-only 1.21

# 特定のプラットフォームのみテスト
./dev-test.sh --fabric 1.21
./dev-test.sh --forge 1.21
./dev-test.sh --neoforge 1.21
./dev-test.sh --paper 1.21
```

サーバーテストの実行にはDockerが必要です。

## コントリビュート

コントリビュートは大歓迎です！IssueやPull Requestをお気軽にどうぞ。ServerChanの使い方を聞かせてください。

1. リポジトリをフォーク
2. フィーチャーブランチを作成（`git checkout -b feature/amazing-feature`）
3. 変更をコミット（`git commit -m 'Add amazing feature'`）
4. ブランチにプッシュ（`git push origin feature/amazing-feature`）
5. Pull Requestを開く

## Discord

[Discordサーバー](https://discord.gg/NuzHC7BCDc)に参加して、コミュニティとの交流、ヘルプの取得、ServerChanの設定共有をしましょう！

## ライセンス

このプロジェクトはGNU General Public License v3.0の下でライセンスされています。詳細は[LICENSE](LICENSE)ファイルを参照してください。

## 謝辞

- [Universal Mod Template](https://github.com/thebuildcraft/Universal-Mod-Template) by thebuildcraft
- YAML設定に[ConfigLib](https://github.com/tomwmth/ConfigLib)を使用
- [Architectury Loom](https://github.com/architectury/architectury-loom)、[Forgix](https://github.com/PacifistMC/Forgix)、[Manifold](https://github.com/manifold-systems/manifold)を使用

## Cerebras

[Cerebras](https://cerebras.ai/)のスポンサーではありませんが、CIテストと自分のサーバーの意図チェッカーモデルに彼らの推論APIを使用しています。非常に高速です！20メッセージのコンテキストで、意図チェックは約1秒以内に完了し、完全な応答は約2-5秒で返ってきます。これは20秒以上かかることが多い他のLLMプロバイダーと比較すると、基本的に瞬時です。このようなリアルタイムチャットアプリケーションにはCerebrasが最適です。CerebrasからAPIクレジットやその他のサポートに興味がある方は、お気軽に[Issueを開いて](https://github.com/himekifee/ServerChan/issues/new)ください。
