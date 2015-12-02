package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.config.ConfigConst;

public class ConstPredictor extends Predictor
{
  public final int iPredict;

  public ConstPredictor(ConfigConst config, BrokerInfoAccess brokerAccess, String[] assetChoices)
  {
    super("Const:" + assetChoices[config.iPredict], brokerAccess, assetChoices);
    this.iPredict = config.iPredict;
    this.predictorType = PredictorType.SelectOne;
  }

  @Override
  protected int calcSelectOne()
  {
    return iPredict;
  }
}
