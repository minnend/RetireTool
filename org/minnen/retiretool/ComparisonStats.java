package org.minnen.retiretool;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

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

  public static ComparisonStats calc(Sequence returns1, Sequence returns2)
  {
    ComparisonStats stats = new ComparisonStats();
    stats.returns1 = returns1;
    stats.returns2 = returns2;

    for (int i = 0; i < durations.length; ++i) {
      int duration = durations[i];
      stats.durationToResults.put(duration, calcResults(returns1, returns2, duration));
    }
    return stats;
  }

  private static Results calcResults(Sequence returns1, Sequence returns2, int nMonths)
  {
    assert returns1.length() == returns2.length();
    Results results = new Results();
    results.duration = nMonths;

    returns1 = RetireTool.calcReturnsForDuration(returns1, nMonths);
    returns2 = RetireTool.calcReturnsForDuration(returns2, nMonths);

    final int N = returns1.length();
    int win1 = 0, win2 = 0;
    double excessSum = 0.0;
    double[] r = new double[N];
    for (int i = 0; i < N; ++i) {
      double diff = returns1.get(i, 0) - returns2.get(i, 0);
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
    ;
    results.meanExcess = excessSum / N;
    results.winPercent1 = 100.0 * win1 / N;
    results.winPercent2 = 100.0 * win2 / N;
    results.worstExcess = r[0];
    results.medianExcess = r[Math.round(N * 0.5f)];
    results.bestExcess = r[N - 1];

    return results;
  }
}
