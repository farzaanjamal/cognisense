"""Monte Carlo study: how device timing error propagates into Cognisense metrics.

Tests hypotheses H1-H3 of the Cognisense paper (section 1.4):
  H1  class A metrics (counts, proportions) are unaffected by device timing error;
  H2  class B metrics (within-person differences, dispersion in ms) keep r >= .95 with
      their error-free values even when children are tested on different devices;
  H3  class C metrics (absolute RT, CV) lose agreement and test-retest reliability when
      children are tested on different devices, but not on a single device model.

What is assumed, not measured:
  * child response times: ex-Gaussian with population parameters below (illustrative);
  * device error: a constant offset per device plus Gaussian jitter per trial;
    offsets span 35-140 ms, the range of total display+touch latency measured across
    smartphones by Nicosia et al. (2023, Behavior Research Methods 55:2800-2812).
Trial structure and response windows are taken from config/tasks.json (v0.2).

Run:   python timing_simulation.py                 (500 replicates; ~1-2 min)
       python timing_simulation.py --reps 50       (quick check)
Output: JSON summary on stdout, or to --out.
Reproducibility: numpy does not promise identical random streams across versions, so the results
record the numpy version that produced them (results/ were made with the version pinned in CI).
"""
import argparse
import json
import sys

import numpy as np

# ---- trial structure from config/tasks.json v0.2 -------------------------------------
GNG_GO, GNG_NOGO = 108, 36          # config v0.2: 144 trials, 25% No-Go (the paper's main analysis)
GNG_WINDOW_MS = 1000                # response window, measured in logged (device) time
FLK_PER_COND = 40                   # 80 trials, half congruent
FLK_WINDOW_MS = 2000

# ---- assumed child population (illustrative, not empirical) ------------------------
CHILD = dict(mu=(450.0, 60.0), sigma=(50.0, 12.0), tau=(150.0, 50.0),
             flanker_shift=50.0, cost=(60.0, 25.0), pcomm_beta=(2.0, 8.0))

# ---- device scenarios ---------------------------------------------------------------
SCENARIOS = {
    "single_device": dict(offset=("fixed", 87.5), jitter=("fixed", 10.0)),
    "mixed_devices": dict(offset=("uniform", 35.0, 140.0), jitter=("uniform", 5.0, 20.0)),
    "mixed_high_jitter": dict(offset=("uniform", 35.0, 140.0), jitter=("uniform", 10.0, 40.0)),
}

METRICS = {  # name: timing class
    "commission_rate": "A", "omission_rate": "A",
    "go_rt_isd": "B", "flanker_cost": "B",
    "go_rt_median": "C", "go_rt_cv": "C",
}


def draw_children(rng, n):
    c = {}
    c["mu"] = rng.normal(*CHILD["mu"], n)
    c["sigma"] = np.clip(rng.normal(*CHILD["sigma"], n), 15, None)
    c["tau"] = np.clip(rng.normal(*CHILD["tau"], n), 30, None)
    c["cost"] = rng.normal(*CHILD["cost"], n)
    c["pcomm"] = rng.beta(*CHILD["pcomm_beta"], n)
    return c


def exgauss(rng, mu, sigma, tau, k):
    n = mu.shape[0]
    return rng.normal(mu[:, None], sigma[:, None], (n, k)) + rng.exponential(tau[:, None], (n, k))


def draw_session(rng, c):
    """True (error-free) behaviour for one session: RTs in ms, NaN = no response."""
    n = c["mu"].shape[0]
    go = exgauss(rng, c["mu"], c["sigma"], c["tau"], GNG_GO)
    # No-Go: a response occurs with probability pcomm, at a Go-like latency
    responds = rng.random((n, GNG_NOGO)) < c["pcomm"][:, None]
    nogo = np.where(responds, exgauss(rng, c["mu"], c["sigma"], c["tau"], GNG_NOGO), np.nan)
    fmu = c["mu"] + CHILD["flanker_shift"]
    con = exgauss(rng, fmu, c["sigma"], c["tau"], FLK_PER_COND)
    inc = exgauss(rng, fmu + c["cost"], c["sigma"], c["tau"], FLK_PER_COND)
    return dict(go=go, nogo=nogo, con=con, inc=inc)


def draw_device(rng, n, spec):
    def one(s):
        if s[0] == "fixed":
            return np.full(n, s[1])
        return rng.uniform(s[1], s[2], n)
    return one(spec["offset"]), one(spec["jitter"])


def measure(rng, sess, offset, jitter):
    """Logged RTs: true RT + device offset + per-trial jitter, rounded to whole ms."""
    out = {}
    for k, v in sess.items():
        e = offset[:, None] + rng.normal(0.0, 1.0, v.shape) * jitter[:, None]
        out[k] = np.round(v + e)
    return out


def metrics(s):
    go = np.where(s["go"] <= GNG_WINDOW_MS, s["go"], np.nan)          # late = omission
    nogo_resp = np.isfinite(s["nogo"]) & (s["nogo"] <= GNG_WINDOW_MS)
    con = np.where(s["con"] <= FLK_WINDOW_MS, s["con"], np.nan)
    inc = np.where(s["inc"] <= FLK_WINDOW_MS, s["inc"], np.nan)
    m = {}
    m["commission_rate"] = nogo_resp.mean(axis=1)
    m["omission_rate"] = np.isnan(go).mean(axis=1)
    m["go_rt_median"] = np.nanmedian(go, axis=1)
    m["go_rt_isd"] = np.nanstd(go, axis=1, ddof=1)
    m["go_rt_cv"] = m["go_rt_isd"] / np.nanmean(go, axis=1)
    m["flanker_cost"] = np.nanmedian(inc, axis=1) - np.nanmedian(con, axis=1)
    return m


def r(a, b):
    ok = np.isfinite(a) & np.isfinite(b)
    if ok.sum() < 3 or np.std(a[ok]) == 0 or np.std(b[ok]) == 0:
        return float("nan")
    return float(np.corrcoef(a[ok], b[ok])[0, 1])


def one_replicate(rng, n):
    c = draw_children(rng, n)
    s1, s2 = draw_session(rng, c), draw_session(rng, c)
    zero = np.zeros(n)
    ref1, ref2 = metrics(measure(rng, s1, zero, zero)), metrics(measure(rng, s2, zero, zero))
    res = {"agreement": {}, "bias": {}, "retest": {"no_device_error": {}}}
    for m in METRICS:
        res["retest"]["no_device_error"][m] = r(ref1[m], ref2[m])
    for name, spec in SCENARIOS.items():
        off, jit = draw_device(rng, n, spec)
        m1 = metrics(measure(rng, s1, off, jit))
        m2_same = metrics(measure(rng, s2, off, jit))
        off2, jit2 = draw_device(rng, n, spec)
        m2_switch = metrics(measure(rng, s2, off2, jit2))
        res["agreement"][name] = {m: r(m1[m], ref1[m]) for m in METRICS}
        res["bias"][name] = {m: float(np.nanmean(m1[m] - ref1[m])) for m in METRICS}
        res["retest"][name + "_same_device"] = {m: r(m1[m], m2_same[m]) for m in METRICS}
        if spec["offset"][0] != "fixed":
            res["retest"][name + "_new_device"] = {m: r(m1[m], m2_switch[m]) for m in METRICS}
    return res


def summarise(reps):
    def agg(path):
        vals = np.array([_get(rep, path) for rep in reps], dtype=float)
        vals = vals[np.isfinite(vals)]
        if vals.size == 0:  # metric undefined in this condition (e.g. no omissions possible)
            return None
        return dict(mean=round(float(vals.mean()), 4),
                    lo=round(float(np.percentile(vals, 2.5)), 4),
                    hi=round(float(np.percentile(vals, 97.5)), 4))
    out = {}
    for section, conds in reps[0].items():
        out[section] = {cond: {m: agg((section, cond, m)) for m in ms} for cond, ms in conds.items()}
    return out


def _get(d, path):
    for p in path:
        d = d[p]
    return d


def main(argv=None):
    global GNG_WINDOW_MS, GNG_GO, GNG_NOGO
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--reps", type=int, default=500)
    ap.add_argument("--children", type=int, default=200)
    ap.add_argument("--seed", type=int, default=20261004)
    ap.add_argument("--gng-go", type=int, default=GNG_GO, help="Go trials (default: config v0.2, as in the paper)")
    ap.add_argument("--gng-nogo", type=int, default=GNG_NOGO, help="No-Go trials (config v0.3: --gng-go 96 --gng-nogo 32)")
    ap.add_argument("--gng-window", type=float, default=GNG_WINDOW_MS,
                    help="Go/No-Go response window in ms (sensitivity analysis; 'inf' = none)")
    ap.add_argument("--out")
    a = ap.parse_args(argv)
    GNG_WINDOW_MS = a.gng_window
    GNG_GO, GNG_NOGO = a.gng_go, a.gng_nogo
    rng = np.random.default_rng(a.seed)
    reps = [one_replicate(rng, a.children) for _ in range(a.reps)]
    result = dict(settings=dict(reps=a.reps, children=a.children, seed=a.seed,
                                gng_window_ms=a.gng_window, gng_go=a.gng_go, gng_nogo=a.gng_nogo,
                                numpy_version=np.__version__,
                                child_population=CHILD, scenarios=SCENARIOS,
                                metric_classes=METRICS),
                  summary=summarise(reps))
    text = json.dumps(result, indent=1)
    if a.out:
        with open(a.out, "w") as f:
            f.write(text)
    else:
        sys.stdout.write(text + "\n")


if __name__ == "__main__":
    main()
