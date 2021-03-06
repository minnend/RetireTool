package org.minnen.retiretool.stats;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

/**
 * Holds statistics that characterize the results of an investment strategy over the full investment duration.
 * 
 * @author David Minnen
 */
public class CumulativeStats implements Comparable<CumulativeStats>
{
  public static final double                 epsCAGR           = 0.008;
  public static final double                 epsDrawdown       = 0.1;

  public Sequence                            dailyReturns;
  public Sequence                            monthlyReturns;
  public Sequence                            preferredReturns;
  public double                              cagr              = 1.0;
  public double                              meanAnnualReturn  = 1.0;
  public double                              devAnnualReturn;
  public double                              totalReturn       = 1.0;
  public double                              drawdown;
  public double                              percentNewHigh;
  public double                              percentDown10;
  public double                              peakReturn;
  public double                              percentUp;
  public double                              percentDown;
  public double[]                            annualPercentiles = new double[5];

  private static Comparator<CumulativeStats> compBasic;
  private static Comparator<CumulativeStats> compDominates;

  public static CumulativeStats calc(Sequence cumulativeReturns)
  {
    return calc(cumulativeReturns, true);
  }

  public static CumulativeStats calc(Sequence cumulativeReturns, boolean calcDurationalStats)
  {
    long timestep = cumulativeReturns.getMeanTimeStep();
    if (timestep > 25 * TimeLib.MS_IN_DAY) {
      // TODO also distinguish between monthly and annual data.
      return calc(null, cumulativeReturns, calcDurationalStats);
    } else {
      assert timestep < 3 * TimeLib.MS_IN_DAY;
      return calc(cumulativeReturns, null, calcDurationalStats);
    }
  }

  public static CumulativeStats calc(Sequence dailyReturns, Sequence monthlyReturns, boolean calcDurationalStats)
  {
    CumulativeStats stats = new CumulativeStats();
    stats.dailyReturns = dailyReturns;
    stats.monthlyReturns = monthlyReturns;

    if (dailyReturns != null && !dailyReturns.isEmpty()) {
      stats.preferredReturns = dailyReturns;
      if (monthlyReturns == null) {
        monthlyReturns = FinLib.dailyToMonthly(dailyReturns);
        stats.monthlyReturns = monthlyReturns;
      }
    } else if (monthlyReturns != null && !monthlyReturns.isEmpty()) {
      stats.preferredReturns = monthlyReturns;
    }

    // Calculate statistics based on the best data we have (daily preferred to monthly).
    if (stats.preferredReturns != null) {
      stats.totalReturn = FinLib.getTotalReturn(stats.preferredReturns);
      double nMonths = stats.preferredReturns.getLengthMonths();
      stats.cagr = FinLib.getAnnualReturn(stats.totalReturn, nMonths);
      stats.calcDrawdownStats(stats.preferredReturns);
    }

    if (calcDurationalStats && monthlyReturns != null && !monthlyReturns.isEmpty()) {
      DurationalStats rstats = DurationalStats.calc(monthlyReturns, 12);
      stats.meanAnnualReturn = rstats.mean;
      stats.devAnnualReturn = rstats.sdev;

      stats.annualPercentiles[0] = rstats.min;
      stats.annualPercentiles[1] = rstats.percentile25;
      stats.annualPercentiles[2] = rstats.median;
      stats.annualPercentiles[3] = rstats.percentile75;
      stats.annualPercentiles[4] = rstats.max;
    }

    return stats;
  }

  private void calcDrawdownStats(Sequence cumulativeReturns)
  {
    final int N = cumulativeReturns.size();
    final double eps = 1e-5;
    peakReturn = 1.0;
    drawdown = 0.0;

    if (N <= 1) return;

    final double firstValue = cumulativeReturns.getFirst(0);
    double prevValue = 1.0; // normalized first value
    int nNewHigh = 0;
    int nDown10 = 0;
    int numUp = 0;
    int numDown = 0;
    for (int i = 1; i < N; ++i) {
      double value = cumulativeReturns.get(i, 0) / firstValue;
      double change = value / prevValue - 1.0;
      prevValue = value;
      if (change > eps) {
        ++numUp;
      } else if (change < -eps) {
        ++numDown;
      }
      if (value < peakReturn) {
        double currentDrawdown = 100.0 * (peakReturn - value) / peakReturn;
        if (currentDrawdown > drawdown) {
          drawdown = currentDrawdown;
        }
        if (currentDrawdown >= 10.0) {
          ++nDown10;
        }
      } else if (value > peakReturn) {
        peakReturn = value;
        ++nNewHigh;
      }
    }

    percentNewHigh = 100.0 * nNewHigh / (N - 1);
    percentDown10 = 100.0 * nDown10 / (N - 1);
    percentUp = 100.0 * numUp / (N - 1);
    percentDown = 100.0 * numDown / (N - 1);
  }

  public double scoreSimple()
  {
    return cagr - drawdown / 10.0; // TODO improve composite score
  }

  public String name()
  {
    if (dailyReturns != null) return dailyReturns.getName();
    else if (monthlyReturns != null) return monthlyReturns.getName();
    else return "Unknown";
  }

  @Override
  public String toString()
  {
    // return String.format("[%s: CAGR=%.2f DD=%.1f DEV=%.2f %%[%.1f|%.1f|%.1f|%.1f|%.1f] Down10=%.1f]",
    // FinLib.getBaseName(name()), cagr, drawdown, devAnnualReturn, annualPercentiles[0], annualPercentiles[1],
    // annualPercentiles[2], annualPercentiles[3], annualPercentiles[4], percentDown10);

    // return String.format("[%s: CAGR=%.2f DD=%.1f Down10=%.1f]", FinLib.getBaseName(name()), cagr, drawdown,
    // percentDown10);

    // return String.format("[%s: %5.2f, %4.1f |%.2f]", FinLib.getBaseName(name()), cagr, drawdown, scoreSimple());
    // return String.format("[%s: %5.2f, %4.1f, %4.2f]", FinLib.getBaseName(name()), cagr, drawdown, devAnnualReturn);
    return String.format("[%s: %5.2f, DD=%4.1f, mean=%5.2f, dev=%5.2f, sharpe=%4.2f]", FinLib.getBaseName(name()), cagr,
        drawdown, meanAnnualReturn, devAnnualReturn, meanAnnualReturn / devAnnualReturn);
  }

  public String toRowString()
  {
    // return String.format("%50s: %.2f \tDD=%.1f \t[%.1f|%.1f|%.1f|%.1f|%.1f] \tDown10=%.1f",
    // FinLib.getBaseName(name()), cagr, drawdown, annualPercentiles[0], annualPercentiles[1], annualPercentiles[2],
    // annualPercentiles[3], annualPercentiles[4], percentDown10);

    return String.format("%50s: %.2f \tDD=%.1f  \tDown10=%.1f", FinLib.getBaseName(name()), cagr, drawdown,
        percentDown10);
  }

  /**
   * Calculates the stats for a list of cumulative returns sequences.
   * 
   * @param cumulativeReturns array of cumulative returns.
   * @return array of InvestmentStats corresponding to each input sequence.
   */
  public static CumulativeStats[] calc(Sequence... cumulativeReturns)
  {
    CumulativeStats[] stats = new CumulativeStats[cumulativeReturns.length];
    for (int i = 0; i < cumulativeReturns.length; ++i) {
      assert cumulativeReturns[i].length() == cumulativeReturns[0].length();
      stats[i] = CumulativeStats.calc(cumulativeReturns[i]);
      cumulativeReturns[i].setName(String.format("%s (%.2f%%)", cumulativeReturns[i].getName(), stats[i].cagr));
    }
    return stats;
  }

  /**
   * Conservative calculation of strategy dominance.
   * 
   * @return 0 if stats are similar else 1 if this stats object dominates and -1 if `other` dominates.
   */
  public int dominates(CumulativeStats other)
  {
    int resultCAGR = 0;
    if (cagr > other.cagr + epsCAGR) resultCAGR = 1;
    else if (other.cagr > cagr + epsCAGR) resultCAGR = -1;

    int resultDD = 0;
    if (drawdown < other.drawdown - epsDrawdown) resultDD = 1;
    else if (other.drawdown < drawdown - epsDrawdown) resultDD = -1;

    final int sum = resultCAGR + resultDD;
    if (sum > 0) return 1;
    if (sum < 0) return -1;
    return 0;
  }

  /** @return true if any of the `defenders` dominates this stats object. */
  public boolean isDominated(List<CumulativeStats> defenders)
  {
    double bestCagr = 0.0;
    double bestDrawdown = 999.0;
    for (CumulativeStats defender : defenders) {
      if (defender.dominates(this) > 0) return true;
      bestCagr = Math.max(bestCagr, defender.cagr);
      bestDrawdown = Math.min(bestDrawdown, defender.drawdown);
    }

    // Nothing dominates directly, but we still reject challenger unless it improves best CAGR or drawdown.
    // TODO is this extra criteria correct / appropriate here?
    return (this.cagr < bestCagr + epsCAGR && this.drawdown > bestDrawdown - epsDrawdown);
  }

  /**
   * Conservative calculation of strategy similarity.
   * 
   * @return true if both CAGR and DD are close.
   */
  public boolean isSimilar(CumulativeStats other)
  {
    if (this == other) return true;
    if (Math.abs(cagr - other.cagr) > epsCAGR) return false;
    if (Math.abs(drawdown - other.drawdown) > epsDrawdown) return false;
    // TODO consider other stats? median?
    return true;
  }

  /** @return Comparator that calls CumulativeStats.prefer(). */
  public static Comparator<CumulativeStats> getComparatorBasic()
  {
    if (compBasic == null) {
      compBasic = new Comparator<CumulativeStats>()
      {
        @Override
        public int compare(CumulativeStats a, CumulativeStats b)
        {
          return a.prefer(b);
        }
      };
    }
    return compBasic;
  }

  /** @return Comparator based on domination. */
  public static Comparator<CumulativeStats> getComparatorDominates()
  {
    if (compDominates == null) {
      compDominates = new Comparator<CumulativeStats>()
      {
        @Override
        public int compare(CumulativeStats a, CumulativeStats b)
        {
          int decision = a.dominates(b);
          if (decision == 0 && a.isSimilar(b)) {
            decision = a.prefer(b);
          }
          return decision;
        }
      };
    }
    return compDominates;
  }

  /** @return 1 if this is better than `other`, -1 if opposite, else 0 if too similar. */
  public int prefer(CumulativeStats other)
  {
    if (this == other) return 0;

    final double score1 = this.scoreSimple();
    final double score2 = other.scoreSimple();
    if (score1 > score2 + 0.05) return 1;
    if (score2 > score1 + 0.05) return -1;

    if (this.cagr > other.cagr + CumulativeStats.epsCAGR) return 1;
    if (other.cagr > this.cagr + CumulativeStats.epsCAGR) return -1;

    if (this.drawdown < other.drawdown - CumulativeStats.epsDrawdown) return 1;
    if (other.drawdown < this.drawdown - CumulativeStats.epsDrawdown) return -1;

    if (this.annualPercentiles[2] > other.annualPercentiles[2] + 0.1) return 1;
    if (other.annualPercentiles[2] > this.annualPercentiles[2] + 0.1) return -1;

    return 0;
  }

  @Override
  public int compareTo(CumulativeStats other)
  {
    return getComparatorBasic().compare(this, other);
  }

  public static void filter(List<CumulativeStats> stats)
  {
    final int N = stats.size();
    Comparator<CumulativeStats> filter = getComparatorDominates();

    // Compare all pairs and set dominated (or duplicate) stats to null.
    for (int i = 0; i < N; ++i) {
      CumulativeStats stats1 = stats.get(i);
      if (stats1 == null) continue;
      for (int j = i + 1; j < N; ++j) {
        CumulativeStats stats2 = stats.get(j);
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
    stats.removeIf(new Predicate<CumulativeStats>()
    {
      @Override
      public boolean test(CumulativeStats cstats)
      {
        return (cstats == null);
      }
    });

    // Sort remaining elements by simple score.
    stats.sort(getComparatorBasic());
  }
}
