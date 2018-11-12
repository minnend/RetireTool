package org.minnen.retiretool.tactical;

import org.minnen.retiretool.predictor.config.PredictorConfig;

public abstract class ConfigGenerator
{
  /** @return random config that acts as a seed for search / optimization. */
  public abstract PredictorConfig genRandom();

  /** @return config to test that may improve on the given config (used for simple hill-climbing optimization). */
  public abstract PredictorConfig genCandidate(PredictorConfig config);
}
