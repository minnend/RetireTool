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
  public double   percentDrawndown;
  public double   peakReturn;

  public static InvestmentStats calcInvestmentStats(Sequence cumulativeReturns)
  {
    InvestmentStats stats = new InvestmentStats();

    stats.cumulativeReturns = cumulativeReturns;
    if (cumulativeReturns == null || cumulativeReturns.isEmpty()) {
      stats.totalReturn = 1.0;
      stats.cagr = 0.0;
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
    peakReturn = 1.0;
    maxDrawdown = 0.0;

    if (N <= 1) {
      percentDrawndown = 0.0;
    } else {
      int nDown = 0;
      for (int i = 1; i < N; ++i) {
        double value = cumulativeReturns.get(i, 0) / cumulativeReturns.getFirst(0);
        if (value < peakReturn) {
          ++nDown;
          double drawdown = 100.0 * (peakReturn - value) / peakReturn;
          if (drawdown > maxDrawdown) {
            maxDrawdown = drawdown;
          }
        } else {
          peakReturn = value;
        }
      }
      percentDrawndown = 100.0 * nDown / (N - 1);
    }
  }
}
