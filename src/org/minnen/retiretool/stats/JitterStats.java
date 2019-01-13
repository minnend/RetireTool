package org.minnen.retiretool.stats;

import java.util.List;
import java.util.function.Predicate;

import org.minnen.retiretool.predictor.config.PredictorConfig;

public class JitterStats implements Comparable<JitterStats>
{
  public final PredictorConfig config;
  public final ReturnStats     cagrStats;
  public final ReturnStats     drawdownStats;
  public final double          cagr;
  public final double          drawdown;

  public JitterStats(PredictorConfig config, ReturnStats cagr, ReturnStats drawdown)
  {
    this.config = config;
    this.cagrStats = cagr;
    this.drawdownStats = drawdown;
    this.cagr = cagrStats.percentile10;
    this.drawdown = drawdownStats.percentile90;
  }

  public JitterStats(PredictorConfig config, double cagr, double drawdown)
  {
    this.config = config;
    this.cagr = cagr;
    this.drawdown = drawdown;
    this.cagrStats = null;
    this.drawdownStats = null;
  }

  public void print()
  {
    if (cagrStats == null) {
      System.out.println(this);
    } else {
      System.out.println(cagrStats);
      System.out.println(drawdownStats);
    }
  }

  @Override
  public String toString()
  {
    return String.format("[%.2f, %.2f]", cagr, drawdown);
  }

  public boolean dominates(JitterStats stats)
  {
    final double epsCAGR = 0.008;
    final double epsDrawdown = 0.4;

    return (cagr > stats.cagr - epsCAGR) && (drawdown < stats.drawdown + epsDrawdown);
  }

  @Override
  public int compareTo(JitterStats stats)
  {
    // if (cagr > stats.cagr) return 1;
    // if (cagr < stats.cagr) return -1;
    //
    // if (drawdown < stats.drawdown) return 1;
    // if (drawdown > stats.drawdown) return -1;

    // return 0;
    if (score() > stats.score()) return 1;
    if (score() < stats.score()) return -1;
    return 0;
  }

  public double score()
  {
    return cagr - drawdown / 20.0;
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
