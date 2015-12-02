package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.ConstPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;

public class ConfigConst extends PredictorConfig
{
  public final int iPredict;

  public ConfigConst(int iPredict)
  {
    this.iPredict = iPredict;
  }

  @Override
  public boolean isValid()
  {
    return (iPredict >= 0);
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    return this;
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    return new ConstPredictor(this, brokerAccess, assetNames);
  }

  @Override
  public String toString()
  {
    return String.format("Const=%d", iPredict);
  }
}
