package org.minnen.retiretool;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds statistics that characterize the results of an investment strategy.
 * 
 * @author David Minnen
 */
public class InvestmentStats
{
  public Sequence cumulativeReturns;
  public double   cagr             = 1.0;
  public double   meanAnnualReturn = 1.0;
  public double   devAnnualReturn  = 0.0;
  public double   totalReturn      = 1.0;
  public double   maxDrawdown;
  public double   percentNewHigh;
  public double   percentDown10;
  public double   peakReturn;
  public double   percentUp;
  public double   percentDown;
  public double[] annualPercentiles;

  public static InvestmentStats calcInvestmentStats(Sequence cumulativeReturns)
  {
    InvestmentStats stats = new InvestmentStats();

    stats.cumulativeReturns = cumulativeReturns;
    if (cumulativeReturns != null && !cumulativeReturns.isEmpty()) {
      stats.totalReturn = cumulativeReturns.getLast(0) / cumulativeReturns.getFirst(0);
      stats.cagr = RetireTool.getAnnualReturn(stats.totalReturn, cumulativeReturns.size() - 1);

      ReturnStats rstats = new ReturnStats(cumulativeReturns, 12);
      stats.meanAnnualReturn = rstats.mean;
      stats.devAnnualReturn = rstats.sdev;

      stats.annualPercentiles = new double[5];
      stats.annualPercentiles[0] = rstats.min;
      stats.annualPercentiles[1] = rstats.percentile25;
      stats.annualPercentiles[2] = rstats.median;
      stats.annualPercentiles[3] = rstats.percentile75;
      stats.annualPercentiles[4] = rstats.max;
    }
    stats.calcDrawdownStats();
    return stats;
  }

  private void calcDrawdownStats()
  {
    final int N = cumulativeReturns.size();
    final double eps = 1e-5;
    peakReturn = 1.0;
    maxDrawdown = 0.0;

    if (N > 1) {
      final double firstValue = cumulativeReturns.getFirst(0);
      double prevValue = firstValue;
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
          double drawdown = 100.0 * (peakReturn - value) / peakReturn;
          if (drawdown > maxDrawdown) {
            maxDrawdown = drawdown;
          }
          if (drawdown >= 10.0) {
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

  public static class WeightedValue
  {
    public double value, weight;

    public WeightedValue(double value, double weight)
    {
      this.value = value;
      this.weight = weight;
    }
  }

  public double calcScore()
  {
    List<WeightedValue> terms = new ArrayList<WeightedValue>();
    double multiYearReturn = RetireTool.mul2ret(Math.pow(RetireTool.ret2mul(cagr), 10));
    terms.add(new WeightedValue(multiYearReturn, 1000));
    terms.add(new WeightedValue(devAnnualReturn, -10));
    terms.add(new WeightedValue(maxDrawdown + 10.0, -1000));
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
    return String
        .format(
            "[%s: CAGR=%.2f  DAR=%.2f  MDD=%.1f  %%[%.1f|%.1f|%.1f|%.1f|%.1f]  %%Up/Down=(%.1f, %.1f)  NewHigh,Down10=(%.1f, %.1f)]",
            name(), cagr, devAnnualReturn, maxDrawdown, annualPercentiles[0], annualPercentiles[1],
            annualPercentiles[2], annualPercentiles[3], annualPercentiles[4], percentUp, percentDown, percentNewHigh,
            percentDown10);
  }
}
