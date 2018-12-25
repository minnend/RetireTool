package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.predictor.features.Momentum.CompoundPeriod;
import org.minnen.retiretool.predictor.features.Momentum.ReturnOrMul;

public class RiskAdjustedReturn extends FeatureExtractor
{
  public final int      nLookback;
  public final double   addDev;
  public final int      iPrice;
  public final Momentum momentum;
  public final StdDev   sdev;

  public RiskAdjustedReturn(int nLookback, double addDev, int iPrice)
  {
    super("RiskAdjustedReturn");
    this.nLookback = nLookback;
    this.addDev = addDev;
    this.iPrice = iPrice;
    momentum = new Momentum(20, 1, nLookback, nLookback - 40, ReturnOrMul.Return, CompoundPeriod.Total, iPrice);
    sdev = new StdDev(nLookback, 1.0, iPrice);
  }

  @Override
  public int size()
  {
    return 3;
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, int assetID)
  {
    double tr = momentum.calculate(brokerAccess, assetID).get(0);
    double dev = sdev.calculate(brokerAccess, assetID).get(0);
    double rar = tr / (dev + addDev);
    return new FeatureVec(brokerAccess.getName(assetID), 3, rar, tr, dev).setTime(brokerAccess.getTime());
  }
}
