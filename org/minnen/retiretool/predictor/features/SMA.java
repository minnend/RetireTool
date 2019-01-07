package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;

public class SMA extends FeatureExtractor
{
  public final int nTrigger;
  public final int nBase;
  public final int iPrice;

  public SMA(int nTrigger, int nBase, int iPrice)
  {
    super(String.format("SMA[%d,%d]", nTrigger, nBase));
    this.nTrigger = nTrigger;
    this.nBase = nBase;
    this.iPrice = iPrice;
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, int assetID)
  {
    Sequence seq = brokerAccess.getSeq(assetID);
    double sma = seq.average(-nTrigger, -nTrigger, iPrice);
    return new FeatureVec(seq.getName(), 1, sma).setTime(brokerAccess.getTime());
  }
}
