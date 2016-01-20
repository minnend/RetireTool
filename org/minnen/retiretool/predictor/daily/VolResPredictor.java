package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

/**
 * Implementation of Volatility-Responsive Asset Allocation from Russell Investments.
 * http://www.russell.com/us/institutional-investors/research/volatility-responsive-asset-allocation.page
 */
public class VolResPredictor extends Predictor
{
  private DiscreteDistribution prevDistribution = null;
  public final String          assetRisky;
  public final String          assetSafe;

  public VolResPredictor(String assetRisky, String assetSafe, BrokerInfoAccess brokerAccess)
  {
    super("VolatilityResponsive", brokerAccess, assetRisky, assetSafe);
    this.predictorType = PredictorType.Distribution;
    this.assetRisky = assetRisky;
    this.assetSafe = assetSafe;
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    if (prevDistribution != null && !brokerAccess.getTimeInfo().isLastDayOfMonth) {
      distribution.copyFrom(prevDistribution);
      return;
    }

    distribution.clear();

    double[] rets = getReturns(assetRisky, 60, 0);
    double sdev = Library.stdev(rets) * Math.sqrt(252);

    final double tv5 = 7.8;
    final double tv25 = 10.3;
    final double tv75 = 16.9;
    final double tv95 = 28.1;

    double pctRisky = 50.0;
    if (sdev > tv75) {
      pctRisky = Math.max(50.0 - (sdev - tv75) / (tv95 - tv75) * 30.0, 20.0);
    } else if (sdev < tv25) {
      pctRisky = Math.min(50.0 + (tv25 - sdev) / (tv25 - tv5) * 30.0, 80.0);
    }

    double fracRisky = pctRisky / 100.0;
    distribution.set(assetRisky, fracRisky);
    distribution.set(assetSafe, 1.0 - fracRisky);
    if (prevDistribution == null) {
      prevDistribution = new DiscreteDistribution(distribution);
    } else {
      prevDistribution.copyFrom(distribution);
    }
  }
}
