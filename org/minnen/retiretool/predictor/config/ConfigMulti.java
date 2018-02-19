package org.minnen.retiretool.predictor.config;

import java.util.Arrays;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.TimeLib;

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
    // Make sure base configs are valid.
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

  public static ConfigMulti buildTactical(int iPrice, int iPredictIn, int iPredictOut)
  {
    final long assetMap = 254;
    final long gap = 2 * TimeLib.MS_IN_DAY;
    PredictorConfig[] tacticalConfigs = new PredictorConfig[] {
        new ConfigSMA(20, 0, 240, 150, 0.25, iPrice, gap, iPredictIn, iPredictOut),
        new ConfigSMA(50, 0, 180, 30, 1.0, iPrice, gap, iPredictIn, iPredictOut),
        new ConfigSMA(10, 0, 220, 0, 2.0, iPrice, gap, iPredictIn, iPredictOut), };
    return new ConfigMulti(assetMap, tacticalConfigs);
  }
}
