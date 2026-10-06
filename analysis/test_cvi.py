"""Tests for cvi.py. Run: python -m unittest test_cvi.py (from analysis/)."""

import os
import tempfile
import unittest

import cvi


def write_csv(text: str) -> str:
    fd, path = tempfile.mkstemp(suffix=".csv")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(text)
    return path


class TestFormulas(unittest.TestCase):
    # Worked values as tabulated in Polit, Beck & Owen (2007) [VERIFY table]:
    # (N experts, A agreeing) -> (I-CVI, Pc, kappa*) rounded to 2-3 dp.
    CASES = [
        (3, 3, 1.00, 0.125, 1.00),
        (5, 4, 0.80, 0.156, 0.76),
        (6, 5, 0.83, 0.094, 0.82),
        (9, 7, 0.78, 0.070, 0.76),
    ]

    def test_polit_table_values(self):
        for n, a, icvi, pc, k in self.CASES:
            r = cvi.item_result(1, "relevance", "T", [4] * a + [1] * (n - a))
            self.assertAlmostEqual(r.i_cvi, icvi, places=2, msg=(n, a))
            self.assertAlmostEqual(r.pc, pc, places=3, msg=(n, a))
            self.assertAlmostEqual(r.kappa, k, places=2, msg=(n, a))

    def test_rating_of_3_counts_as_agreement(self):
        r = cvi.item_result(1, "relevance", "T", [3, 3, 3])
        self.assertEqual(r.agree, 3)

    def test_rating_of_2_does_not(self):
        r = cvi.item_result(1, "relevance", "T", [2, 4, 4])
        self.assertEqual(r.agree, 2)

    def test_lynn_rule(self):
        self.assertFalse(cvi.meets_lynn(5, 0.80))  # small panel needs 1.00
        self.assertTrue(cvi.meets_lynn(5, 1.00))
        self.assertTrue(cvi.meets_lynn(9, 7 / 9))  # .78 with 9 experts
        self.assertFalse(cvi.meets_lynn(9, 6 / 9))

    def test_kappa_labels(self):
        self.assertEqual(cvi.kappa_label(0.80), "excellent")
        self.assertEqual(cvi.kappa_label(0.65), "good")
        self.assertEqual(cvi.kappa_label(0.45), "fair")
        self.assertEqual(cvi.kappa_label(0.10), "poor")

    def test_decision_not_set_by_default(self):
        self.assertIsNone(cvi.DECISION_BANDS)
        r = cvi.item_result(1, "relevance", "T", [4, 4, 4])
        self.assertEqual(r.decision, "not_set")


class TestScale(unittest.TestCase):
    def test_scvi_ave_and_ua_with_missing(self):
        path = write_csv(
            "# synthetic test data\n"
            "round,panelist_id,task_id,dimension,rating\n"
            "1,E1,T1,relevance,4\n1,E2,T1,relevance,4\n1,E3,T1,relevance,3\n"
            "1,E1,T2,relevance,4\n1,E2,T2,relevance,1\n1,E3,T2,relevance,\n"
        )
        items, scales = cvi.compute(cvi.read_ratings(path))
        os.remove(path)
        t1 = next(i for i in items if i.task_id == "T1")
        t2 = next(i for i in items if i.task_id == "T2")
        self.assertEqual((t1.n, t1.agree), (3, 3))
        self.assertEqual((t2.n, t2.agree), (2, 1))  # missing rating excluded
        s = scales[0]
        self.assertAlmostEqual(s["s_cvi_ave"], (1.0 + 0.5) / 2, places=4)
        self.assertAlmostEqual(s["s_cvi_ua"], 0.5, places=4)
        self.assertEqual(s["n_panelists"], 3)

    def test_rounds_kept_separate(self):
        path = write_csv(
            "round,panelist_id,task_id,dimension,rating\n"
            "1,E1,T1,clarity,2\n2,E1,T1,clarity,4\n"
        )
        items, scales = cvi.compute(cvi.read_ratings(path))
        os.remove(path)
        self.assertEqual(len(items), 2)
        self.assertEqual({s["round"] for s in scales}, {1, 2})


class TestValidation(unittest.TestCase):
    def _expect_error(self, text):
        path = write_csv(text)
        try:
            with self.assertRaises(cvi.RatingError):
                cvi.read_ratings(path)
        finally:
            os.remove(path)

    def test_out_of_range(self):
        self._expect_error("round,panelist_id,task_id,dimension,rating\n1,E1,T1,relevance,5\n")

    def test_duplicate(self):
        self._expect_error("round,panelist_id,task_id,dimension,rating\n"
                           "1,E1,T1,relevance,4\n1,E1,T1,relevance,3\n")

    def test_unknown_dimension(self):
        self._expect_error("round,panelist_id,task_id,dimension,rating\n1,E1,T1,fun,4\n")

    def test_missing_column(self):
        self._expect_error("round,panelist_id,task_id,rating\n1,E1,T1,4\n")


if __name__ == "__main__":
    unittest.main()
