package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;

public class Momentum extends FeatureExtractor
{
  public final int nTriggerA;
  public final int nTriggerB;
  public final int nBaseA;
  public final int nBaseB;
  public final int iPrice;

  public Momentum(int nTriggerA, int nTriggerB, int nBaseA, int nBaseB, int iPrice, BrokerInfoAccess brokerAccess)
  {
    super(String.format("Momentum[%d,%d]/[%d,%d]", nTriggerA, nTriggerB, nBaseA, nBaseB), brokerAccess);
    this.nTriggerA = nTriggerA;
    this.nTriggerB = nTriggerB;
    this.nBaseA = nBaseA;
    this.nBaseB = nBaseB;
    this.iPrice = iPrice;
  }

  @Override
  public FeatureVec calculate(String assetName)
  {
    Sequence seq = brokerAccess.getSeq(assetName);
    double now = seq.average(-nTriggerA, -nTriggerB, iPrice);
    double before = seq.average(-nBaseA, -nBaseB, iPrice);
    double momentum = now / before;
    return new FeatureVec(1, momentum).setTime(brokerAccess.getTime());
  }
}
