package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;

public class MultiPredictor extends Predictor
{
  private final long assetMap;

  public MultiPredictor(Predictor[] predictors, long assetMap, String assetName, String alternativeAsset,
      BrokerInfoAccess brokerAccess)
  {
    super("Multiscale", brokerAccess, new String[] { assetName, alternativeAsset });
    this.predictorType = PredictorType.InOut;
    this.predictors = predictors;
    this.assetMap = assetMap;
    reset();  // child predictors may have already been used. 
  }

  @Override
  protected boolean calcInOut()
  {
    int code = 0;
    int nIn = 0;
    for (int i = 0; i < predictors.length; i++) {
      code <<= 1;
      if (predictors[i].calcInOut()) {
        ++code;
        ++nIn;
      }
    }

    if (assetMap < 0) {
      return (nIn > predictors.length / 2);
    } else {
      return ((assetMap >> code) & 1L) > 0;
    }
  }
}
