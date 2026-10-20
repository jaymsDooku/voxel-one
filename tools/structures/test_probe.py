#!/usr/bin/env python3
import unittest
from run_probe import summarize, validate_trials


class ProbeReportTest(unittest.TestCase):
    def test_twenty_run_nearest_rank_p95_and_median(self):
        self.assertEqual(dict(median=10.5, p95=19, worst=20), summarize(list(range(20, 0, -1))))

    def trials(self):
        return [dict(cells=c, run=r, quads=c * 12, mesh_array_bytes=c * 12 * 216, elapsed_ms=1.0)
                for c in (1, 8, 64, 512) for r in (1, 2)]

    def test_complete_grid(self):
        validate_trials(self.trials(), 2)

    def test_duplicate_or_missing_trial(self):
        trials = self.trials()
        trials[-1] = trials[0]
        with self.assertRaisesRegex(ValueError, 'Missing or duplicate'):
            validate_trials(trials, 2)

    def test_nonfinite_timing(self):
        trials = self.trials()
        trials[0]['elapsed_ms'] = float('nan')
        with self.assertRaisesRegex(ValueError, 'Invalid probe'):
            validate_trials(trials, 2)

    def test_wrong_mesh_layout(self):
        trials = self.trials()
        trials[0]['mesh_array_bytes'] += 4
        with self.assertRaisesRegex(ValueError, 'Invalid probe'):
            validate_trials(trials, 2)


if __name__ == '__main__':
    unittest.main()
