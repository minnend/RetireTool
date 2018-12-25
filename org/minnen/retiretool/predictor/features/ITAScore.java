package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.predictor.features.Momentum.CompoundPeriod;
import org.minnen.retiretool.predictor.features.Momentum.ReturnOrMul;

public class ITAScore extends FeatureExtractor
{
  // Described here: http://itawealth.com/2015/04/20/deciphering-the-dual-momentum-model/
  public final int      iPrice;
  public final Momentum mom60, mom120, mom240;
  public final StdDev   sdev;

  public ITAScore(int iPrice)
  {
    super("ITA");
    this.iPrice = iPrice;
    mom240 = new Momentum(20, 1, 240, 220, ReturnOrMul.Return, CompoundPeriod.Total, iPrice);
    mom120 = new Momentum(20, 1, 120, 100, ReturnOrMul.Return, CompoundPeriod.Total, iPrice);
    mom60 = new Momentum(20, 1, 60, 40, ReturnOrMul.Return, CompoundPeriod.Total, iPrice);
    sdev = new StdDev(40, 1.0, iPrice);
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, int assetID)
  {
    double m60 = mom60.calculate(brokerAccess, assetID).get(0);
    double m120 = mom120.calculate(brokerAccess, assetID).get(0);
    double m240 = mom240.calculate(brokerAccess, assetID).get(0);
    double dev = sdev.calculate(brokerAccess, assetID).get(0);
    double score = 0.7 * m60 + 0.5 * m120 + 0.1 * m240 - 0.3 * dev;
    return new FeatureVec(brokerAccess.getName(assetID), 1, score).setTime(brokerAccess.getTime());
  }
}
