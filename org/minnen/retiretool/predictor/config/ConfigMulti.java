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
    super(configs[0].iPredictIn, configs[0].iPredictOut);
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
    // Make sure basde configs are valid.
    for (int i = 0; i < configs.length; ++i) {
      if (!configs[i].isValid()) return false;
    }

    // Make sure all base configs have the same in/out values.
    for (int i = 1; i < configs.length; ++i) {
      assert configs[i].iPredictIn == configs[0].iPredictIn;
      assert configs[i].iPredictOut == configs[0].iPredictOut;
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

    return new MultiPredictor(predictors, assetMap, assetNames[iPredictIn], assetNames[iPredictOut], brokerAccess);
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
