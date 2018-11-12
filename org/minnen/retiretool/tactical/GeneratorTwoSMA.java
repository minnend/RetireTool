package org.minnen.retiretool.tactical;

import java.util.HashSet;
import java.util.Set;

import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.PredictorConfig;

/** Generator class for a multi-predictor that uses two SMA-based predictors. */
public class GeneratorTwoSMA extends ConfigGenerator
{
  private static final boolean      defaultDecision = true;
  private static final Set<Integer> contraryCodes   = new HashSet<Integer>();

  private final GeneratorSMA        generatorSMA    = new GeneratorSMA();

  static {
    contraryCodes.add(0);
  }

  @Override
  public PredictorConfig genRandom()
  {
    PredictorConfig[] configs = new PredictorConfig[2];

    // Start with a known-good single predictor.
    configs[0] = GeneratorSMA.getRandomKnownConfig();

    // Add a random predictor.
    configs[1] = generatorSMA.genRandom();

    return new ConfigMulti(defaultDecision, contraryCodes, configs);
  }

  @Override
  public PredictorConfig genCandidate(PredictorConfig config)
  {
    ConfigMulti multi = (ConfigMulti) config;

    PredictorConfig[] perturbed = new PredictorConfig[2];
    perturbed[0] = multi.configs[0]; // no change to base config
    perturbed[1] = multi.configs[1].genPerturbed(); // random perturbation to second config

    return new ConfigMulti(defaultDecision, contraryCodes, perturbed);
  }

}
