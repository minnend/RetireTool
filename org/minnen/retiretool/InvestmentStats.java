package org.minnen.retiretool;

/**
 * Holds statistics that characterize the results of an investment strategy.
 * 
 * @author David Minnen
 */
public class InvestmentStats
{
  public Sequence cumulativeReturns;
  public double   cagr;
  public double   totalReturn;
  public double   maxDrawdown;
  public double   percentNewHigh;
  public double   percentDown10;
  public double   peakReturn;
  public double   percentUp;
  public double   percentDown;

  public static InvestmentStats calcInvestmentStats(Sequence cumulativeReturns)
  {
    InvestmentStats stats = new InvestmentStats();

    stats.cumulativeReturns = cumulativeReturns;
    if (cumulativeReturns == null || cumulativeReturns.isEmpty()) {
      stats.totalReturn = 1.0;
    } else {
      stats.totalReturn = cumulativeReturns.getLast(0) / cumulativeReturns.getFirst(0);
      stats.cagr = RetireTool.getAnnualReturn(stats.totalReturn, cumulativeReturns.size() - 1);
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

  public String name()
  {
    return cumulativeReturns == null ? "Unknown" : cumulativeReturns.getName();
  }

  @Override
  public String toString()
  {
    return String.format("[%s: CAGR=%.2f  MDD=%.1f  %%Up/Down=(%.1f, %.1f)  NewHigh,Down10=(%.1f, %.1f)]", name(), cagr,
        maxDrawdown, percentUp, percentDown, percentNewHigh, percentDown10);
  }
}
