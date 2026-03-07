# one_day_keizai プラグイン デバッグ報告書

**日時:** 2026-02-24  
**対象リポジトリ:** https://github.com/megantebro/one_day_keizai  
**デバッグツール:** Maven (unit test) + mineflayer (接続テスト)

---

## 1. プラグイン概要

Paper 1.20.1 向けの **経済・PvP管理プラグイン**。Vault Economy に依存。

| 機能 | 概要 |
|------|------|
| PvP金銭システム | 死亡時に所持金の33%を奪う |
| 罪人システム | 無実キル3回で罪人化・赤ネームタグ |
| 正当防衛判定 | 先に殴られた側は報復キルでもカウントされない |
| リスポーン保護 | リスポーン後10分間PvP無効・緑ネームタグ |
| 債権・債務システム | `/debt lend/repay/forgive` で金銭貸借・期限切れで罪人化 |
| オークション | 30分ごとにランダムアイテムをオークション |
| ワールド分離 | `economy`（安全）と `world`（危険）の2ワールド |
| ログアウト管理 | ベッド付近15秒待機で安全ログアウト、それ以外はペナルティ |
| 戦闘ログアウト | 戦闘中ログアウトで所持金33%を攻撃者に渡す |
| スコアボード | 所持金トップ5をサイドバーに表示 |

---

## 2. ビルド結果

```
mvn clean package
BUILD SUCCESS
```

- 使用: Maven 3.9.9 / Java 21
- 生成物: `target/one_day_keizai-1.0-SNAPSHOT.jar`
- 依存: paper-api 1.20.1、VaultAPI 1.7.1（shade含む）

---

## 3. ユニットテスト結果

Mockito + JUnit Jupiter による単体テスト。全 **136ケース PASS**。

| テストクラス | ケース数 | 結果 | 所要時間 |
|---|---|---|---|
| AuctionManagerTest | 19 | ✅ PASS | 2.331s |
| LogoutManagerTest | 9 | ✅ PASS | 0.378s |
| DebtManagerTest | 24 | ✅ PASS | 0.064s |
| ProtectionManagerTest | 5 | ✅ PASS | 0.013s |
| WorldManagerTest | 9 | ✅ PASS | 0.035s |
| CombatManagerTest | 15 | ✅ PASS | 0.018s |
| CriminalManagerTest | 9 | ✅ PASS | 0.019s |
| DebtCommandTest | 31 | ✅ PASS | 0.181s |
| PvPListenerTest | 15 | ✅ PASS | 0.206s |
| **合計** | **136** | **✅ 全PASS** | **3.245s** |

---

## 4. mineflayer 接続テスト

`mineflayer-debug/debug.js` を実装・実行。

```
接続先: localhost:25565 / ユーザー: DebugBot
[INFO] サーバー接続確認 → kicked (オフラインモード認証拒否)
```

**判定:** mineflayer 自体は正常動作。サーバーが `online-mode=true` で稼働している場合は有効なアカウントが必要。`online-mode=false` のテストサーバーなら接続・コマンドテストまで可能。

実装済みシナリオ（サーバー起動時に実行可能）:
- `/bal` → 「所持金」応答確認
- `/debt list` → 「債権」応答確認
- `/auction 100` → 「オークション」応答確認
- `/ow` → 「オーバーワールド」応答確認

実行方法:
```bash
cd mineflayer-debug
HOST=<サーバーIP> PORT=25565 node debug.js
```

---

## 5. コードレビュー・指摘事項

### 🔴 バグ・論理的問題

#### 5-1. `PvPListener.java` — 無実キル表示上限のハードコード

```java
// PvPListener.java L106
int limit = 3; // config value ← コメントのみ、実際にはconfigから取得していない
killer.sendMessage(ChatColor.YELLOW + "無実キル: " + count + "/" + limit);
```

`CriminalManager` は `innocentKillLimit` を正しく持っているが、PvPListener はそれを参照せずリテラル `3` を表示している。実際の罪人化ロジックは正しく動くが、**config で `innocent-kill-limit` を変更しても表示だけ `3` のまま**になる。

**修正案:**
```java
// PvPListener に innocentKillLimit を渡すか、CriminalManager にgetterを追加する
killer.sendMessage(ChatColor.YELLOW + "無実キル: " + count + "/" + criminalManager.getInnocentKillLimit());
```

---

#### 5-2. `DebtCommand.java` — 貸付リクエストの自動期限切れ処理なし

`pendingRequests` に追加されたリクエストは `accept` or `deny` しない限り **永久に残る**。  
60秒チェックは `handleAccept` 内のみで行われており、放置されたリクエストは Map に蓄積し続ける。

**再現手順:**
1. `/debt lend PlayerB 1000 30` を実行
2. PlayerB が60秒以上無視
3. 新たに別のPlayerが同じBorrowerへリクエストを送ろうとすると「保留中のリクエストがある」と弾かれる

**修正案:**
```java
// handleLend の最初でpending内の古いリクエストをクリーンアップ
pendingRequests.entrySet().removeIf(e -> 
    System.currentTimeMillis() - e.getValue().createdAt > 60_000);
```

---

### 🟡 警告・品質問題

#### 5-3. `LogoutManager.java` — デバッグログが残存

```java
// LogoutManager.java startBedProximityTracker()
Bukkit.getLogger().info("start");
```

本番環境に残るべきでないデバッグ出力。サーバー起動ログを汚染する。

**修正案:** 削除するかより具体的なメッセージに変更。

---

#### 5-4. `BalanceScoreboardManager.java` — int キャストによるオーバーフロー

```java
.map(p -> Map.entry(p.getName(), (int) economy.getBalance(p)))
```

所持金が `Integer.MAX_VALUE`（約21億）を超えるとスコアボードの値が壊れる。  
Minecraftのスコアボードの仕様（整数のみ）という制約はあるが、大きな経済サーバーでは問題になりうる。

**修正案:** 表示用に単位を変える（例: 万円単位に丸める）か、上限チェックを入れる。

---

#### 5-5. `CombatManager.java` — スレッドセーフ性

内部で `HashMap` を使用しているが、非同期タスクやマルチスレッド環境から参照される可能性がある。  
`LogoutManager` は `ConcurrentHashMap` を使っており、一貫性がない。

**修正案:** `HashMap` を `ConcurrentHashMap` に変更するか、アクセスを同期化する。

---

#### 5-6. `PlayerListener.java` — プラグイン名のハードコード

```java
player.getServer().getPluginManager().getPlugin("one_day_keizai")
```

プラグイン名をリネームすると `null` になりNullPointerExceptionが発生する。

**修正案:**
```java
// plugin インスタンスをフィールドとして持ち直接参照する
```

---

#### 5-7. `AuctionManager.java` — deprecated APIの可能性

```java
meta.addStoredEnchant(Enchantment.LOOT_BONUS_BLOCKS, 2, true);
```

Paper 1.20.1 環境では `Enchantment.LOOT_BONUS_BLOCKS` は deprecated で、`Enchantment.FORTUNE` への移行が推奨されている。ビルドは通るが将来的な互換性に懸念あり。

---

### 🟢 良い実装

- `CombatManager` の正当防衛判定ロジックが丁寧に実装されており、先制攻撃の記録が適切に管理されている
- オークションの入札上限を「開始時の所持金」とすることで、途中で金を使っての不正入札を防止している
- `DebtCommand` の `/debt lend` が即時実行ではなく accept/deny フローを取り入れており、誤操作を防いでいる
- ログアウトペナルティの猶予期間設計（グレースフルな再ログイン対応）
- ユニットテストの網羅性が高く、エッジケース（0入札、期限切れ、オフライン落札者等）もカバーされている

---

## 6. 修正優先度まとめ

| 優先度 | 項目 | 場所 |
|--------|------|------|
| 🔴 高 | 無実キル表示の上限ハードコード | `PvPListener.java` |
| 🔴 高 | 貸付リクエストの自動期限切れなし | `DebtCommand.java` |
| 🟡 中 | デバッグログ残存 | `LogoutManager.java` |
| 🟡 中 | int キャストオーバーフロー | `BalanceScoreboardManager.java` |
| 🟡 中 | スレッドセーフ性の不一致 | `CombatManager.java` |
| 🟡 中 | プラグイン名ハードコード | `PlayerListener.java` |
| 🟢 低 | deprecated Enchantment API | `AuctionManager.java` |

---

## 7. 総評

ビルドおよびユニットテスト（136ケース）は**全て正常**。  
設計は丁寧で経済・PvPの複雑なインタラクションがよく整理されている。  
本番投入前に、上記 🔴 の2点（無実キル表示バグ・リクエスト期限切れ未処理）は修正を推奨。  
🟡 の中優先度もゲームバランスや安定性に影響するため、早めの対処が望ましい。
