/**
 * one_day_keizai プラグイン mineflayer デバッグスクリプト
 *
 * 使い方:
 *   node debug.js --host <サーバーIP> --port <ポート> --user <ユーザー名>
 *
 * 前提: Paper 1.20.1 + one_day_keizai プラグイン + Vault + Economy実装 が起動中であること
 */

const mineflayer = require('mineflayer');

const HOST = process.env.HOST || 'localhost';
const PORT = parseInt(process.env.PORT || '25565');
const USER = process.env.USER || 'DebugBot';

// テストシナリオ一覧
const scenarios = [
  scenarioBal,
  scenarioDebtLend,
  scenarioAuction,
  scenarioCombatLogout,
];

// -------- ユーティリティ --------

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function waitForMessage(bot, keyword, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`Timeout waiting for: ${keyword}`)), timeoutMs);
    bot.on('messagestr', (msg) => {
      if (msg.includes(keyword)) {
        clearTimeout(timer);
        resolve(msg);
      }
    });
  });
}

// -------- シナリオ --------

async function scenarioBal(bot) {
  console.log('[TEST] /bal コマンド');
  bot.chat('/bal');
  const msg = await waitForMessage(bot, '所持金');
  console.log('[PASS] /bal 応答:', msg);
}

async function scenarioDebtLend(bot) {
  console.log('[TEST] /debt list (債権なし)');
  bot.chat('/debt list');
  const msg = await waitForMessage(bot, '債権');
  console.log('[PASS] /debt list 応答:', msg);
}

async function scenarioAuction(bot) {
  console.log('[TEST] /auction (オークションなし)');
  bot.chat('/auction 100');
  const msg = await waitForMessage(bot, 'オークション');
  console.log('[PASS] /auction 応答:', msg);
}

async function scenarioCombatLogout(bot) {
  console.log('[TEST] /ow (使い方確認)');
  bot.chat('/ow');
  const msg = await waitForMessage(bot, 'オーバーワールド');
  console.log('[PASS] /ow 応答:', msg);
}

// -------- メイン --------

async function main() {
  console.log(`\n=== one_day_keizai mineflayer デバッグ開始 ===`);
  console.log(`接続先: ${HOST}:${PORT} / ユーザー: ${USER}\n`);

  const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username: USER,
    version: '1.20.1',
    auth: 'offline',
  });

  bot.on('login', async () => {
    console.log('[INFO] サーバー接続成功');
    await sleep(2000); // プラグイン初期化待ち

    let passed = 0;
    let failed = 0;

    for (const scenario of scenarios) {
      try {
        await scenario(bot);
        passed++;
      } catch (err) {
        console.error(`[FAIL] ${scenario.name}: ${err.message}`);
        failed++;
      }
      await sleep(1000);
    }

    console.log(`\n=== 結果: PASS ${passed} / FAIL ${failed} / 合計 ${passed + failed} ===`);
    bot.quit();
  });

  bot.on('error', (err) => {
    console.error('[ERROR]', err.message);
    // 接続エラー: サーバー未起動の場合は「connect ECONNREFUSED」が出る
  });

  bot.on('kicked', (reason) => {
    console.log('[INFO] キックされました:', reason);
  });

  bot.on('end', () => {
    console.log('[INFO] 切断しました');
    process.exit(0);
  });
}

main().catch(console.error);
