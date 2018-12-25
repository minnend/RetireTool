package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;

public class StdDev extends FeatureExtractor
{
  public final double K;
  public final int    nLookback;
  public final int    iPrice;

  public StdDev(int nLookback, double K, int iPrice)
  {
    super("StdDev");
    this.nLookback = nLookback;
    this.K = K;
    this.iPrice = iPrice;
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, int assetID)
  {
    Sequence seq = brokerAccess.getSeq(assetID);
    double[] rets = FinLib.getDailyReturns(seq, -nLookback, -1, iPrice);
    double sdev = Library.stdev(rets) * Math.sqrt(252);
    return new FeatureVec(seq.getName(), 1, sdev);
  }
}
