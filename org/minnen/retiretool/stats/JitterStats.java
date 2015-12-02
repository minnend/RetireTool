package org.minnen.retiretool.stats;

import java.util.List;
import java.util.function.Predicate;

import org.minnen.retiretool.predictor.config.PredictorConfig;

public class JitterStats implements Comparable<JitterStats>
{
  public final PredictorConfig config;
  public final ReturnStats     cagr;
  public final ReturnStats     drawdown;

  public JitterStats(PredictorConfig config, ReturnStats cagr, ReturnStats drawdown)
  {
    this.config = config;
    this.cagr = cagr;
    this.drawdown = drawdown;
  }

  public void print()
  {
    System.out.println(cagr);
    System.out.println(drawdown);
  }

  @Override
  public String toString()
  {
    return String.format("[%.2f, %.2f]", cagr.percentile10, drawdown.percentile90);
  }

  public boolean dominates(JitterStats stats)
  {
    final double epsCAGR = 0.008;
    final double epsDrawdown = 0.4;

    return (cagr.percentile10 > stats.cagr.percentile10 - epsCAGR)
        && (drawdown.percentile90 < stats.drawdown.percentile90 + epsDrawdown);
  }

  @Override
  public int compareTo(JitterStats stats)
  {
    if (cagr.percentile10 > stats.cagr.percentile10) return 1;
    if (cagr.percentile10 < stats.cagr.percentile10) return -1;

    if (drawdown.percentile90 < stats.drawdown.percentile90) return 1;
    if (drawdown.percentile90 > stats.drawdown.percentile90) return -1;

    return 0;
  }

  public static void filter(List<JitterStats> stats)
  {
    final int N = stats.size();
    for (int i = 0; i < N; ++i) {
      JitterStats stats1 = stats.get(i);
      if (stats1 == null) {
        continue;
      }
      for (int j = i + 1; j < N; ++j) {
        JitterStats stats2 = stats.get(j);
        if (stats2 == null) {
          continue;
        }

        if (stats1.dominates(stats2) || stats1.compareTo(stats2) == 0) {
          stats.set(j, null);
          continue;
        }

        if (stats2.dominates(stats1)) {
          stats.set(i, null);
          break;
        }
      }
    }

    // Remove all null entries.
    stats.removeIf(new Predicate<JitterStats>()
    {
      @Override
      public boolean test(JitterStats cstats)
      {
        return (cstats == null);
      }
    });
  }
}
