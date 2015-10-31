package org.minnen.retiretool.stats;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.WeightedValue;

/**
 * Holds statistics that characterize the results of an investment strategy over the full investment duration.
 * 
 * @author David Minnen
 */
public class CumulativeStats implements Comparable<CumulativeStats>
{
  public Sequence cumulativeReturns;
  public double   cagr              = 1.0;
  public double   meanAnnualReturn  = 1.0;
  public double   devAnnualReturn;
  public double   totalReturn       = 1.0;
  public double   drawdown;
  public double   percentNewHigh;
  public double   percentDown10;
  public double   peakReturn;
  public double   percentUp;
  public double   percentDown;
  public double[] annualPercentiles = new double[5];
  public double   leverage          = 1.0;

  public static CumulativeStats calc(Sequence cumulativeReturns)
  {
    CumulativeStats stats = new CumulativeStats();
    stats.cumulativeReturns = cumulativeReturns;

    if (cumulativeReturns != null && !cumulativeReturns.isEmpty()) {
      stats.totalReturn = cumulativeReturns.getLast(0) / cumulativeReturns.getFirst(0);
      stats.cagr = FinLib.getAnnualReturn(stats.totalReturn, cumulativeReturns.size() - 1);

      DurationalStats rstats = DurationalStats.calc(cumulativeReturns, 12);
      stats.meanAnnualReturn = rstats.mean;
      stats.devAnnualReturn = rstats.sdev;

      stats.annualPercentiles[0] = rstats.min;
      stats.annualPercentiles[1] = rstats.percentile25;
      stats.annualPercentiles[2] = rstats.median;
      stats.annualPercentiles[3] = rstats.percentile75;
      stats.annualPercentiles[4] = rstats.max;

      stats.calcDrawdownStats();
    }

    return stats;
  }

  private void calcDrawdownStats()
  {
    final int N = cumulativeReturns.size();
    final double eps = 1e-5;
    peakReturn = 1.0;
    drawdown = 0.0;

    if (N > 1) {
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
  }

  public double calcScore()
  {
    List<WeightedValue> terms = new ArrayList<WeightedValue>();
    double multiYearReturn = FinLib.mul2ret(Math.pow(FinLib.ret2mul(cagr), 10));
    terms.add(new WeightedValue(multiYearReturn, 1000));
    terms.add(new WeightedValue(devAnnualReturn, -10));
    terms.add(new WeightedValue(drawdown + 10.0, -1000));
    terms.add(new WeightedValue(percentDown10, -10));
    terms.add(new WeightedValue(percentNewHigh, 5));
    terms.add(new WeightedValue(annualPercentiles[0], 5));
    terms.add(new WeightedValue(annualPercentiles[1], 10));
    terms.add(new WeightedValue(annualPercentiles[2], 20));
    terms.add(new WeightedValue(annualPercentiles[3], 10));
    terms.add(new WeightedValue(annualPercentiles[4], 5));

    double score = 0.0;
    for (WeightedValue wv : terms) {
      score += wv.value * wv.weight;
    }
    return score / 10000.0;
  }

  public String name()
  {
    return cumulativeReturns == null ? "Unknown" : cumulativeReturns.getName();
  }

  @Override
  public String toString()
  {
    return String.format("[%s: CAGR=%.2f  DD=%.1f  DEV=%.2f  %%[%.1f|%.1f|%.1f|%.1f|%.1f]  Down10=%.1f]",
        FinLib.getBaseName(name()), cagr, drawdown, devAnnualReturn, annualPercentiles[0], annualPercentiles[1],
        annualPercentiles[2], annualPercentiles[3], annualPercentiles[4], percentDown10);
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

  public boolean dominates(CumulativeStats cstats)
  {
    final double epsCAGR = 0.008;
    final double epsDrawdown = 0.4;

    return (cagr > cstats.cagr - epsCAGR) && (drawdown < cstats.drawdown + epsDrawdown);
  }

  @Override
  public int compareTo(CumulativeStats other)
  {
    assert other != null && other instanceof CumulativeStats;
    if (this == other)
      return 0;

    if (cagr > other.cagr)
      return 1;
    if (other.cagr > cagr)
      return -1;

    if (drawdown > other.drawdown)
      return -1;
    if (other.drawdown > drawdown)
      return 1;

    if (annualPercentiles[2] > other.annualPercentiles[2])
      return 1;
    if (other.annualPercentiles[2] > annualPercentiles[2])
      return -1;

    if (percentDown10 > other.percentDown10)
      return -1;
    if (other.percentDown10 > percentDown10)
      return 1;

    return 0;
  }
}
