package org.minnen.retiretool.predictor.config;

import java.util.Arrays;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;

public class ConfigMulti extends PredictorConfig
{
  public long                     assetMap;
  private final PredictorConfig[] configs;

  public ConfigMulti(long assetMap, PredictorConfig... configs)
  {
    this.assetMap = assetMap;
    this.configs = Arrays.copyOf(configs, configs.length);
  }

  public int size()
  {
    return configs.length;
  }

  @Override
  public boolean isValid()
  {
    for (int i = 0; i < configs.length; ++i) {
      if (!configs[i].isValid()) return false;
    }
    return true;
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    PredictorConfig[] perturbed = new PredictorConfig[configs.length];
    for (int i = 0; i < configs.length; ++i) {
      perturbed[i] = configs[i].genPerturbed();
    }
    return new ConfigMulti(assetMap, perturbed);
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    Predictor[] predictors = new Predictor[configs.length];
    for (int i = 0; i < configs.length; ++i) {
      predictors[i] = configs[i].build(brokerAccess, assetNames);
    }
    return new MultiPredictor(predictors, assetMap, assetNames[0], assetNames[1], brokerAccess);
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("MultiPredictor (%s)\n", assetMap));
    for (int i = 0; i < configs.length; ++i) {
      sb.append(String.format(" %s%s", configs[i], i == configs.length - 1 ? "" : "\n"));
    }
    return sb.toString();
  }
}
