/**
 * one_day_keizai mineflayer デバッグスクリプト
 * online-mode=false のローカルサーバーに接続してコマンド動作を確認する
 */

const mineflayer = require('mineflayer');

const HOST = process.env.HOST || 'localhost';
const PORT = parseInt(process.env.PORT || '25565');

const results = { pass: 0, fail: 0, log: [] };

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function log(type, msg) {
  const icon = type === 'PASS' ? '✅' : type === 'FAIL' ? '❌' : 'ℹ️';
  console.log(`${icon} [${type}] ${msg}`);
  results.log.push({ type, msg });
  if (type === 'PASS') results.pass++;
  if (type === 'FAIL') results.fail++;
}

function waitForMessage(bot, keyword, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`Timeout: "${keyword}"`)), timeoutMs);
    const handler = (msg) => {
      if (msg.includes(keyword)) {
        clearTimeout(timer);
        bot.removeListener('messagestr', handler);
        resolve(msg);
      }
    };
    bot.on('messagestr', handler);
  });
}

async function runTests(bot) {
  await sleep(2000);

  // --- Test 1: 初期スポーンが安全ワールド(economy)か確認 ---
  try {
    const world = bot.game.dimension;
    const pos = bot.entity.position;
    log('INFO', `スポーン地点: ${world} / x=${pos.x.toFixed(1)} y=${pos.y.toFixed(1)} z=${pos.z.toFixed(1)}`);
    // economyワールドにいるかはサーバー側コマンドで確認
    bot.chat('/bal');
    const balMsg = await waitForMessage(bot, '所持金');
    log('PASS', `/bal 応答OK: ${balMsg.trim()}`);
  } catch (e) {
    log('FAIL', `/bal タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 2: /job list ---
  try {
    bot.chat('/job list');
    const jobMsg = await waitForMessage(bot, '農家');
    log('PASS', `/job list 応答OK: ${jobMsg.trim()}`);
  } catch (e) {
    log('FAIL', `/job list タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 3: /job select farmer ---
  try {
    bot.chat('/job select farmer');
    const msg = await waitForMessage(bot, '農家');
    log('PASS', `/job select farmer OK: ${msg.trim()}`);
  } catch (e) {
    log('FAIL', `/job select farmer タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 4: /job info で農家になっているか確認 ---
  try {
    bot.chat('/job info');
    const msg = await waitForMessage(bot, '農家');
    log('PASS', `/job info 農家確認OK: ${msg.trim()}`);
  } catch (e) {
    log('FAIL', `/job info タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 5: /job select blacksmith ---
  try {
    bot.chat('/job select blacksmith');
    const msg = await waitForMessage(bot, '鍛冶屋');
    log('PASS', `/job select blacksmith OK: ${msg.trim()}`);
  } catch (e) {
    log('FAIL', `/job select blacksmith タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 6: /ow enter (所持金不足で失敗するはず) ---
  try {
    bot.chat('/ow enter');
    const msg = await waitForMessage(bot, '入場料');
    log('PASS', `/ow enter 入場料チェックOK: ${msg.trim()}`);
  } catch (e) {
    log('FAIL', `/ow enter タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 7: /auction list ---
  try {
    bot.chat('/auction list');
    const msg = await waitForMessage(bot, 'オークション');
    log('PASS', `/auction list 応答OK: ${msg.trim()}`);
  } catch (e) {
    log('FAIL', `/auction list タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 8: /debt list ---
  try {
    bot.chat('/debt list');
    const msg = await waitForMessage(bot, '債権');
    log('PASS', `/debt list 応答OK: ${msg.trim()}`);
  } catch (e) {
    log('FAIL', `/debt list タイムアウト: ${e.message}`);
  }

  await sleep(500);

  // --- Test 9: /ow return (オーバーワールドにいないのでエラーになるはず) ---
  try {
    bot.chat('/ow return');
    const msg = await waitForMessage(bot, 'オーバーワールド');
    log('PASS', `/ow return エラー応答OK: ${msg.trim()}`);
  } catch (e) {
    log('FAIL', `/ow return タイムアウト: ${e.message}`);
  }

  // --- 結果サマリー ---
  console.log('\n' + '='.repeat(50));
  console.log(`テスト結果: PASS ${results.pass} / FAIL ${results.fail} / 合計 ${results.pass + results.fail}`);
  console.log('='.repeat(50));

  bot.quit();
}

const bot = mineflayer.createBot({
  host: HOST,
  port: PORT,
  username: 'DebugBot',
  version: '1.20.1',
  auth: 'offline',
});

bot.on('login', () => {
  console.log(`[INFO] 接続成功: ${HOST}:${PORT}`);
  runTests(bot).catch(e => {
    console.error('[ERROR]', e);
    bot.quit();
  });
});

bot.on('messagestr', (msg) => {
  if (msg.trim()) console.log(`  [CHAT] ${msg.trim()}`);
});

bot.on('error', err => console.error('[ERROR]', err.message));
bot.on('kicked', reason => console.log('[KICKED]', reason));
bot.on('end', () => { console.log('[INFO] 切断'); process.exit(0); });
