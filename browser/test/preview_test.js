/*
 * End-to-end test of the browser preview in headless Chromium. Drives one task (or all) through the
 * real page with a scripted reviewer, asserts trial counts and privacy guarantees, saves screenshots.
 * Usage: node browser/test/preview_test.js <T2|T5|T6|T9|T10|all|guided> [width height] [shotDir]
 * 'guided' runs the whole guided demo, as a professor would; the others start each task from the landing page.
 * Chrome: CHROME_PATH env var (e.g. /usr/bin/google-chrome in CI), else @sparticuz/chromium if installed.
 */
const path = require('path'), fs = require('fs');
const puppeteer = require('puppeteer-core');
const FILE = 'file://' + path.resolve(__dirname, '..', 'dist', 'cognisense-preview.html');
const [code = 'all', W = '1280', H = '800', SHOTS = '/tmp/cognisense-shots'] = process.argv.slice(2);
const CFG = JSON.parse(fs.readFileSync(path.resolve(__dirname, '..', '..', 'config', 'tasks.json'), 'utf8'));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let failures = 0;
const check = (name, ok, detail = '') => { console.log((ok ? 'ok   ' : 'FAIL ') + name + (ok ? '' : ' — ' + detail)); if (!ok) failures++; };

// Runs inside the page: a scripted reviewer that reacts to what the task shows, using real input events.
function installDriver() {
  const H = window.__cognisensePreview;
  let prev = {}, seen = [], stimStart = 0, stimDur = 0;
  const key = (type, code) => window.dispatchEvent(new KeyboardEvent(type, { code, key: code === 'Space' ? ' ' : code, bubbles: true }));
  const press = (code, holdMs, after) => setTimeout(() => { key('keydown', code); setTimeout(() => key('keyup', code), holdMs); }, after);
  const tap = (p, after, id) => setTimeout(() => {
    const c = document.getElementById('stage'); if (!c || !p) return;
    c.dispatchEvent(new PointerEvent('pointerdown', { clientX: p.x, clientY: p.y, pointerId: id, bubbles: true }));
    setTimeout(() => c.dispatchEvent(new PointerEvent('pointerup', { clientX: p.x, clientY: p.y, pointerId: id, bubbles: true })), 80);
  }, after);
  function step() {
    const c = H.content || {};
    if (H.screen === 'task') {
      if (c.awaitingContinue && !prev.awaitingContinue) press('Space', 50, 300);
      if (c.stimulus && !prev.stimulus) {
        stimStart = performance.now();
        if (c.stimulus.endsWith('go_circle')) press('Space', 80, 380);
        else if (c.stimulus.startsWith('flanker')) press(c.stimulus.endsWith('left') ? 'KeyF' : 'KeyJ', 80, 450);
      }
      if (!c.stimulus && prev.stimulus) stimDur = performance.now() - stimStart;
      if (c.highlight !== null && c.highlight !== undefined && c.highlight !== prev.highlight) seen.push(c.highlight);
      if (c.board && c.responseCue && !prev.responseCue) {
        let order = H.part === 'SPB' ? seen.slice().reverse() : seen.slice();
        if (order.length > 2) order = order.slice(1).concat(order[0]); // recall length 2 correctly, fail from 3 (keeps the test short)
        order.forEach((i, k) => tap(H.squarePoint(i), 500 + 400 * k, 11 + k));
        seen = [];
      }
      if (!c.board && c.responseCue && !prev.responseCue) press('Space', 300 + 0.9 * stimDur, 300);
      if (c.choice && c.choice.stage === 'CHOOSE' && (!prev.choice || prev.choice.stage !== 'CHOOSE')) {
        const L = c.choice.left, R = c.choice.right;
        const side = !L.active ? 'R' : !R.active ? 'L' : (L.longDelay ? 'R' : 'L'); // forced: the active one; free: smaller-sooner (short test)
        press(side === 'L' ? 'KeyF' : 'KeyJ', 80, 700);
      }
    }
    prev = c;
    requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}

async function drive(page, label, shots, shotWhen) {
  // Clicks through instructions, the scored-block start and between-task screens until the results page.
  let shot = {}, insShot = {}, sizeChecked = false, keysChecked = false, seenBetween = false; const t0 = Date.now();
  while (Date.now() - t0 < 900000) {
    const s = await page.evaluate(() => ({ screen: __cognisensePreview.screen, content: __cognisensePreview.content, part: __cognisensePreview.part }));
    if (s.screen === 'summary') break;
    if (s.screen === 'task' && !sizeChecked) {
      sizeChecked = true; // regression: a canvas is not stretched by insets alone and fell back to 300 x 150
      const g = await page.evaluate(() => { const r = document.getElementById('stage').getBoundingClientRect(); return { w: r.width, h: r.height, vw: innerWidth, vh: innerHeight }; });
      check(label + ' task stage fills the viewport', g.w >= g.vw - 1 && g.h >= g.vh - 1, JSON.stringify(g));
    }
    if (s.screen === 'instructions' && !keysChecked) {
      keysChecked = true;
      check(label + ' instructions state the keyboard mapping', (await page.evaluate(() => document.querySelector('.keys').textContent)).length > 20);
    }
    if (s.screen === 'between' && !seenBetween) {
      seenBetween = true;
      if (shots) { await sleep(400); await page.screenshot({ path: `${SHOTS}/${label}-between.png` }); }
      check(label + ' between-task screen shows progress', await page.evaluate(() => document.querySelectorAll('.steps li.done').length >= 1));
    }
    if (['instructions', 'real_start', 'between'].includes(s.screen)) {
      const id = await page.evaluate(() => document.querySelector('h1').textContent);
      if (shots && s.screen === 'instructions' && !insShot[id]) { await sleep(900); await page.screenshot({ path: `${SHOTS}/${label}-instructions-${Object.keys(insShot).length + 1}.png` }); insShot[id] = 1; }
      await page.evaluate(() => document.querySelector('[data-act="primary"]').click());
    }
    if (shots && s.screen === 'task' && s.content && s.part && !shot[s.part] && shotWhen[s.part] && shotWhen[s.part](s.content)) {
      await page.screenshot({ path: `${SHOTS}/${label}-task-${s.part}.png` }); shot[s.part] = 1;
    }
    await sleep(40);
  }
  if (shots) { await sleep(300); await page.screenshot({ path: `${SHOTS}/${label}-results.png`, fullPage: true }); }
  return page.evaluate(() => __cognisensePreview.screen);
}

const SHOT_WHEN = { GNG: (c) => c.stimulus === 'gng_go_circle', FLK: (c) => c.stimulus && c.stimulus.includes('incongruent'),
  SPF: (c) => c.highlight !== null && c.highlight !== undefined, SPB: (c) => c.responseCue, TRP: (c) => c.responseCue, CDT: (c) => c.choice && c.choice.stage === 'CHOOSE' };
const EXPECTED = { GNG: 24, FLK: 16, TRP: 4, CDT: 4 };

async function verifyDownload(page, dl, meta, parts, label) {
  await page.evaluate(() => document.querySelector('[data-act="download"]').click());
  await sleep(800);
  const files = fs.readdirSync(dl).filter((f) => f.endsWith('.json')).sort((a, b) => fs.statSync(path.join(dl, a)).mtimeMs - fs.statSync(path.join(dl, b)).mtimeMs);
  const doc = JSON.parse(fs.readFileSync(path.join(dl, files[files.length - 1]), 'utf8'));
  check(label + ' download is marked browser_preview and carries the config hash', doc.browser_preview === true && doc.config_sha256 === meta.sha);
  for (const partId of parts) {
    const scored = doc.runs.filter((r) => r.part === partId && r.phase === 'SCORED').pop();
    const n = scored ? scored.records.length : -1;
    if (EXPECTED[partId]) check(`${partId} review block has ${EXPECTED[partId]} trials`, n === EXPECTED[partId], String(n));
    else check(`${partId} adaptive block stopped after two failures at length 3`, n === 4, String(n));
    check(`${partId} version is the review variant`, scored && /-review$/.test(scored.version), scored && scored.version);
    check(`${partId} metrics present`, scored && scored.metrics.length > 3);
  }
  const charts = await page.evaluate(() => document.querySelectorAll('svg.chart').length);
  check(label + ' results page draws one chart per task part', charts === parts.length, String(charts));
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  let exe = process.env.CHROME_PATH, args = ['--no-sandbox'];
  if (!exe) { const ch = require('@sparticuz/chromium'); exe = await ch.executablePath(); args = ch.args.filter((a) => !a.startsWith('--font')); }
  const browser = await puppeteer.launch({ executablePath: exe, args, headless: true,
    env: { ...process.env, FONTCONFIG_PATH: '/etc/fonts', FONTCONFIG_FILE: '/etc/fonts/fonts.conf' } });
  const page = await browser.newPage();
  await page.setViewport({ width: +W, height: +H });
  const requests = []; const errors = [];
  page.on('request', (r) => requests.push(r.url()));
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  const dl = path.join(SHOTS, 'downloads'); fs.mkdirSync(dl, { recursive: true });
  const cdp = await page.target().createCDPSession();
  await cdp.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: dl });

  await page.goto(FILE, { waitUntil: 'load' });
  const atLoad = requests.length;
  await page.evaluate(installDriver);
  const meta = await page.evaluate(() => ({
    robots: document.querySelector('meta[name=robots]')?.content, csp: document.querySelector('meta[http-equiv=Content-Security-Policy]')?.content,
    cookie: document.cookie, sha: __cognisensePreview.configSha256 }));
  check('robots noindex', /noindex/.test(meta.robots));
  check('CSP forbids network connections', /connect-src 'none'/.test(meta.csp) && /default-src 'none'/.test(meta.csp));
  check('no cookies', meta.cookie === '');
  check('embedded config hash matches config/tasks.json', meta.sha === require('crypto').createHash('sha256').update(fs.readFileSync(path.resolve(__dirname, '..', '..', 'config', 'tasks.json'))).digest('hex'));
  check('disclaimer verbatim on landing', (await page.evaluate(() => document.body.textContent)).includes(
    JSON.parse(fs.readFileSync(path.resolve(__dirname, '..', '..', 'config', 'strings.json'), 'utf8')).strings.disclaimer.en));
  await page.screenshot({ path: `${SHOTS}/landing.png`, fullPage: true });

  if (code === 'guided' || code.startsWith('guided:')) {
    // 'guided' runs the whole demo from its button; 'guided:T5,T9' runs the same flow over a subset (shorter, for CI time limits).
    const subset = code.startsWith('guided:') ? code.slice(7).split(',') : CFG.core_battery_order;
    if (code === 'guided') await page.evaluate(() => document.querySelector('[data-act="guided"]').click());
    else await page.evaluate((c) => __cognisensePreview.startGuided(c), subset);
    const end = await drive(page, 'guided', true, SHOT_WHEN);
    check('guided demo reached the results page', end === 'summary', end);
    const progress = await page.evaluate(() => document.querySelectorAll('.steps').length);
    check('results page has no progress bar left over', progress === 0, String(progress));
    await verifyDownload(page, dl, meta, subset.flatMap((c) => CFG.pool.find((p) => p.code === c).parts), 'guided');
  } else {
    const todo = CFG.core_battery_order.filter((c) => code === 'all' || c === code).map((c) => CFG.pool.find((p) => p.code === c));
    for (const info of todo) {
      await page.evaluate((c) => document.querySelector(`[data-task="${c}"]`).click(), info.code);
      const end = await drive(page, info.code, true, SHOT_WHEN);
      check(info.code + ' reached the results page', end === 'summary', end);
      await verifyDownload(page, dl, meta, info.parts, info.code);
      await page.evaluate(() => document.querySelector('[data-act="home"]').click());
      await sleep(200);
    }
  }
  check('no network requests after page load', requests.length === atLoad, requests.slice(atLoad).join(', '));
  check('no page errors', errors.length === 0, errors.join(' | '));
  await browser.close();
  console.log(failures ? `FAILED: ${failures}` : 'all browser checks passed');
  process.exit(failures ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
