package org.minnen.retiretool.predictor.config;

import java.util.Arrays;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.predictor.daily.MixedPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;

public class ConfigMixed extends PredictorConfig
{
  private final DiscreteDistribution mix;
  private final PredictorConfig[]    configs;

  public ConfigMixed(DiscreteDistribution mix, PredictorConfig... configs)
  {
    this.mix = mix;
    this.configs = Arrays.copyOf(configs, configs.length);
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
    assert mix.isNormalized();
    DiscreteDistribution perturbedMix = new DiscreteDistribution(mix);
    for (int i = 0; i < perturbedMix.size(); ++i) {
      perturbedMix.weights[i] = Math.max(0.0, perturbedMix.weights[i] + rng.nextGaussian() * (1.0 / 100.0));
    }
    perturbedMix.normalize();

    PredictorConfig[] perturbedConfigs = new PredictorConfig[configs.length];
    for (int i = 0; i < configs.length; ++i) {
      perturbedConfigs[i] = configs[i].genPerturbed();
    }
    return new ConfigMixed(perturbedMix, perturbedConfigs);
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    Predictor[] predictors = new Predictor[configs.length];
    for (int i = 0; i < configs.length; ++i) {
      predictors[i] = configs[i].build(brokerAccess, assetNames);
    }
    return new MixedPredictor(predictors, mix, brokerAccess, assetNames);
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("MixedPredictor%s", mix));
    // sb.append("\n");
    // for (int i = 0; i < configs.length; ++i) {
    // sb.append(String.format(" %s%s", configs[i], i == configs.length - 1 ? "" : "\n"));
    // }
    return sb.toString();
  }

}
