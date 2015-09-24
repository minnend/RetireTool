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
  public Map<Integer, Results> durationToResults;
  public final static int[]    durations = new int[] { 1, 12, 5 * 12, 10 * 12, 20 * 12, 30 * 12 };

  private ComparisonStats()
  {
    durationToResults = new TreeMap<Integer, ComparisonStats.Results>();
  }

  public static ComparisonStats calc(Sequence cumulativeReturns1, Sequence cumulativeReturns2)
  {
    assert cumulativeReturns1.length() == cumulativeReturns2.length();
    ComparisonStats stats = new ComparisonStats();
    stats.returns1 = cumulativeReturns1;
    stats.returns2 = cumulativeReturns2;

    for (int i = 0; i < durations.length && durations[i] < cumulativeReturns1.length(); ++i) {
      final int duration = durations[i];
      stats.durationToResults.put(duration, calcResults(cumulativeReturns1, cumulativeReturns2, duration));
    }
    return stats;
  }

  public static Results calcResults(Sequence cumulativeReturns1, Sequence cumulativeReturns2, int nMonths)
  {
    assert cumulativeReturns1.length() == cumulativeReturns2.length();
    Results results = new Results();
    results.duration = nMonths;

    cumulativeReturns1 = FinLib.calcReturnsForDuration(cumulativeReturns1, nMonths);
    cumulativeReturns2 = FinLib.calcReturnsForDuration(cumulativeReturns2, nMonths);

    final int N = cumulativeReturns1.length();
    assert N > 0;
    int win1 = 0, win2 = 0;
    double excessSum = 0.0;
    double[] r = new double[N];
    for (int i = 0; i < N; ++i) {
      double diff = cumulativeReturns1.get(i, 0) - cumulativeReturns2.get(i, 0);
      r[i] = diff;
      excessSum += diff;
      if (Math.abs(diff) > 0.01) {
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
