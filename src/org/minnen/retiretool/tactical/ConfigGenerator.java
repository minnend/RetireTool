package org.minnen.retiretool.tactical;

import org.minnen.retiretool.predictor.config.PredictorConfig;

public abstract class ConfigGenerator
{
  public static enum Mode {
    ALL_NEW, EXTEND
  }

  public final Mode mode;

  public ConfigGenerator(Mode mode)
  {
    this.mode = mode;
  }

  /** @return random config that acts as a seed for search / optimization. */
  public abstract PredictorConfig genRandom();

  /** @return config to test that may improve on the given config (used for simple hill-climbing optimization). */
  public abstract PredictorConfig genCandidate(PredictorConfig config);
}
