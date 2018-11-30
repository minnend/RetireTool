package org.minnen.retiretool.stats;

import java.util.Arrays;
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

  public Sequence              returns1, returns2;
  public double                targetReturn;
  public Sequence[]            defenders;
  public Map<Integer, Results> durationToResults;
  public final static int[]    durations = new int[] { 1, 12, 2 * 12, 3 * 12, 4 * 12, 5 * 12, 10 * 12, 15 * 12, 20 * 12,
      30 * 12 };

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

  public static ComparisonStats calc(Sequence cumulativeReturns1, Sequence cumulativeReturns2, double diffMargin)
  {
    assert cumulativeReturns1.length() == cumulativeReturns2.length();
    ComparisonStats stats = new ComparisonStats();
    stats.returns1 = cumulativeReturns1;
    stats.returns2 = cumulativeReturns2;

    for (int i = 0; i < durations.length && durations[i] < cumulativeReturns1.length(); ++i) {
      final int duration = durations[i];
      stats.durationToResults.put(duration,
          calcFromCumulative(cumulativeReturns1, cumulativeReturns2, duration, diffMargin));
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

  public static Results calcFromCumulative(Sequence cumulativeReturns1, Sequence cumulativeReturns2, int nMonths,
      double diffMargin)
  {
    assert cumulativeReturns1.length() == cumulativeReturns2.length();

    Sequence returns1 = FinLib.calcReturnsForMonths(cumulativeReturns1, nMonths);
    Sequence returns2 = FinLib.calcReturnsForMonths(cumulativeReturns2, nMonths);

    return calcFromDurationReturns(returns1, returns2, nMonths, diffMargin);
  }

  public static Results calcFromCumulative(Sequence cumulativeReturns, Sequence[] defenders, int nMonths,
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

  public static Results calcFromCumulative(Sequence cumulativeReturns, double targetReturn, int nMonths,
      double diffMargin)
  {
    Sequence returns = FinLib.calcReturnsForMonths(cumulativeReturns, nMonths);
    return calcFromDurationReturns(returns, targetReturn, nMonths, diffMargin);
  }

  public static Results calcFromDurationReturns(Sequence returnsForDuration1, Sequence returnsForDuration2, int nMonths,
      double diffMargin)
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

  public static Results calcFromDurationReturns(Sequence returnsForDuration, double targetReturn, int nMonths,
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
}
