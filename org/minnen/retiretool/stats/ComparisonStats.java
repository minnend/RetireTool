package org.minnen.retiretool.stats;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;

/** Calculates statistics comparing two strategies using their cumulative returns. */
public class ComparisonStats
{
  public static class Results
  {
    public int    duration;
    public double winPercent1;
    public double winPercent2;
    public double meanExcess;
    public double worstExcess;
    public double medianExcess;
    public double bestExcess;

    public double getWinPercent(int i)
    {
      if (i == 0) return winPercent1;
      else if (i == 1) return winPercent2;
      else {
        throw new IllegalArgumentException(String.format("Only indices {0,1} are supported, not %d", i));
      }
    }
  };

  public Sequence                            returns1, returns2;                                                    // monthly
                                                                                                                    // returns
  public double                              targetReturn;
  public Sequence[]                          defenders;
  public Map<Integer, Results>               durationToResults;
  public final static int[]                  durations = new int[] { 1, 12, 2 * 12, 3 * 12, 4 * 12, 5 * 12, 10 * 12,
      15 * 12, 20 * 12, 30 * 12 };

  private static Comparator<ComparisonStats> compBasic;
  private static Comparator<ComparisonStats> compDominates;

  private ComparisonStats()
  {
    durationToResults = new TreeMap<Integer, ComparisonStats.Results>();
    targetReturn = Double.NaN;
  }

  public static ComparisonStats calc(Sequence cumulativeReturns, double diffMargin, Sequence... defenders)
  {
    assert cumulativeReturns.length() == defenders[0].length() : String.format("%d vs. %d", cumulativeReturns.length(),
        defenders[0].length());
    ComparisonStats stats = new ComparisonStats();
    stats.returns1 = cumulativeReturns;
    stats.defenders = defenders;

    // Calculate comparison stats for each duration.
    for (int i = 0; i < durations.length && durations[i] < cumulativeReturns.length(); ++i) {
      final int duration = durations[i];
      stats.durationToResults.put(duration, calcFromCumulative(cumulativeReturns, defenders, duration, diffMargin));
    }
    return stats;
  }

  public static ComparisonStats calc(Sequence monthlyReturns1, Sequence monthlyReturns2, double diffMargin)
  {
    assert monthlyReturns1.length() == monthlyReturns2.length();
    ComparisonStats stats = new ComparisonStats();
    stats.returns1 = monthlyReturns1;
    stats.returns2 = monthlyReturns2;

    for (int i = 0; i < durations.length && durations[i] < monthlyReturns1.length(); ++i) {
      final int duration = durations[i];
      stats.durationToResults.put(duration, calcFromCumulative(monthlyReturns1, monthlyReturns2, duration, diffMargin));
    }
    return stats;
  }

  public static ComparisonStats calc(Sequence cumulativeReturns, double targetReturn, double diffMargin)
  {
    ComparisonStats stats = new ComparisonStats();
    stats.returns1 = cumulativeReturns;
    stats.returns2 = null;
    stats.targetReturn = targetReturn;

    for (int i = 0; i < durations.length && durations[i] < cumulativeReturns.length(); ++i) {
      final int duration = durations[i];
      stats.durationToResults.put(duration,
          calcFromCumulative(cumulativeReturns, targetReturn, durations[i], diffMargin));
    }
    return stats;
  }

  private static Results calcFromCumulative(Sequence monthlyReturns1, Sequence monthlyReturns2, int nMonths,
      double diffMargin)
  {
    assert monthlyReturns1.length() == monthlyReturns2.length();

    Sequence returns1 = FinLib.calcReturnsForMonths(monthlyReturns1, nMonths);
    Sequence returns2 = FinLib.calcReturnsForMonths(monthlyReturns2, nMonths);

    return calcFromDurationReturns(returns1, returns2, nMonths, diffMargin);
  }

  private static Results calcFromCumulative(Sequence cumulativeReturns, Sequence[] defenders, int nMonths,
      double diffMargin)
  {
    Sequence returns1 = FinLib.calcReturnsForMonths(cumulativeReturns, nMonths);
    Sequence returns2 = null;
    for (int i = 0; i < defenders.length; ++i) {
      if (cumulativeReturns == defenders[i]) continue;
      assert cumulativeReturns.length() == defenders[i].length();
      Sequence returns = FinLib.calcReturnsForMonths(defenders[i], nMonths);
      if (returns2 == null) {
        returns2 = returns;
      } else {
        returns2._max(returns); // keep the best returns for any defender
      }
    }
    return calcFromDurationReturns(returns1, returns2, nMonths, diffMargin);
  }

  private static Results calcFromCumulative(Sequence cumulativeReturns, double targetReturn, int nMonths,
      double diffMargin)
  {
    Sequence returns = FinLib.calcReturnsForMonths(cumulativeReturns, nMonths);
    return calcFromDurationReturns(returns, targetReturn, nMonths, diffMargin);
  }

  private static Results calcFromDurationReturns(Sequence returnsForDuration1, Sequence returnsForDuration2,
      int nMonths, double diffMargin)
  {
    if (returnsForDuration1 == null || returnsForDuration2 == null) return null;
    assert returnsForDuration1.length() == returnsForDuration2.length();
    Results results = new Results();
    results.duration = nMonths;

    final int N = returnsForDuration1.length();
    assert N > 0;
    int win1 = 0, win2 = 0;
    double excessSum = 0.0;
    double[] r = new double[N];
    for (int i = 0; i < N; ++i) {
      double diff = returnsForDuration1.get(i, 0) - returnsForDuration2.get(i, 0);
      r[i] = diff;
      excessSum += diff;
      if (Math.abs(diff) > diffMargin) {
        if (diff > 0.0) {
          ++win1;
        } else {
          ++win2;
        }
      }
    }

    Arrays.sort(r);
    results.meanExcess = excessSum / N;
    results.winPercent1 = 100.0 * win1 / N;
    results.winPercent2 = 100.0 * win2 / N;
    results.worstExcess = r[0];
    results.medianExcess = r[Math.min(Math.round(N * 0.5f), N - 1)];
    results.bestExcess = r[N - 1];
    return results;
  }

  private static Results calcFromDurationReturns(Sequence returnsForDuration, double targetReturn, int nMonths,
      double diffMargin)
  {
    Results results = new Results();
    results.duration = nMonths;

    // Target return is annual so adjust for durations less than one year.
    if (nMonths < 12) {
      double multiplier = FinLib.ret2mul(targetReturn);
      targetReturn = Math.pow(multiplier, nMonths / 12.0);
    }

    final int N = returnsForDuration.length();
    assert N > 0;
    int win1 = 0, win2 = 0;
    double excessSum = 0.0;
    double[] r = new double[N];
    for (int i = 0; i < N; ++i) {
      double diff = returnsForDuration.get(i, 0) - targetReturn;
      r[i] = diff;
      excessSum += diff;
      if (Math.abs(diff) > diffMargin) {
        if (diff > 0.0) {
          ++win1;
        } else {
          ++win2;
        }
      }
    }

    Arrays.sort(r);
    results.meanExcess = excessSum / N;
    results.winPercent1 = 100.0 * win1 / N;
    results.winPercent2 = 100.0 * win2 / N;
    results.worstExcess = r[0];
    results.medianExcess = r[Math.min(Math.round(N * 0.5f), N - 1)];
    results.bestExcess = r[N - 1];

    return results;
  }

  /** @return score based on 5, 10, and 20-year win percent. */
  public double score()
  {
    Results r5 = durationToResults.get(60);
    Results r10 = durationToResults.get(120);
    Results r20 = durationToResults.get(240);

    double score5 = r5.winPercent1 - r5.winPercent2 * 10;
    double score10 = r10.winPercent1 - r10.winPercent2 * 10;
    double score20 = r20.winPercent1 - r20.winPercent2 * 10;

    return score5 + 4 * score10 + 10 * score20;
  }

  /** @return 1 if this is better than `other`, -1 if opposite, else 0 if too similar. */
  public int prefer(ComparisonStats other)
  {
    if (this == other) return 0;

    final double scoreA = this.score();
    final double scoreB = other.score();
    if (scoreA > scoreB) return 1;
    if (scoreB > scoreA) return -1;
    return 0;
  }

  public int dominates(ComparisonStats other)
  {
    Results a5 = this.durationToResults.get(60);
    Results a10 = this.durationToResults.get(120);
    Results a20 = this.durationToResults.get(240);

    Results b5 = other.durationToResults.get(60);
    Results b10 = other.durationToResults.get(120);
    Results b20 = other.durationToResults.get(240);

    double[] diffs = new double[] { a5.winPercent1 - b5.winPercent1, b5.winPercent2 - a5.winPercent2,
        a10.winPercent1 - b10.winPercent1, b10.winPercent2 - a10.winPercent2, a20.winPercent1 - b20.winPercent1,
        b20.winPercent2 - a20.winPercent2 };

    int nWins = 0;
    int nLoss = 0;
    for (double x : diffs) {
      if (Math.abs(x) < 0.1) continue;
      if (x > 0) ++nWins;
      if (x < 0) ++nLoss;
    }

    if (nWins > 0 && nLoss == 0) return 1;
    if (nWins == 0 && nLoss > 0) return -1;
    return 0;
  }

  /** @return Comparator based on 5, 10, and 20-year win percent. */
  public static Comparator<ComparisonStats> getComparatorBasic()
  {
    if (compBasic == null) {
      compBasic = new Comparator<ComparisonStats>()
      {
        @Override
        public int compare(ComparisonStats a, ComparisonStats b)
        {
          return a.prefer(b);
        }
      };
    }
    return compBasic;
  }

  /** @return Comparator based on 5, 10, and 20-year win percent. */
  public static Comparator<ComparisonStats> getComparatorDominates()
  {
    if (compDominates == null) {
      compDominates = new Comparator<ComparisonStats>()
      {
        @Override
        public int compare(ComparisonStats a, ComparisonStats b)
        {
          return a.dominates(b);
        }
      };
    }
    return compDominates;
  }
}
