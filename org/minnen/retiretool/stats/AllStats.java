package org.minnen.retiretool.stats;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.minnen.retiretool.predictor.config.PredictorConfig;

public class AllStats
{
  public CumulativeStats                    cumulative;
  public final Map<String, ComparisonStats> comparisons = new HashMap<>();
  public PredictorConfig                    config;

  /** @return true if any of the `defenders` dominates this stats object. */
  public boolean isBeaten(List<AllStats> defenders, Comparator<AllStats> comp)
  {
    for (AllStats defender : defenders) {
      if (comp.compare(defender, this) > 0) return true;
    }
    return false;
  }

  @Override
  public String toString()
  {
    if (comparisons.isEmpty()) {
      return String.format("[%5.2f, %4.1f]", cumulative.cagr, cumulative.drawdown);
    } else {
      // TODO(dminnen) provide API for selecting which entry to use.
      Map.Entry<String, ComparisonStats> entry = comparisons.entrySet().iterator().next();
      ComparisonStats compare = entry.getValue();
      ComparisonStats.Results r5 = compare.durationToResults.get(60); // 5 years
      ComparisonStats.Results r10 = compare.durationToResults.get(120); // 10 years
      ComparisonStats.Results r20 = compare.durationToResults.get(240); // 20 years
      return String.format("[%5.2f, %4.1f | (5=%3.1f,%3.1f; 10=%3.1f,%3.1f; 20=%3.1f,%3.1f)]", cumulative.cagr,
          cumulative.drawdown, r5.winPercent1, r5.winPercent2, r10.winPercent1, r10.winPercent2, r20.winPercent1,
          r20.winPercent2);
    }
  }

  /** @return Comparator based on cumulative stats. */
  public static Comparator<AllStats> getCompare(Comparator<CumulativeStats> comp)
  {
    return new Comparator<AllStats>()
    {
      @Override
      public int compare(AllStats a, AllStats b)
      {
        return comp.compare(a.cumulative, b.cumulative);
      }
    };
  }

  /** @return Comparator based on cumulative stats. */
  public static Comparator<AllStats> getCompare(Comparator<ComparisonStats> comp, String baselineName)
  {
    return new Comparator<AllStats>()
    {
      @Override
      public int compare(AllStats a, AllStats b)
      {
        ComparisonStats x = a.comparisons.get(baselineName);
        ComparisonStats y = b.comparisons.get(baselineName);
        return comp.compare(x, y);
      }
    };
  }

  public static void filter(List<AllStats> stats, Comparator<AllStats> filter)
  {
    final int N = stats.size();

    // Compare all pairs and set dominated (or duplicate) stats to null.
    for (int i = 0; i < N; ++i) {
      AllStats stats1 = stats.get(i);
      if (stats1 == null) continue;
      for (int j = i + 1; j < N; ++j) {
        AllStats stats2 = stats.get(j);
        if (stats2 == null) continue;

        final int decision = filter.compare(stats1, stats2);
        if (decision > 0) {
          stats.set(j, null);
        } else if (decision < 0) {
          stats.set(i, null);
          break;
        }
      }
    }

    // Remove all null entries.
    stats.removeIf(new Predicate<AllStats>()
    {
      @Override
      public boolean test(AllStats x)
      {
        return (x == null);
      }
    });

    // Sort remaining elements by simple score.
    stats.sort(getCompare(CumulativeStats.getComparatorBasic()));
  }
}
