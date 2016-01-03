package org.minnen.retiretool.predictor.config;

import java.util.Arrays;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.ConstPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;

public class ConfigConst extends PredictorConfig
{
  public final String assetName;

  public ConfigConst(String assetName)
  {
    this.assetName = assetName;
  }

  @Override
  public boolean isValid()
  {
    return (assetName != null);
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    return this;
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    assert Arrays.asList(assetNames).contains(assetName) : assetName;
    return new ConstPredictor(this, brokerAccess, assetName);
  }

  @Override
  public String toString()
  {
    return "Const=" + assetName;
  }
}
