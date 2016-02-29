package org.minnen.retiretool.predictor.features;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;

public class BasicStats extends FeatureExtractor
{
  public enum Period {
    Daily, Weekly, Monthly
  }

  public final int    nLookback;
  public final int    iPrice;
  public final Period compoundPeriod;

  public BasicStats(int nLookback, Period compoundPeriod, int iPrice)
  {
    super(String.format("TrailingStats[%d]", nLookback));
    this.nLookback = nLookback;
    this.compoundPeriod = compoundPeriod;
    this.iPrice = iPrice;

    assert nLookback > 0;
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, String assetName)
  {
    int delay = 1;
    if (compoundPeriod == Period.Weekly) delay = 5;
    else if (compoundPeriod == Period.Monthly) delay = 20;
    else {
      assert compoundPeriod == Period.Daily;
    }

    Sequence seq = brokerAccess.getSeq(assetName);
    double[] rets = FinLib.getReturns(seq, delay, -nLookback - 1, -1, iPrice);
    ReturnStats stats = ReturnStats.calc(assetName, rets);
    // return new FeatureVec(assetName, 8, stats.mean, stats.sdev, stats.percentUp, stats.percentile10,
    // stats.percentile25, stats.median, stats.percentile75, stats.percentile90).setTime(brokerAccess.getTime());

    return new FeatureVec(assetName, 1, stats.median).setTime(brokerAccess.getTime());
  }
}
