'use strict';
/*
 * Cognisense web demo: a demonstration and expert-review tool, not the measurement instrument.
 * Task logic, sequences and scoring come from the shared Kotlin core compiled to JavaScript
 * (window['cognisense-core']); every parameter and every string comes from config/ (embedded at build).
 * No network requests, no cookies, no storage: the page's Content-Security-Policy forbids them.
 */
(() => {
  const TASKS_TEXT = document.getElementById('cfg-tasks').textContent;
  const CFG = JSON.parse(TASKS_TEXT);
  const STR = JSON.parse(document.getElementById('cfg-strings').textContent);
  const core = globalThis['cognisense-core'];
  const api = new core.org.cognisense.browser.PreviewApi(TASKS_TEXT);
  const app = document.getElementById('app');

  let lang = 'en'; // visitors read English; one tap switches the child-facing text to the Urdu drafts
  const session = { started_utc: new Date().toISOString(), seed: randomSeed(), runIndex: 0, runs: [] };
  let screen = 'landing';
  let task = null;

  function randomSeed() {
    const a = new BigUint64Array(1);
    crypto.getRandomValues(a);
    return BigInt.asIntN(64, a[0]).toString();
  }
  const t = (k) => (STR.strings[k] ? STR.strings[k][lang] : '\u27e8' + k + '\u27e9');
  const tl = (k, l) => STR.strings[k][l];

  function el(tag, attrs, ...kids) {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === undefined || v === null || v === false) continue;
      if (k === 'class') e.className = v;
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
      else e.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids) if (kid !== null && kid !== undefined && kid !== false) e.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    return e;
  }
  const local = (tag, key, attrs) => el(tag, Object.assign({ lang, dir: lang === 'ur' ? 'rtl' : 'ltr' }, attrs || {}), t(key));
  const english = (tag, text, attrs) => el(tag, Object.assign({ lang: 'en', dir: 'ltr' }, attrs || {}), text);
  const button = (label, onclick, quiet) => el('button', { type: 'button', class: quiet ? 'quiet' : null, onclick, lang }, label);

  function page(name, ...kids) {
    stopTask();
    stopAmbient();
    document.body.classList.remove('tasking');
    document.documentElement.lang = lang;
    app.replaceChildren(el('main', { class: 'page', dir: lang === 'ur' ? 'rtl' : 'ltr' }, ...kids));
    window.scrollTo(0, 0);
    screen = name;
  }

  // ------------------------------------------------------------------ painter (config-driven; mirrors the Android painter)
  const D = CFG.display, S = CFG.stimuli;
  const rgb = (a) => 'rgb(' + a[0] + ',' + a[1] + ',' + a[2] + ')';
  const stimDef = (id) => S[id] || S[id.split('_')[0]] || null;
  const defByShape = (shape) => Object.values(S).find((s) => s.shape === shape) || null;

  const Paint = {
    missing(g, s, cx, cy) { g.fillStyle = '#f0f'; g.fillRect(cx - 40 * s, cy - 40 * s, 80 * s, 80 * s); },
    fixation(g, s, cx, cy) {
      const f = D.fixation, half = f.size_dp * s / 2, w = f.stroke_dp * s / 2;
      g.fillStyle = rgb(f.rgb); g.fillRect(cx - half, cy - w, 2 * half, 2 * w); g.fillRect(cx - w, cy - half, 2 * w, 2 * half);
    },
    stimulus(g, s, id, cx, cy) {
      const d = stimDef(id); if (!d) return Paint.missing(g, s, cx, cy);
      g.fillStyle = rgb(d.rgb);
      if (d.shape === 'circle') { g.beginPath(); g.arc(cx, cy, d.diameter_dp * s / 2, 0, 2 * Math.PI); g.fill(); }
      else if (d.shape === 'square') { const h = d.side_dp * s / 2; g.fillRect(cx - h, cy - h, 2 * h, 2 * h); }
      else if (d.shape === 'arrow_row') Paint.arrowRow(g, s, d, id, cx, cy);
      else Paint.missing(g, s, cx, cy);
    },
    arrowRow(g, s, d, id, cx, cy) {
      const parts = id.split('_'), targetLeft = parts[2] === 'left';
      const flankLeft = parts[1] === 'congruent' ? targetLeft : !targetLeft;
      const w = d.arrow_width_dp * s, gap = d.gap_dp * s, h = d.arrow_height_dp * s, n = d.count;
      const total = n * w + (n - 1) * gap;
      for (let i = 0; i < n; i++) Paint.arrow(g, cx - total / 2 + i * (w + gap) + w / 2, cy, w, h, d.shaft_height_fraction,
        i === Math.floor(n / 2) ? targetLeft : flankLeft);
    },
    arrow(g, cx, cy, w, h, shaft, left) {
      const k = left ? -1 : 1, sh = h * shaft;
      g.beginPath();
      g.moveTo(cx - k * w / 2, cy - sh / 2); g.lineTo(cx + k * w * 0.02, cy - sh / 2); g.lineTo(cx + k * w * 0.02, cy - h / 2);
      g.lineTo(cx + k * w / 2, cy); g.lineTo(cx + k * w * 0.02, cy + h / 2); g.lineTo(cx + k * w * 0.02, cy + sh / 2);
      g.lineTo(cx - k * w / 2, cy + sh / 2); g.closePath(); g.fill();
    },
    squares(s, boardId, cx, cy) {
      const b = S[boardId], W = b.width_dp * s, H = b.height_dp * s, left = cx - W / 2, top = cy - H / 2;
      return { b, W, H, left, top, half: b.square_dp * s / 2, pts: b.positions.map(([x, y]) => [left + x * W, top + y * H]) };
    },
    board(g, s, boardId, cx, cy, hl, echo, cue) {
      const q = Paint.squares(s, boardId, cx, cy);
      q.pts.forEach(([x, y], i) => {
        g.fillStyle = rgb(i === hl ? q.b.lit_rgb : i === echo ? q.b.echo_rgb : q.b.idle_rgb);
        g.fillRect(x - q.half, y - q.half, 2 * q.half, 2 * q.half);
      });
      if (cue) {
        const f = q.b.cue_frame_dp * s; g.strokeStyle = rgb(q.b.cue_frame_rgb); g.lineWidth = f;
        g.strokeRect(q.left - f / 2, q.top - f / 2, q.W + f, q.H + f);
      }
    },
    tokens(g, s, d, n, cx, cy, filled, active) {
      const size = d.token_dp * s, gap = d.token_gap_dp * s, total = n * size + (n - 1) * gap;
      const color = rgb(active ? d.token_rgb : d.inactive_rgb);
      for (let i = 0; i < n; i++) {
        g.beginPath(); g.arc(cx - total / 2 + size / 2 + i * (size + gap), cy, size / 2, 0, 2 * Math.PI);
        if (filled) { g.fillStyle = color; g.fill(); } else { g.strokeStyle = color; g.lineWidth = d.token_outline_dp * s; g.stroke(); }
      }
    },
    choice(g, s, ch, lx, rx, cx, cy) {
      const d = defByShape('choice_panels'); if (!d) return Paint.missing(g, s, cx, cy);
      if (ch.stage === 'REWARD') return Paint.tokens(g, s, d, ch.rewardTokens, cx, cy, true, true);
      const opt = (o, x) => {
        if (!o) return;
        Paint.tokens(g, s, d, o.tokens, x, cy - 20 * s, ch.stage === 'CHOOSE', o.active);
        const len = (o.longDelay ? d.long_bar_dp : d.short_bar_dp) * s, h = d.bar_height_dp * s;
        g.fillStyle = rgb(o.active ? d.bar_rgb : d.inactive_rgb); g.fillRect(x - len / 2, cy + 30 * s, len, h);
      };
      opt(ch.left, lx); opt(ch.right, rx);
    },
    durationBars(g, s, target, rep, cx, cy) {
      const d = defByShape('duration_bars'); if (!d) return Paint.missing(g, s, cx, cy);
      const max = d.max_bar_dp * s, h = d.bar_height_dp * s, gap = d.gap_dp * s, k = max / Math.max(target, rep, 1), left = cx - max / 2;
      g.fillStyle = rgb(d.target_rgb); g.fillRect(left, cy - h - gap / 2, target * k, h);
      g.fillStyle = rgb(d.reproduced_rgb); g.fillRect(left, cy + gap / 2, rep * k, h);
    },
    feedback(g, s, ok, cx, cy) {
      const f = D.feedback, r = f.half_size_dp * s;
      g.strokeStyle = rgb(f.rgb); g.lineWidth = f.stroke_dp * s; g.lineCap = 'round'; g.lineJoin = 'round'; g.beginPath();
      if (ok) { g.moveTo(cx - r, cy); g.lineTo(cx - r / 4, cy + r * 0.85); g.lineTo(cx + r * 1.15, cy - r * 0.9); }
      else { g.moveTo(cx - r, cy - r); g.lineTo(cx + r, cy + r); g.moveTo(cx - r, cy + r); g.lineTo(cx + r, cy - r); }
      g.stroke();
    },
    pad(g, s, x, y, r, pressed, cue) {
      const p = D.pads;
      g.beginPath(); g.arc(x, y, r, 0, 2 * Math.PI); g.fillStyle = rgb(pressed ? p.pressed_rgb : p.fill_rgb); g.fill();
      g.lineWidth = (cue ? p.cue_ring_dp : p.ring_dp) * s; g.strokeStyle = rgb(cue ? p.cue_ring_rgb : p.ring_rgb); g.stroke();
    },
  };

  // ------------------------------------------------------------------ the task stage
  class Scene {
    constructor(canvas, pads) { this.c = canvas; this.g = canvas.getContext('2d'); this.pads = pads; this.resize(); }
    resize() {
      const dpr = window.devicePixelRatio || 1;
      const r = this.c.getBoundingClientRect();
      this.w = r.width || window.innerWidth; this.h = r.height || window.innerHeight;
      this.c.width = Math.round(this.w * dpr); this.c.height = Math.round(this.h * dpr);
      this.g.setTransform(dpr, 0, 0, dpr, 0, 0);
      // dp are drawn as CSS px; small screens scale the whole scene uniformly (designed for >= 640 x 360)
      this.s = Math.min(1, this.w / 640, this.h / 360);
    }
    needsRotate() { return this.h > this.w && this.w < 700; }
    padCentres() {
      const p = D.pads, r = p.radius_dp * this.s, y = this.h - p.bottom_margin_dp * this.s - r;
      if (this.pads === 'SINGLE') return [['SINGLE', this.w / 2, y]];
      if (this.pads === 'LEFT_RIGHT') return [['LEFT', p.side_margin_dp * this.s + r, y], ['RIGHT', this.w - p.side_margin_dp * this.s - r, y]];
      return [];
    }
    stimY() {
      const p = D.pads;
      return this.pads === 'BOARD' ? this.h / 2 : (this.h - p.bottom_margin_dp * this.s - 2 * p.radius_dp * this.s) / 2;
    }
    text(msg, y) {
      const g = this.g;
      g.fillStyle = rgb(D.text_rgb); g.textAlign = 'center'; g.textBaseline = 'middle';
      g.font = (D.text_sp * this.s) + 'px ' + (lang === 'ur' ? getComputedStyle(document.body).getPropertyValue('--urdu') || 'serif' : 'system-ui, sans-serif');
      g.fillText(msg, this.w / 2, y);
    }
    draw(c) {
      const g = this.g, s = this.s, cx = this.w / 2, cy = this.stimY();
      g.fillStyle = rgb(D.background_rgb); g.fillRect(0, 0, this.w, this.h);
      if (c.paused) return this.text(t('paused'), cy);
      if (c.awaitingContinue) return this.text(t('tap_continue'), cy);
      if (c.breakRemainingMs !== null) {
        this.text(t('rest'), cy - 30 * s);
        const frac = 1 - Math.min(1, Math.max(0, c.breakRemainingMs / CFG.common.block_break_ms));
        const bw = this.w * 0.5;
        g.fillStyle = 'rgb(105,105,105)'; g.fillRect(cx - bw / 2, cy + 10 * s, bw, 12 * s);
        g.fillStyle = 'rgb(40,40,40)'; g.fillRect(cx - bw / 2, cy + 10 * s, bw * frac, 12 * s);
        return;
      }
      if (c.finished) return;
      const r = D.pads.radius_dp * s;
      if (c.showPads) for (const [, x, y] of this.padCentres()) Paint.pad(g, s, x, y, r, c.padPressed, c.responseCue);
      if (c.board) Paint.board(g, s, c.board, cx, cy, c.highlight, c.echo, c.responseCue);
      if (c.fixation) Paint.fixation(g, s, cx, cy);
      if (c.stimulus) Paint.stimulus(g, s, c.stimulus, cx, cy);
      if (c.choice) {
        const pc = this.padCentres(), L = pc.find((p) => p[0] === 'LEFT'), R = pc.find((p) => p[0] === 'RIGHT');
        Paint.choice(g, s, c.choice, L ? L[1] : this.w * 0.25, R ? R[1] : this.w * 0.75, cx, cy);
      }
      if (c.durationFeedback) Paint.durationBars(g, s, c.durationFeedback[0], c.durationFeedback[1], cx, cy);
      if (c.feedback !== null) Paint.feedback(g, s, c.feedback, cx, cy);
    }
    drawRotate() {
      this.g.fillStyle = rgb(D.background_rgb); this.g.fillRect(0, 0, this.w, this.h);
      this.text(t('preview_rotate'), this.h / 2);
    }
    hit(x, y) {
      const r = D.pads.radius_dp * this.s * D.pads.hit_radius_factor;
      const pad = this.padCentres().find(([, px, py]) => Math.hypot(x - px, y - py) <= r);
      return { key: pad ? pad[0] : '', target: this.boardHit(x, y) };
    }
    boardHit(x, y) {
      if (!this.boardId) return -1;
      const q = Paint.squares(this.s, this.boardId, this.w / 2, this.stimY()), tol = q.half + 8 * this.s;
      const i = q.pts.findIndex(([px, py]) => Math.abs(x - px) <= tol && Math.abs(y - py) <= tol);
      return i;
    }
    squarePoint(i) { const q = Paint.squares(this.s, this.boardId, this.w / 2, this.stimY()); return { x: q.pts[i][0], y: q.pts[i][1] }; }
    padPoint(key) { const p = this.padCentres().find((c) => c[0] === key); return p ? { x: p[1], y: p[2] } : null; }
  }

  const KEYS = { SINGLE: { Space: 'SINGLE' }, LEFT_RIGHT: { KeyF: 'LEFT', ArrowLeft: 'LEFT', KeyJ: 'RIGHT', ArrowRight: 'RIGHT' }, BOARD: {} };
  const KEY_POINTER = { SINGLE: 1001, LEFT: 1002, RIGHT: 1003 };

  function runTask(partId, phase, attempt, done) {
    stopTask();
    stopAmbient();
    screen = 'task';
    document.body.classList.add('tasking');
    const canvas = el('canvas', { id: 'stage', 'aria-label': 'task' });
    app.replaceChildren(canvas);
    try { const fs = document.documentElement.requestFullscreen && document.documentElement.requestFullscreen(); if (fs && fs.catch) fs.catch(() => {}); } catch (e) { /* not available: fine */ }
    const pads = CFG.tasks[partId].pads;
    const scene = new Scene(canvas, pads);
    scene.boardId = pads === 'BOARD' ? CFG.tasks[partId].board : null;
    const probe = [];
    let run = null, content = { fixation: true, showPads: false }, lastJson = '', hz = null;
    const seqBase = (session.runIndex++) * 1000;
    const keymap = KEYS[pads];

    const interrupt = () => { if (run && !run.finished) run.interrupt(performance.now()); };
    const onVisibility = () => { if (document.hidden) interrupt(); };
    const onResize = () => { scene.resize(); scene.draw(content); };
    const control = () => {
      if (content.paused) { run.resume(); return true; }
      if (content.awaitingContinue) { run.continueAfterBreak(); return true; }
      return false;
    };
    const onPointerDown = (e) => {
      e.preventDefault();
      try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* synthetic or ended pointer */ }
      if (!run || control()) return;
      const h = scene.hit(e.offsetX, e.offsetY);
      run.touch(e.timeStamp, h.key, h.target, false, e.pointerId, e.offsetX, e.offsetY);
    };
    const onPointerUp = (e) => { if (run) run.touch(e.timeStamp, '', -1, true, e.pointerId, e.offsetX, e.offsetY); };
    // pointercancel is deliberately not a release (as on Android): a cancelled gesture is not a child's lift
    const onKeyDown = (e) => {
      if (e.code === 'Escape') { finish(true); return; }
      const key = keymap[e.code];
      if (key || e.code === 'Space') e.preventDefault();
      if (e.repeat || !run) return;
      if (control()) return;
      if (!key) return;
      const p = scene.padPoint(key) || { x: NaN, y: NaN };
      run.touch(e.timeStamp, key, -1, false, KEY_POINTER[key], p.x, p.y);
    };
    const onKeyUp = (e) => {
      const key = keymap[e.code];
      if (!key || !run) return;
      run.touch(e.timeStamp, '', -1, true, KEY_POINTER[key], NaN, NaN);
    };

    // Keep the screen from dimming mid-task where supported, and ask before leaving a running task.
    let wake = null;
    try { if (navigator.wakeLock) navigator.wakeLock.request('screen').then((w) => { if (me.stopped) w.release().catch(() => {}); else wake = w; }).catch(() => {}); } catch (e) { /* unsupported: fine */ }
    const onBeforeUnload = (e) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', onBeforeUnload);

    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointerup', onPointerUp);
    canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    window.addEventListener('blur', interrupt);
    window.addEventListener('resize', onResize);
    document.addEventListener('visibilitychange', onVisibility);

    const me = {
      scene, partId, stopped: false,
      get content() { return content; },
      cleanup() {
        this.stopped = true;
        cancelAnimationFrame(this.raf);
        window.removeEventListener('keydown', onKeyDown);
        window.removeEventListener('keyup', onKeyUp);
        window.removeEventListener('blur', interrupt);
        window.removeEventListener('resize', onResize);
        document.removeEventListener('visibilitychange', onVisibility);
        window.removeEventListener('beforeunload', onBeforeUnload);
        if (wake) { wake.release().catch(() => {}); wake = null; }
        if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {});
      },
    };
    task = me;

    function finish(aborted) {
      if (me.stopped) return;
      const result = aborted ? null : {
        part: partId, version: api.reviewVersion(partId), phase, attempt, measured_hz: hz,
        practice_criterion_met: phase === 'PRACTICE' ? run.practiceCriterionMet() : null,
        dropped_frames: run.droppedFrames(), interruptions: run.interruptions(),
        records: JSON.parse(run.recordsJson()), metrics: phase === 'SCORED' ? JSON.parse(run.metricsJson()) : [],
      };
      if (result) session.runs.push(result);
      stopTask();
      done(result);
    }

    function onFrame(ts) {
      if (me.stopped) return;
      if (scene.needsRotate()) { probe.length = 0; scene.drawRotate(); me.raf = requestAnimationFrame(onFrame); return; }
      if (!run) {
        probe.push(ts);
        scene.draw(content);
        if (probe.length > CFG.common.refresh_probe_frames) {
          const d = probe.slice(1).map((x, i) => x - probe[i]).sort((a, b) => a - b);
          hz = 1000 / d[Math.floor(d.length / 2)];
          run = api.startReviewRun(partId, phase, session.seed, Math.min(500, Math.max(20, hz)), attempt, seqBase);
        }
        me.raf = requestAnimationFrame(onFrame);
        return;
      }
      const json = run.frame(ts);
      if (json !== lastJson) { lastJson = json; content = JSON.parse(json); scene.draw(content); }
      if (run.finished) { finish(false); return; }
      me.raf = requestAnimationFrame(onFrame);
    }
    me.raf = requestAnimationFrame(onFrame);
  }

  function stopTask() {
    if (task) { task.cleanup(); task = null; }
  }

  // ------------------------------------------------------------------ visitor-facing copy (English; the demo shell only)
  // Participant-facing text (task names, instructions, keyboard hints) stays in config/strings.json with its Urdu drafts.
  // Links appear only once they are set (when the repository and paper are public).
  const LINKS = { paper: '', code: 'https://github.com/farzaanjamal/cognisense', feedback: 'https://github.com/farzaanjamal/cognisense/issues' };
  const REDUCED = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const GUIDED = CFG.core_battery_order.map((code) => CFG.pool.find((p) => p.code === code));
  const COPY = {
    T2: { name: 'Go/No-Go', measures: 'Holding back a quick, habitual response, and keeping attention steady when events come quickly or slowly.', does: 'Tap for the circle. Do nothing for the square.' },
    T6: { name: 'Spatial span', measures: 'Remembering where things happened and in what order, then reversing that order in your head.', does: 'Watch squares light up one by one, then tap them in the same order. A second round asks for the reverse order.' },
    T5: { name: 'Flanker', measures: 'Ignoring nearby arrows that point the wrong way.', does: 'Press the side that the middle arrow points to.' },
    T9: { name: 'Time reproduction', measures: 'Judging and reproducing durations of one to six seconds.', does: 'Watch how long a circle stays on screen, then hold the button down for the same time.' },
    T10: { name: 'Choice-delay', measures: 'Choosing between a smaller reward soon and a larger reward later.', does: 'Choose one token after a short wait, or two tokens after a long wait.' },
  };
  const PART_TITLE = { GNG: 'Go/No-Go', SPF: 'Spatial span, forwards', SPB: 'Spatial span, backwards', FLK: 'Flanker', TRP: 'Time reproduction', CDT: 'Choice-delay' };
  const PART_LEAD = { SPB: 'The same squares, but now tap them in reverse order: the last one first.' };
  const DEMO_CAPTION = {
    GNG: 'Example trials at their real timing: a circle, a tap just after it disappears, then a square that is left alone.',
    SPF: 'Example: three squares light up, then the same three are tapped in order.',
    SPB: 'Example: the same three squares, tapped in reverse order.',
    FLK: 'Example: the middle arrow points right, so the right button is pressed. Then a left trial.',
    TRP: 'Example: the circle stays for two seconds, then the button is held for about two seconds. No timer is ever shown.',
    CDT: 'Example: two tokens after the long wait is chosen, and the tokens appear.',
  };
  const CLASS_LABEL = { A: 'timing-independent', B: 'robust to a constant device delay', C: 'affected by device delay' };
  const METRIC_LABEL = {
    commission_rate: 'Taps to the square', omission_rate: 'Circles missed', median_go_rt: 'Median reaction time',
    isd_go_rt: 'Reaction-time variability (SD)', event_rate_median_rt_effect: 'Slow minus fast median reaction time',
    interference_rt: 'Interference cost (incongruent minus congruent)', accuracy_congruent: 'Accuracy, congruent trials',
    accuracy_incongruent: 'Accuracy, incongruent trials', median_rt: 'Median reaction time', span: 'Span (longest length recalled)',
    total_correct: 'Sequences correct', product_score: 'Span \u00d7 sequences correct', mean_ratio: 'Mean held \u00f7 shown duration',
    vierordt_slope: 'Slope of held on shown duration', prop_larger_later: 'Larger, later choices', median_choice_latency: 'Median time to choose',
  };
  const KEY_METRICS = {
    GNG: ['commission_rate', 'omission_rate', 'median_go_rt', 'isd_go_rt', 'event_rate_median_rt_effect'],
    FLK: ['interference_rt', 'accuracy_congruent', 'accuracy_incongruent', 'median_rt'],
    SPF: ['span', 'total_correct', 'product_score'], SPB: ['span', 'total_correct', 'product_score'],
    TRP: ['mean_ratio', 'vierordt_slope'], CDT: ['prop_larger_later', 'median_choice_latency'],
  };

  // ------------------------------------------------------------------ ambient example animations (shell only; never during a task)
  let ambient = null;
  function stopAmbient() { if (ambient) { ambient.stop(); ambient = null; } }
  const fieldCanvas = () => el('canvas', { class: 'field', 'aria-hidden': 'true' });
  function prep(cv) {
    const w = cv.clientWidth, h = cv.clientHeight, dpr = window.devicePixelRatio || 1;
    if (cv.width !== Math.round(w * dpr) || cv.height !== Math.round(h * dpr)) { cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); }
    const g = cv.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { g, w, h };
  }
  /** Cycles a list of scenes ({ ms, draw(g, w, h, s) }) on a stage-grey canvas; a reduced-motion viewer sees one still scene. */
  function loop(cv, scenes, still) {
    const total = scenes.reduce((a, s) => a + s.ms, 0);
    let raf = 0, last = -1, alive = true;
    const t0 = performance.now() - scenes.slice(0, still).reduce((a, s) => a + s.ms, 0); // open on the stimulus, not a blank
    const paint = (i) => {
      const { g, w, h } = prep(cv); if (!w || !h) return;
      g.fillStyle = rgb(D.background_rgb); g.fillRect(0, 0, w, h);
      scenes[i].draw(g, w, h, Math.min(w / 640, h / 400));
    };
    const tick = (now) => {
      if (!alive || !cv.isConnected) return;
      let t = (now - t0) % total, i = 0;
      while (t >= scenes[i].ms) { t -= scenes[i].ms; i++; }
      if (i !== last) { last = i; paint(i); }
      raf = requestAnimationFrame(tick);
    };
    const onResize = () => { last = -1; if (REDUCED) paint(still); };
    window.addEventListener('resize', onResize);
    requestAnimationFrame(() => { if (!alive) return; if (REDUCED) paint(still); else raf = requestAnimationFrame(tick); });
    return { stop() { alive = false; cancelAnimationFrame(raf); window.removeEventListener('resize', onResize); } };
  }
  const padR = (s) => D.pads.radius_dp * s;
  const padY = (h, s) => h - D.pads.bottom_margin_dp * s - padR(s);
  const stimY = (h, s) => (h - D.pads.bottom_margin_dp * s - 2 * padR(s)) / 2;
  const one = (pressed, cue) => (g, w, h, s) => Paint.pad(g, s, w / 2, padY(h, s), padR(s), pressed, cue);
  const two = (pressed) => (g, w, h, s) => {
    const r = padR(s), m = D.pads.side_margin_dp * s;
    Paint.pad(g, s, m + r, padY(h, s), r, pressed === 'LEFT', false); Paint.pad(g, s, w - m - r, padY(h, s), r, pressed === 'RIGHT', false);
  };
  const scene = (ms, ...layers) => ({ ms, draw: (g, w, h, s) => layers.forEach((f) => f(g, w, h, s)) });
  const stim = (id) => (g, w, h, s) => Paint.stimulus(g, s, id, w / 2, stimY(h, s));
  const fix = () => (g, w, h, s) => Paint.fixation(g, s, w / 2, stimY(h, s));

  function fastIsi() {
    const blocks = CFG.tasks.GNG.blocks || [];
    const b = blocks.find((x) => JSON.stringify(x).includes('fast')) || {};
    const k = Object.keys(b).find((x) => /isi/.test(x));
    return k ? b[k] : 1000;
  }
  function scenesFor(partId) {
    const G = CFG.tasks.GNG, on = G.stimulus_ms, gap = fastIsi();
    // The real task shows a fixation cross whenever no shape is on screen (engine: fixationInForeperiod/AfterStimulus).
    if (partId === 'GNG') return [
      scene(900, fix(), one(false)), scene(on, stim(G.stimuli.go), one(false)), scene(100, fix(), one(false)), scene(160, fix(), one(true)),
      scene(gap - 260, fix(), one(false)), scene(on, stim(G.stimuli.nogo), one(false)), scene(gap + 200, fix(), one(false))];
    if (partId === 'FLK') { const p = CFG.tasks.FLK.stimulus_prefix; return [
      scene(700, fix(), two(null)), scene(560, stim(p + '_incongruent_right'), two(null)), scene(160, two('RIGHT')),
      scene(800, fix(), two(null)), scene(520, stim(p + '_congruent_left'), two(null)), scene(160, two('LEFT')), scene(700, two(null))]; }
    if (partId === 'SPF' || partId === 'SPB') {
      const id = CFG.tasks[partId].board, seq = [1, 6, 3], taps = partId === 'SPB' ? seq.slice().reverse() : seq;
      const bs = (s, w) => Math.min(s, (w - 24) / S[id].width_dp);
      const board = (hl, echo, cue) => (g, w, h, s) => Paint.board(g, bs(s, w), id, w / 2, h / 2, hl, echo, cue);
      const out = [scene(800, board(null, null, false))];
      for (const i of seq) out.push(scene(700, board(i, null, false)), scene(300, board(null, null, false)));
      out.push(scene(500, board(null, null, true)));
      for (const i of taps) out.push(scene(200, board(null, i, true)), scene(450, board(null, null, true)));
      out.push(scene(900, board(null, null, false)));
      return out;
    }
    if (partId === 'TRP') { const st = CFG.tasks.TRP.stimulus; return [
      scene(700, fix(), one(false)), scene(2000, stim(st), one(false)), scene(500, one(false)), scene(350, one(false, true)),
      scene(2000, one(true, true)), scene(1000, one(false))]; }
    if (partId === 'CDT') {
      const opts = (stage) => (g, w, h, s) => {
        const r = padR(s), m = D.pads.side_margin_dp * s;
        Paint.choice(g, s, { left: { tokens: 1, longDelay: false, active: true }, right: { tokens: 2, longDelay: true, active: true }, stage },
          m + r, w - m - r, w / 2, stimY(h, s));
      };
      const reward = (g, w, h, s) => Paint.choice(g, s, { left: null, right: null, stage: 'REWARD', rewardTokens: 2 }, 0, 0, w / 2, stimY(h, s));
      return [scene(1600, opts('CHOOSE'), two(null)), scene(200, opts('CHOOSE'), two('RIGHT')), scene(1200, reward), scene(700, () => {})];
    }
    return [scene(1000, () => {})];
  }
  const STILL = { GNG: 1, FLK: 1, SPF: 1, SPB: 1, TRP: 1, CDT: 0 };

  function thumb(info) {
    const cv = el('canvas', { class: 'field', width: 240, height: 144, 'aria-hidden': 'true' });
    const g = cv.getContext('2d');
    g.scale(2, 2);
    g.fillStyle = rgb(D.background_rgb); g.fillRect(0, 0, 120, 72);
    const part = info.parts[0];
    if (part === 'GNG') Paint.stimulus(g, 0.45, CFG.tasks.GNG.stimuli.go, 60, 36);
    if (part === 'FLK') Paint.stimulus(g, 0.38, CFG.tasks.FLK.stimulus_prefix + '_incongruent_left', 60, 36);
    if (part === 'SPF') Paint.board(g, 0.22, CFG.tasks.SPF.board, 60, 36, 4, null, false);
    if (part === 'TRP') Paint.stimulus(g, 0.45, CFG.tasks.TRP.stimulus, 60, 36);
    if (part === 'CDT') Paint.choice(g, 0.22, { left: { tokens: 1, longDelay: false, active: true }, right: { tokens: 2, longDelay: true, active: true }, stage: 'CHOOSE' }, 30, 86, 60, 34);
    return cv;
  }

  // ------------------------------------------------------------------ shared pieces
  function langToggle(redraw) {
    return button(t('lang_switch'), () => { lang = lang === 'ur' ? 'en' : 'ur'; redraw(); }, true);
  }
  const act = (label, onclick, name, quiet) => el('button', { type: 'button', class: quiet ? 'quiet' : null, 'data-act': name || null, onclick }, label);
  function topbar(onLanding) {
    return el('div', { class: 'topbar' },
      onLanding ? el('span', null) : el('a', { class: 'wordmark', href: '#', onclick: (e) => { e.preventDefault(); landing(); } }, 'Cognisense'),
      el('span', { class: 'demo-mark' }, 'Demonstration, not a measurement'));
  }
  function linksRow() {
    const items = [['paper', 'Read the paper'], ['code', 'Source code and documentation'], ['feedback', 'Give feedback']].filter(([k]) => LINKS[k]);
    return items.length ? el('p', { class: 'actions' }, ...items.map(([k, label]) => el('a', { href: LINKS[k], rel: 'noopener' }, label))) : null;
  }
  function researcherDrawer() {
    return el('details', null, el('summary', null, 'For researchers'),
      el('p', null, 'Task logic, trial sequences and scoring come from the same Kotlin core as the Android app, compiled to JavaScript. In scripted tests the two builds produce byte-identical records. The demo runs the shortened Expert Review variant of each task. Reaction times are timed by this browser and are not measurements.'),
      el('p', null, 'Task configuration ', el('span', { class: 'code' }, api.configVersion), ', SHA-256 ', el('span', { class: 'code' }, api.configSha256), '. Interface text version ', el('span', { class: 'code' }, STR.version), '.'),
      el('p', null, 'Privacy: the page makes no network requests and uses no cookies or storage. Data stay in this tab unless you download them.'),
      el('p', null, 'Not shown in this demo: five further candidate tasks (simple reaction time, a continuous performance test, stop-signal, spatial n-back and task switching) are specified but not yet implemented.'));
  }
  const footer = () => el('footer', { class: 'disclaimer', lang: 'en' }, tl('disclaimer', 'en'));
  function steps(current) {
    if (!flow || flow.mode !== 'guided') return null;
    return el('ol', { class: 'steps', 'aria-label': 'Demo progress' }, ...flow.infos.map((info, i) =>
      el('li', { class: i < current ? 'done' : i === current ? 'current' : null, 'aria-current': i === current ? 'step' : null }, COPY[info.code].name)));
  }
  /** A timing class as a small letter badge; deliberately neutral (no colour that reads as good or bad). */
  const clsBadge = (c, decorative) => el('span', { class: 'cls-badge', 'aria-hidden': decorative ? 'true' : null }, c);
  const capitalise = (x) => x.charAt(0).toUpperCase() + x.slice(1);
  const lowerFirst = (x) => x.charAt(0).toLowerCase() + x.slice(1);
  function keysFor(partId) {
    const pads = CFG.tasks[partId].pads;
    if (partId === 'TRP') return 'preview_keys_hold';
    return pads === 'BOARD' ? 'preview_keys_board' : pads === 'LEFT_RIGHT' ? 'preview_keys_lr' : 'preview_keys_single';
  }

  // ------------------------------------------------------------------ screens
  function landing() {
    flow = null;
    const hero = fieldCanvas();
    page('landing',
      topbar(true),
      el('section', { class: 'hero' },
        el('div', null,
          el('h1', null, 'Cognisense'),
          el('p', { class: 'lede' }, 'A research prototype that runs five standard cognitive tasks on ordinary devices, and states plainly which of its measurements a cheap screen can distort.'),
          el('p', { class: 'measure' }, 'It was built for Pakistan and similar settings, where specialist assessment is scarce and low-cost Android phones are what is available. It does not diagnose ADHD or any other condition.'),
          el('div', { class: 'actions' }, act('Start the 10-minute demo', () => startFlow('guided', GUIDED), 'guided')),
          el('p', { class: 'hint' }, 'Best on a laptop, or on a phone turned sideways. Nothing you do leaves this page.')),
        el('figure', null, hero, el('figcaption', null, 'The Go/No-Go task as participants see it, at its real timing: tap for the circle, hold back for the square.'))),
      el('h2', null, 'Or try one task'),
      el('ul', { class: 'tasks' }, ...GUIDED.map((info) => el('li', { class: 'task-row' },
        thumb(info),
        el('div', null, el('h3', null, COPY[info.code].name), el('p', null, COPY[info.code].measures), el('p', { class: 'duration' }, capitalise(info.review_duration) + '.')),
        el('button', { type: 'button', class: 'quiet', 'data-task': info.code, 'aria-label': 'Try ' + COPY[info.code].name, onclick: () => startFlow('single', [info]) }, 'Try')))),
      el('h2', null, 'How to read what it records'),
      el('p', { class: 'measure' }, 'Phones and browsers add their own delay between something appearing on screen and the moment a touch is timestamped. Cognisense labels every measurement by how much that delay can distort it.'),
      el('div', { class: 'classes' },
        el('div', null, el('b', null, clsBadge('A', true), 'Class A'), el('p', null, 'Timing-independent: counts, choices and accuracy.')),
        el('div', null, el('b', null, clsBadge('B', true), 'Class B'), el('p', null, 'Robust to a constant device delay, because the delay cancels in a difference between two reaction times.')),
        el('div', null, el('b', null, clsBadge('C', true), 'Class C'), el('p', null, 'Affected by device delay: absolute reaction times. Comparable only within one device model.'))),
      el('p', { class: 'measure small muted' }, 'In this demo every value is an illustration: the blocks are shortened and the timing comes from your browser.'),
      researcherDrawer(), linksRow(), footer());
    ambient = loop(hero, scenesFor('GNG'), STILL.GNG);
  }

  let flow = null;
  function startFlow(mode, infos) { flow = { mode, infos, i: 0, results: [] }; nextTask(); }
  function nextTask() { partStep(flow.infos[flow.i], 0, []); }
  function partStep(info, k, done) {
    if (k >= info.parts.length) {
      flow.results.push({ info, parts: done });
      flow.i++;
      return flow.mode === 'guided' && flow.i < flow.infos.length ? between() : summary();
    }
    const partId = info.parts[k];
    instructions(info, partId, k, () => runTask(partId, 'PRACTICE', 1, (practice) => {
      if (!practice) return stopped();
      realStart(partId, () => runTask(partId, 'SCORED', 1, (scored) => {
        if (!scored) return stopped();
        partStep(info, k + 1, done.concat([{ partId, practice, scored }]));
      }));
    }));
  }
  function stopped() { return flow && flow.results.length ? summary() : landing(); }

  function instructions(info, partId, k, onStart) {
    const c = COPY[info.code], demo = fieldCanvas();
    page('instructions',
      topbar(), steps(flow.i),
      el('div', { class: 'intro' },
        el('div', null,
          el('h1', null, PART_TITLE[partId]),
          k === 0
            ? el('dl', { class: 'facts' }, el('dt', null, 'What it measures'), el('dd', null, c.measures), el('dt', null, 'What you do'), el('dd', null, c.does))
            : el('p', { class: 'lede' }, PART_LEAD[partId] || c.does),
          el('div', { class: 'childtext' }, el('p', { class: 'label', lang: 'en' }, 'Instructions as a child hears them'), local('p', partId.toLowerCase() + '_instr')),
          local('p', keysFor(partId), { class: 'keys' }),
          el('div', { class: 'actions' },
            act('Start practice', onStart, 'primary'),
            langToggle(() => instructions(info, partId, k, onStart)),
            act('Leave the demo', landing, 'leave', true))),
        el('figure', null, demo, el('figcaption', null, DEMO_CAPTION[partId]))));
    ambient = loop(demo, scenesFor(partId), STILL[partId]);
  }

  function realStart(partId, onStart) {
    page('real_start', topbar(), steps(flow.i),
      el('div', { class: 'measure' },
        el('h1', null, PART_TITLE[partId] + ': the scored block'),
        local('p', 'real_start', { class: 'lede' }),
        el('div', { class: 'actions' }, act('Start', onStart, 'primary'))));
  }

  function between() {
    const prev = flow.infos[flow.i - 1], next = flow.infos[flow.i];
    page('between', topbar(), steps(flow.i),
      el('div', { class: 'measure' },
        el('h1', null, COPY[prev.code].name + ' complete'),
        el('p', { class: 'lede' }, 'Next: ' + COPY[next.code].name + '. ' + COPY[next.code].measures),
        el('p', { class: 'muted' }, capitalise(next.review_duration) + '.'),
        el('div', { class: 'actions' },
          act('Continue to ' + COPY[next.code].name, nextTask, 'primary'),
          act('Stop and see results so far', summary, 'stop', true))));
  }

  // ------------------------------------------------------------------ results
  const rt = (r) => r.rt_ms_not_a_measurement;
  const median = (v) => { if (!v.length) return null; const a = v.slice().sort((x, y) => x - y), m = a.length >> 1; return a.length % 2 ? a[m] : (a[m - 1] + a[m]) / 2; };
  const NS = 'http://www.w3.org/2000/svg';
  function Sv(tag, attrs, ...kids) {
    const e = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs || {})) if (v !== undefined && v !== null) e.setAttribute(k, v);
    for (const k of kids) if (k !== null && k !== undefined) e.append(k.nodeType ? k : document.createTextNode(String(k)));
    return e;
  }
  const frame = (w, h, label) => Sv('svg', { class: 'chart', viewBox: `0 0 ${w} ${h}`, role: 'img', 'aria-label': label });
  const txt = (x, y, s, attrs) => Sv('text', Object.assign({ x, y }, attrs || {}), s);
  function glyph(kind, cx, cy) {
    if (kind === 'hit') return Sv('circle', { cx, cy, r: 4.5, style: 'fill: var(--ink)' });
    if (kind === 'miss') return Sv('circle', { cx, cy, r: 4.5, style: 'fill: none; stroke: var(--error); stroke-width: 1.6' });
    if (kind === 'withhold') return Sv('line', { x1: cx, x2: cx, y1: cy - 7, y2: cy, style: 'stroke: var(--ink); stroke-width: 2' });
    if (kind === 'commission') return Sv('path', { d: `M${cx - 4.5} ${cy - 4.5}L${cx + 4.5} ${cy + 4.5}M${cx - 4.5} ${cy + 4.5}L${cx + 4.5} ${cy - 4.5}`, style: 'fill: none; stroke: var(--error); stroke-width: 2' });
    if (kind === 'early') return Sv('rect', { x: cx - 4, y: cy - 4, width: 8, height: 8, style: 'fill: none; stroke: var(--muted); stroke-width: 1.4' });
    if (kind === 'ok') return Sv('rect', { x: cx - 10, y: cy - 10, width: 20, height: 20, rx: 2, style: 'fill: var(--ink)' });
    if (kind === 'fail') return Sv('rect', { x: cx - 9.25, y: cy - 9.25, width: 18.5, height: 18.5, rx: 2, style: 'fill: none; stroke: var(--error); stroke-width: 1.5' });
    if (kind === 'token') return Sv('circle', { cx, cy, r: 6, style: 'fill: var(--token)' });
    if (kind === 'forced') return Sv('circle', { cx, cy, r: 5.25, style: 'fill: none; stroke: var(--muted); stroke-width: 1.5' });
    return null;
  }
  const legend = (items) => el('ul', { class: 'legend' }, ...items.map(([kind, label]) =>
    el('li', null, Sv('svg', { viewBox: '-8 -8 16 16', 'aria-hidden': 'true' }, glyph(kind, 0, kind === 'withhold' ? 3.5 : 0)), label)));
  function yAxis(svg, ticks, y, L, R, W, unit) {
    for (const v of ticks) { svg.append(Sv('line', { class: 'axis', x1: L, x2: W - R, y1: y(v), y2: y(v) })); svg.append(txt(L - 8, y(v) + 4, v, { 'text-anchor': 'end' })); }
    if (unit) svg.append(txt(L - 8, y(ticks[ticks.length - 1]) - 10, unit, { 'text-anchor': 'end' }));
  }

  function chartGNG(recs) {
    const W = 640, H = 236, L = 52, R = 12, T = 22, B = 30, n = recs.length, cw = (W - L - R) / Math.max(1, n);
    const top = CFG.tasks.GNG.response_window_ms, x = (i) => L + (i + 0.5) * cw, y = (v) => T + (1 - Math.min(v, top) / top) * (H - T - B);
    const svg = frame(W, H, 'Go/No-Go trials in order, by outcome and reaction time');
    for (let i = 0; i < n;) {
      if (recs[i].block_condition !== 'slow') { i++; continue; }
      let j = i; while (j < n && recs[j].block_condition === 'slow') j++;
      svg.append(Sv('rect', { class: 'band', x: L + i * cw, y: T, width: (j - i) * cw, height: H - T - B }), txt(L + i * cw + 6, T + 14, 'slow pace'));
      i = j;
    }
    yAxis(svg, [0, 250, 500, 750, 1000].filter((v) => v <= top), y, L, R, W, 'ms');
    svg.append(txt(L, H - 8, 'Trials in order'));
    let early = false;
    recs.forEach((r, k) => {
      const go = r.condition === 'go', o = r.outcome, v = rt(r);
      let g = null;
      if (o === 'ANTICIPATION') { early = true; g = glyph('early', x(k), y(v || 0)); }
      else if (go && o === 'CORRECT') g = glyph('hit', x(k), y(v));
      else if (go && o === 'OMISSION') g = glyph('miss', x(k), y(top));
      else if (!go && o === 'CORRECT') g = glyph('withhold', x(k), y(0));
      else if (!go && o === 'COMMISSION') g = glyph('commission', x(k), y(v || top));
      if (g) svg.append(g);
    });
    return { svg, legend: legend([['hit', 'Tap to a circle'], ['miss', 'Missed circle'], ['commission', 'Tap to a square'], ['withhold', 'Square correctly ignored']].concat(early ? [['early', 'Too fast to count']] : [])),
      caption: 'Each mark is one trial, in order. Taps are placed at their reaction time; missed circles sit at the top, past the response window. The shaded span is the slow-pace block.' };
  }

  function chartFLK(recs, scored) {
    const W = 640, H = 250, L = 52, R = 12, T = 22, B = 46;
    const ok = recs.filter((r) => r.outcome === 'CORRECT' && rt(r) != null);
    const top = Math.max(1000, Math.ceil(Math.max(0, ...ok.map(rt)) / 250) * 250), y = (v) => T + (1 - v / top) * (H - T - B);
    const svg = frame(W, H, 'Flanker reaction times for congruent and incongruent trials');
    const ticks = []; for (let v = 0; v <= top; v += 250) ticks.push(v);
    yAxis(svg, ticks, y, L, R, W, 'ms');
    const cx = [L + (W - L - R) * 0.3, L + (W - L - R) * 0.7], med = [];
    [['congruent', 'Arrows agree'], ['incongruent', 'Arrows disagree']].forEach(([cond, label], ci) => {
      const v = ok.filter((r) => r.condition === cond).map(rt);
      v.forEach((t0, k) => svg.append(glyph('hit', cx[ci] + ((k * 37) % 41) - 20, y(t0))));
      const m = median(v); med.push(m);
      if (m != null) svg.append(Sv('line', { x1: cx[ci] - 36, x2: cx[ci] + 36, y1: y(m), y2: y(m), style: 'stroke: var(--ink); stroke-width: 2.5' }));
      const errs = recs.filter((r) => r.condition === cond && !['CORRECT', 'INTERRUPTED'].includes(r.outcome)).length;
      svg.append(txt(cx[ci], H - 24, label, { 'text-anchor': 'middle', style: 'fill: var(--ink)' }), txt(cx[ci], H - 8, errs ? errs + (errs > 1 ? ' errors' : ' error') : 'no errors', { 'text-anchor': 'middle' }));
    });
    if (med[0] != null && med[1] != null) {
      const rec = (scored.metrics.find((m) => m.name === 'interference_rt') || {}).value;
      const d = Math.round(rec !== undefined && rec !== null ? rec : med[1] - med[0]);
      svg.append(Sv('line', { x1: cx[0] + 40, x2: cx[1] - 40, y1: y(med[0]), y2: y(med[1]), style: 'stroke: var(--muted); stroke-dasharray: 3 4' }),
        txt((cx[0] + cx[1]) / 2, y((med[0] + med[1]) / 2) - 10, (d > 0 ? '+' : d < 0 ? '\u2212' : '') + Math.abs(d) + ' ms', { 'text-anchor': 'middle', style: 'fill: var(--ink); font-weight: 700' }));
    }
    return { svg, legend: null, caption: 'Each dot is a correct trial; the bar is the median. The gap between the two medians is the interference cost, a class B measure: a constant device delay cancels out of it.' };
  }

  function chartSpan(recs, scored) {
    const byLen = new Map();
    recs.filter((r) => r.outcome !== 'INTERRUPTED').forEach((r) => { const n = +r.details.length; if (!byLen.has(n)) byLen.set(n, []); byLen.get(n).push(r.outcome === 'CORRECT'); });
    const lens = [...byLen.keys()].sort((a, b) => a - b), colW = 52, L = 8, W = 640, H = 112;
    const svg = frame(W, H, 'Spatial span sequences by length and outcome');
    lens.forEach((len, ci) => {
      const cx = L + 26 + ci * colW;
      byLen.get(len).forEach((ok, k) => svg.append(glyph(ok ? 'ok' : 'fail', cx, 18 + k * 28)));
      svg.append(txt(cx, H - 22, len, { 'text-anchor': 'middle', style: 'fill: var(--ink)' }));
    });
    svg.append(txt(L + 2, H - 4, 'Sequence length'));
    const span = (scored.metrics.find((m) => m.name === 'span') || {}).value;
    if (span !== undefined && span !== null) svg.append(txt(L + 26 + lens.length * colW + 16, 34, 'Span ' + span, { style: 'fill: var(--ink); font-weight: 700; font-size: 16px' }));
    return { svg, legend: legend([['ok', 'Recalled correctly'], ['fail', 'Not recalled']]),
      caption: 'Each square is one sequence. Two sequences are given at each length; the task moves on after a success and stops after two failures at one length.' };
  }

  function chartTRP(recs) {
    const pts = recs.filter((r) => r.details && r.details.reproduced_ms !== '' && r.details.reproduced_ms !== undefined && isFinite(+r.details.reproduced_ms))
      .map((r) => [+r.details.target_ms / 1000, +r.details.reproduced_ms / 1000]);
    const W = 640, H = 300, L = 52, R = 16, T = 22, B = 40, top = Math.max(7, Math.ceil(Math.max(0, ...pts.map((p) => p[1]))));
    const x = (v) => L + (v / top) * (W - L - R), y = (v) => T + (1 - v / top) * (H - T - B);
    const svg = frame(W, H, 'Time reproduction: shown against held duration');
    const ticks = []; for (let v = 0; v <= top; v += top > 8 ? 2 : 1) ticks.push(v);
    yAxis(svg, ticks, y, L, R, W, 's held');
    ticks.forEach((v) => svg.append(txt(x(v), H - B + 18, v, { 'text-anchor': 'middle' })));
    svg.append(txt(W - R, H - 6, 'seconds shown', { 'text-anchor': 'end' }));
    svg.append(Sv('line', { x1: x(0), y1: y(0), x2: x(top), y2: y(top), style: 'stroke: var(--muted); stroke-dasharray: 4 4' }));
    const seen = {};
    pts.forEach(([a, b]) => { const k = (seen[a] = (seen[a] || 0) + 1) - 1; svg.append(glyph('hit', x(a) + (k % 3 - 1) * 7, y(b))); });
    return { svg, legend: null, caption: 'Each dot is one trial. Dots on the dashed line would be perfect reproductions. A common pattern is holding too long for short durations and too briefly for long ones.' };
  }

  function chartCDT(recs) {
    const items = recs.filter((r) => r.details && r.details.kind), W = 640, H = 92, step = 40, L = 8;
    const svg = frame(W, H, 'Choice-delay choices in order');
    items.forEach((r, i) => {
      const cx = L + 20 + i * step, forced = r.details.kind !== 'free', n = r.details.choice === 'LL' ? 2 : r.details.choice === 'SS' ? 1 : 0;
      for (let k = 0; k < n; k++) svg.append(glyph(forced ? 'forced' : 'token', cx, 40 - k * 16 + (n === 1 ? -8 : 0)));
      if (!n) svg.append(txt(cx, 40, '\u2013', { 'text-anchor': 'middle' }));
    });
    svg.append(txt(L + 2, H - 6, 'Choices in order'));
    const anyForced = items.some((r) => r.details.kind !== 'free');
    return { svg, legend: legend([['token', 'Free choice (one token: smaller, sooner; two: larger, later)']].concat(anyForced ? [['forced', 'Forced choice, to learn the options']] : [])),
      caption: 'Tokens in this task are never totalled or exchanged, so there is no real reward. That is a known limitation of this version.' };
  }
  const CHARTS = { GNG: chartGNG, FLK: chartFLK, SPF: chartSpan, SPB: chartSpan, TRP: chartTRP, CDT: chartCDT };

  function fmtVal(m) {
    if (m.value === null || m.value === undefined || !isFinite(m.value)) return '\u2014';
    if (m.unit === 'proportion') return Math.round(m.value * 100) + '%';
    if (m.unit === 'count') return String(Math.round(m.value));
    if (m.unit === 'ms') return Math.round(m.value) + ' ms';
    if (Number.isInteger(m.value)) return String(m.value);
    return (Math.round(m.value * 100) / 100).toFixed(2);
  }
  function metricsTable(metrics, names) {
    const rows = names ? names.map((n) => metrics.find((m) => m.name === n)).filter(Boolean) : metrics;
    return el('table', { class: 'metrics' },
      el('thead', null, el('tr', null, el('th', null, 'Measure'), el('th', { class: 'num' }, 'Value'), el('th', null, 'Timing class'))),
      el('tbody', null, ...rows.map((m) => el('tr', null,
        el('td', null, names ? (METRIC_LABEL[m.name] || m.name) : m.name),
        el('td', { class: 'num' }, fmtVal(m)),
        el('td', { class: 'cls' }, ...(m.timing_class
          ? [clsBadge(m.timing_class), el('span', null, CLASS_LABEL[m.timing_class] || '')]
          : []))))));
  }
  function quality(scored, practice) {
    const hz = scored.measured_hz ? Math.round(scored.measured_hz) : null;
    return 'Practice criterion ' + (practice && practice.practice_criterion_met ? 'met' : 'not met') + '. Timed by this browser' +
      (hz ? ' at about ' + hz + ' Hz' : '') + ', with ' + scored.dropped_frames + ' dropped frame' + (scored.dropped_frames === 1 ? '' : 's') +
      ' and ' + scored.interruptions + ' interruption' + (scored.interruptions === 1 ? '' : 's') + '.';
  }
  function partResult(p, sub) {
    const ch = CHARTS[p.partId](p.scored.records, p.scored);
    return el('div', { class: 'part' },
      sub ? el('h3', null, PART_TITLE[p.partId]) : null,
      ch.svg, ch.legend,
      el('p', { class: 'small muted measure' }, ch.caption),
      metricsTable(p.scored.metrics, KEY_METRICS[p.partId]),
      el('p', { class: 'quality' }, quality(p.scored, p.practice)),
      el('details', { class: 'all-measures' }, el('summary', null, 'All recorded measures'), metricsTable(p.scored.metrics, null)));
  }
  function summary() {
    const res = flow ? flow.results : [];
    page('summary', topbar(),
      el('div', { class: 'measure' },
        el('h1', null, 'What the demo recorded'),
        el('p', { class: 'lede' }, 'Raw values from shortened blocks, timed by your browser. They show what Cognisense records. They are not scores, and there is nothing to compare them with.')),
      res.length > 1 ? el('nav', { class: 'toc', 'aria-label': 'Tasks on this page' },
        el('ol', null, ...res.map((r) => el('li', null, el('a', {
          href: '#result-' + r.info.code,
          onclick: (e) => { e.preventDefault(); document.getElementById('result-' + r.info.code).scrollIntoView(); },
        }, COPY[r.info.code].name))))) : null,
      ...res.map((r) => el('section', { class: 'result', id: 'result-' + r.info.code },
        el('h2', null, COPY[r.info.code].name),
        el('p', { class: 'muted measure' }, COPY[r.info.code].measures),
        ...r.parts.map((p) => partResult(p, r.parts.length > 1)))),
      el('div', { class: 'actions' }, act('Download this session (JSON)', download, 'download'), act('Back to the start', landing, 'home', true)),
      researcherDrawer(), linksRow(), footer());
  }

  /** The reviewer's own data, saved to their device only. A blob: URL is local; nothing is sent anywhere. */
  function download() {
    const doc = {
      browser_preview: true,
      not_a_measurement: tl('preview_banner', 'en'),
      disclaimer: tl('disclaimer', 'en'),
      user_agent: navigator.userAgent,
      started_utc: session.started_utc,
      downloaded_utc: new Date().toISOString(),
      config_version: api.configVersion,
      config_sha256: api.configSha256,
      strings_version: STR.version,
      session_seed: session.seed,
      runs: session.runs,
    };
    const url = URL.createObjectURL(new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' }));
    const a = el('a', { href: url, download: 'cognisense-preview-' + session.started_utc.replace(/[:.]/g, '-') + '.json' });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  // Read-only hook for the automated browser tests (browser/test). Exposes nothing that is not already on the page.
  Object.defineProperty(window, '__cognisensePreview', {
    value: Object.freeze({
      get screen() { return screen; },
      get content() { return task ? task.content : null; },
      get runs() { return session.runs.length; },
      get part() { return task ? task.partId : null; },
      configSha256: api.configSha256,
      squarePoint: (i) => (task ? task.scene.squarePoint(i) : null),
      padPoint: (k) => (task ? task.scene.padPoint(k) : null),
      // Test seam: the guided flow over a subset of tasks (the full demo takes about 10 minutes).
      startGuided: (codes) => startFlow('guided', codes.map((c) => CFG.pool.find((p) => p.code === c))),
    }),
  });

  landing();
})();
