package org.minnen.retiretool;

public class RebalanceInfo
{
  public double[] targetPercents;
  public int      nMonths;
  public double   band;
  public int      nRebalances;

  public RebalanceInfo(int[] targetPercents, int rebalanceMonths, double rebalanceBand)
  {
    this.targetPercents = new double[targetPercents.length];
    this.nMonths = rebalanceMonths;
    this.band = rebalanceBand;

    int sum = Library.sum(targetPercents);
    assert sum > 0;
    for (int i = 0; i < targetPercents.length; ++i) {
      this.targetPercents[i] = (double) targetPercents[i] / sum;
    }
  }

  public void setPolicy(int rebalanceMonths, double rebalanceBand)
  {
    this.nMonths = rebalanceMonths;
    this.band = rebalanceBand;
  }
}
