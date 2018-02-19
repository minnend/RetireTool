package org.minnen.retiretool.predictor.config;

import java.util.Arrays;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.ConstPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;

/** Configuration for a "predictor" that always returns the same asset. */
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

  /** @return list of configs matching the list of asset names. */
  public static ConfigConst[] wrap(String... assetNames)
  {
    ConfigConst[] configs = new ConfigConst[assetNames.length];
    for (int i = 0; i < assetNames.length; ++i) {
      configs[i] = new ConfigConst(assetNames[i]);
    }
    return configs;
  }
}
