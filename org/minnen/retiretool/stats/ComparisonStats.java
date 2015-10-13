package org.minnen.retiretool.stats;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.data.Sequence;

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
  };

  public Sequence              returns1, returns2;
  public double                targetReturn;
  public Sequence[]            defenders;
  public Map<Integer, Results> durationToResults;
  public final static int[]    durations = new int[] { 1, 12, 5 * 12, 10 * 12, 15*12, 20 * 12, 30 * 12 };

  private ComparisonStats()
  {
    durationToResults = new TreeMap<Integer, ComparisonStats.Results>();
    targetReturn = Double.NaN;
  }

  public static ComparisonStats calc(Sequence cumulativeReturns, double diffMargin, Sequence... defenders)
  {
    assert cumulativeReturns.length() == defenders[0].length();
    ComparisonStats stats = new ComparisonStats();
    stats.returns1 = cumulativeReturns;
    stats.defenders = defenders;

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

    Sequence returns1 = FinLib.calcReturnsForDuration(cumulativeReturns1, nMonths);
    Sequence returns2 = FinLib.calcReturnsForDuration(cumulativeReturns2, nMonths);

    return calcFromDurationReturns(returns1, returns2, nMonths, diffMargin);
  }

  public static Results calcFromCumulative(Sequence cumulativeReturns, Sequence[] defenders, int nMonths,
      double diffMargin)
  {
    assert cumulativeReturns.length() == defenders[0].length();
    Sequence returns1 = FinLib.calcReturnsForDuration(cumulativeReturns, nMonths);
    Sequence returns2 = FinLib.calcReturnsForDuration(defenders[0], nMonths);

    for (int i = 1; i < defenders.length; ++i) {
      assert cumulativeReturns.length() == defenders[i].length();
      returns2._max(FinLib.calcReturnsForDuration(defenders[i], nMonths));
    }

    return calcFromDurationReturns(returns1, returns2, nMonths, diffMargin);
  }

  public static Results calcFromCumulative(Sequence cumulativeReturns, double targetReturn, int nMonths,
      double diffMargin)
  {
    Sequence returns = FinLib.calcReturnsForDuration(cumulativeReturns, nMonths);
    return calcFromDurationReturns(returns, targetReturn, nMonths, diffMargin);
  }

  public static Results calcFromDurationReturns(Sequence returnsForDuration1, Sequence returnsForDuration2,
      int nMonths, double diffMargin)
  {
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
