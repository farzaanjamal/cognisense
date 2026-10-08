# Simulation outputs used in the paper

Produced by `timing_simulation.py` with seed 20261004, 500 replicates of 200 simulated children each. They are regenerated exactly by:

```bash
python timing_simulation.py --reps 500 --out results/timing_simulation_main.json
python timing_simulation.py --reps 500 --gng-window 1500 --out results/timing_simulation_window1500.json
python timing_simulation.py --reps 500 --gng-window inf --out results/timing_simulation_nowindow.json
python timing_simulation.py --reps 500 --gng-go 96 --gng-nogo 32 --out results/timing_simulation_v03_counts.json
```

The first three use the Go/No-Go trial counts of config v0.2 (108 Go, 36 No-Go), as reported in the paper. The last is a robustness check with the config v0.3 design (96 Go, 32 No-Go): every agreement value changed by at most .005.

Each file records its settings: the assumed child population, the device scenarios and the response window.

Every value is the mean across replicates with its 2.5th and 97.5th percentiles. The children are simulated and their parameters are illustrative assumptions; no real person or device produced these numbers.
