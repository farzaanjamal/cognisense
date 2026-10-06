"""Tests for timing_simulation.py: run with  python -m unittest test_timing_simulation.py"""
import unittest

import numpy as np

import timing_simulation as ts


class TimingSimulationTests(unittest.TestCase):
    def setUp(self):
        self.rng = np.random.default_rng(1)
        self.c = ts.draw_children(self.rng, 300)
        self.s = ts.draw_session(self.rng, self.c)
        self.zero = np.zeros(300)
        self.ref = ts.metrics(ts.measure(self.rng, self.s, self.zero, self.zero))

    def test_no_device_error_reproduces_reference(self):
        m = ts.metrics(ts.measure(self.rng, self.s, self.zero, self.zero))
        for k in ts.METRICS:
            np.testing.assert_array_equal(m[k], self.ref[k], err_msg=k)

    def test_constant_offset_cancels_from_flanker_cost_without_window(self):
        saved = ts.FLK_WINDOW_MS
        ts.FLK_WINDOW_MS = 10 ** 9
        try:
            ref = ts.metrics(ts.measure(self.rng, self.s, self.zero, self.zero))
            k = np.full(300, 97.0)  # whole-ms offset, no jitter: rounding commutes with it
            m = ts.metrics(ts.measure(self.rng, self.s, k, self.zero))
            np.testing.assert_allclose(m["flanker_cost"], ref["flanker_cost"], atol=1e-9)
        finally:
            ts.FLK_WINDOW_MS = saved

    def test_offset_breaks_cancellation_only_at_the_window_edge(self):
        k = 97.0
        m = ts.metrics(ts.measure(self.rng, self.s, np.full(300, k), self.zero))
        bad = np.where(~np.isclose(m["flanker_cost"], self.ref["flanker_cost"]))[0]
        for i in bad:
            rts = np.round(np.concatenate([self.s["con"][i], self.s["inc"][i]]))
            self.assertTrue(np.any((rts > ts.FLK_WINDOW_MS - k) & (rts <= ts.FLK_WINDOW_MS)), i)
        self.assertLess(len(bad), 0.05 * 300)

    def test_constant_offset_shifts_median_rt_and_lowers_cv(self):
        k = np.full(300, 97.0)
        m = ts.metrics(ts.measure(self.rng, self.s, k, self.zero))
        self.assertGreater(np.nanmean(m["go_rt_median"] - self.ref["go_rt_median"]), 80)
        self.assertLess(np.nanmean(m["go_rt_cv"]), np.nanmean(self.ref["go_rt_cv"]))

    def test_offset_pushes_slow_responses_out_of_window(self):
        k = np.full(300, 140.0)
        m = ts.metrics(ts.measure(self.rng, self.s, k, self.zero))
        self.assertGreater(m["omission_rate"].mean(), self.ref["omission_rate"].mean())

    def test_jitter_inflates_isd(self):
        j = np.full(300, 40.0)
        m = ts.metrics(ts.measure(self.rng, self.s, self.zero, j))
        self.assertGreater(np.nanmean(m["go_rt_isd"]), np.nanmean(self.ref["go_rt_isd"]))

    def test_every_metric_has_a_timing_class(self):
        self.assertEqual(set(ts.METRICS.values()) <= {"A", "B", "C"}, True)
        self.assertEqual(set(ts.metrics(ts.measure(self.rng, self.s, self.zero, self.zero))),
                         set(ts.METRICS))


if __name__ == "__main__":
    unittest.main()
