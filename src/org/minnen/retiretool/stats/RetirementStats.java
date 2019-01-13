package org.minnen.retiretool.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import smile.math.Math;

public class RetirementStats extends ReturnStats
{
  public final double principal;

  public RetirementStats(String name, double principal, double[] returns)
  {
    super(name, returns);
    this.principal = principal;
  }

  @Override
  public int compareTo(ReturnStats other)
  {
    if (this == other) return 0;

    // TODO breaks comparison transitivity across parent/subclass!
    if (other instanceof RetirementStats) {
      RetirementStats rstats = (RetirementStats) other;
      if (principal < rstats.principal) return -1;
      if (principal > rstats.principal) return 1;
    }

    return super.compareTo(other);
  }

  public static RetirementStats[] filter(RetirementStats[] results)
  {
    Arrays.sort(results, new Comparator<RetirementStats>()
    {
      @Override
      public int compare(RetirementStats a, RetirementStats b)
      {
        if (a.principal < b.principal) return -1;
        if (a.principal > b.principal) return 1;

        if (a.percentile10 > b.percentile10) return -1;
        if (a.percentile10 > b.percentile10) return 1;

        if (a.percentile25 > b.percentile25) return -1;
        if (a.percentile25 > b.percentile25) return 1;

        return a.name.compareTo(b.name);
      }
    });
    List<RetirementStats> winners = new ArrayList<>();
    for (int i = 0; i < results.length;) {
      double maxPrincipal = Math.ceil(results[i].principal / 5000.0) * 5000.0;
      int iBest = i;
      int j = i + 1;
      while (j < results.length && results[j].principal <= maxPrincipal) {
        if (results[j].percentile10 > results[iBest].percentile10) {
          iBest = j;
        }
        ++j;
      }
      winners.add(results[iBest]);
      i = j;
    }
    return winners.toArray(new RetirementStats[winners.size()]);
  }
}
