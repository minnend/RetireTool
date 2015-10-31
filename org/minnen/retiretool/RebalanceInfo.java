package org.minnen.retiretool;

import org.minnen.retiretool.predictor.AssetPredictor;

public class RebalanceInfo
{
  private final double[] targetPercents;
  private final int      rebalanceMonths;
  private final double   rebalanceBand;

  public RebalanceInfo(double[] targetPercents, int rebalanceMonths, double rebalanceBand)
  {
    this.targetPercents = targetPercents;
    this.rebalanceMonths = rebalanceMonths;
    this.rebalanceBand = rebalanceBand;
  }
}
