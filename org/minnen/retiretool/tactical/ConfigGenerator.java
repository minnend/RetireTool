package org.minnen.retiretool.tactical;

import org.minnen.retiretool.predictor.config.PredictorConfig;

public abstract class ConfigGenerator
{
  public abstract PredictorConfig genRandom();

  public abstract PredictorConfig genCandidate(PredictorConfig config);
}
