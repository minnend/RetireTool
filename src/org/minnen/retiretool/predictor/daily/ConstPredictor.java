package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.config.ConfigConst;

public class ConstPredictor extends Predictor
{
  public final String assetName;

  public ConstPredictor(ConfigConst config, BrokerInfoAccess brokerAccess, String assetName)
  {
    super(assetName, brokerAccess, assetName);
    this.assetName = assetName;
    this.predictorType = PredictorType.SelectOne;
  }

  @Override
  protected String calcSelectOne()
  {
    return assetName;
  }
}
