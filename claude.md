# claude.md — 開発中に起きた問題・注意事項まとめ

このファイルは開発中に遭遇したバグ・ハマりどころ・環境起因の問題をまとめたものです。
次のセッションで同じ問題を踏まないための参照ドキュメント。

---

## 🔴 環境・インフラ系

### Maven が再起動で消える
- **場所**: `/tmp/apache-maven-3.9.9/bin/mvn`
- **原因**: `/tmp` はサーバー再起動でクリアされる
- **対処**: 再起動後は毎回再ダウンロードが必要
  ```bash
  cd /tmp && curl -sO https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz && tar -xzf apache-maven-3.9.9-bin.tar.gz
  ```

### tmux セッションが再起動で消える
- **対処**: 再起動後に再作成
  ```bash
  tmux new-session -d -s onedaykeizai
  tmux send-keys -t onedaykeizai "cd /home/megante/onedaykeizai && bash start.sh" Enter
  ```

### Mineflayer ボットが接続できない
- **原因**: `online-mode=true` のため Mojang 認証が必要
- **回避策なし**: ボットによる自動テストは不可

### grep の exit code でパイプが止まる
- **問題**: `mvn package -q 2>&1 | grep -E "ERROR|FAILURE" && cp ...` の形で書くと、grep がマッチなし(exit 1)を返してパイプが止まる
- **対処**: `; echo "BUILD:$?"` を使うか `grep ... || true` にする

---

## 🔴 QuickShop 系

### バックアップ復元後にショップが壊れる（null inventory）
- **症状**: ログに `Failed to load shop: Inventory is null` が大量出力
- **原因**: ワールドを復元してもプラグインDBは上書きされない → チェストが存在しない座標のショップデータが残る
- **対処**: サーバー停止 → `shops.mv.db` をバックアップから復元（または削除）→ 再起動
  ```bash
  tar -xzf /home/megante/backups/onedaykeizai_YYYYMMDD_HHMM_clean.tar.gz \
    -C /tmp onedaykeizai/plugins/QuickShop-Hikari/shops.mv.db
  cp /tmp/onedaykeizai/plugins/QuickShop-Hikari/shops.mv.db \
    /home/megante/onedaykeizai/plugins/QuickShop-Hikari/shops.mv.db
  ```

### `debug.delete-corrupt-shops: true` が効かない
- **症状**: 設定しても壊れたショップが自動削除されない
- **原因**: `qs reload` ではショップを再ロードしないケースがある
- **対処**: DB を直接バックアップから復元する方が確実

### PlugMan でリロードすると設定が上書きされる
- **問題**: プラグインがロードされている間に config.yml / shops.mv.db を編集しても、アンロード時に古い状態で上書きされる
- **対処**: **必ずプラグインをアンロードしてからファイルを編集する**
  ```
  plugman unload one_day_keizai
  # ← ここで設定ファイルを編集
  plugman load one_day_keizai
  ```

---

## 🔴 Paper 1.20.1 API の落とし穴

### `PotionEffectType.RESISTANCE` が存在しない
- **正しい名前**: `PotionEffectType.DAMAGE_RESISTANCE`
- 間違えるとコンパイルエラーまたは実行時 NPE

### `VillagerCareerChangeEvent.ChangeReason.LEVEL_UP` が存在しない
- **存在する値**: `EMPLOYED` と `LOSING_JOB` のみ
- `LEVEL_UP` はない。村人レベルアップは別のイベントで処理する

### TradeSystem v2.6.3 が Paper 1.20.1 と非互換
- アンロード済み。再インストール不可

---

## 🔴 プラグイン実装バグ

### WorldListener にコンストラクタでワールド名を注入し忘れ
- `WorldListener(String pvpWorld, String economyWorld)` でワールド名を受け取る設計
- ハードコードせず、必ずコンストラクタから注入すること

### JobFarmListener で `event.getBlock().getWorld()` を使っていた
- **正しい**: `event.getBlockPlaced().getWorld()`
- `getBlock()` はプレイヤー位置のブロックを返す場合があり、ワールド判定がずれる

### JobCraftListener の判定ロジック
- クラフトビューアーが複数いる場合、「全員が許可職業」ではなく「**1人でも許可職業がいればOK**」が正しい
- ソロプレイでも問題ないが、複数人で同じ作業台を使う場合に重要

### AuctionManager の `withdrawPlayer` レスポンス確認忘れ
- `EconomyResponse.transactionSuccess()` を必ず確認してから処理を進める

### NametagManager: JobManager の後注入が必要
- `new NametagManager()` の後に `nametagManager.setJobManager(jobManager)` を呼ぶこと
- 順番を間違えると NPE

### `defaultItems()` が static だと NamespacedKey を参照できない
- `AirdropManager.buildDefaultItems()` を非 static に変更して `noRepairKey` を参照できるようにした
- static メソッド内で `new NamespacedKey(plugin, ...)` は使えない

---

## 🟡 設計・運用上の注意

### オーバーワールドデポジットはクラッシュ対策で即 save() する
```java
playerDataManager.setOverworldDeposit(uuid, fee);
playerDataManager.save(); // クラッシュ時消失防止
```

### PlugMan hot-reload 後にスケジューラータスクが二重起動することがある
- `plugman unload` → `plugman load` の間に既存タスクが残ることがある
- サーバー再起動のほうが確実なケースあり

### GitHub SSH キー
- `~/.ssh/github_id` を使う
  ```bash
  GIT_SSH_COMMAND="ssh -i ~/.ssh/github_id" git push
  ```

### XConomy の残高表示フォーマット
- `display-format: '%balance%円'` / `integer-bal: true`
- Vault 経由の Economy は `%balance%` 変数を使う

---

## 📁 重要ファイル・パス

| 項目 | パス |
|------|------|
| サーバー | `/home/megante/onedaykeizai/` |
| プラグインソース | `/home/megante/.openclaw/workspace/one_day_keizai/` |
| ビルド jar | `target/one_day_keizai-1.0-SNAPSHOT.jar` |
| デプロイ先 | `/home/megante/onedaykeizai/plugins/` |
| XConomy DB | `plugins/XConomy/playerdata/data.db` (SQLite) |
| QuickShop DB | `plugins/QuickShop-Hikari/shops.mv.db` (H2) |
| バックアップ | `/home/megante/backups/` |
| Maven (再起動後消える) | `/tmp/apache-maven-3.9.9/bin/mvn` |

---

## 🟢 正常に動作している構成（現在）

- Paper 1.20.1
- Vault + XConomy（残高管理）
- QuickShop-Hikari（プレイヤーショップ）
- Multiverse-Core（`economy` / `world` 2ワールド構成）
- LuckPerms
- WorldEdit / WorldGuard
- PlugManX（hot-reload）
- online-mode=true / whitelist=ON
