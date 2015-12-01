package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.Random;

public abstract class PredictorConfig
{
  public static final Random rng = new Random();

  public abstract boolean isValid();

  /** @return a perturbed version of this configuration. */
  public abstract PredictorConfig genPerturbed();

  public abstract Predictor build(BrokerInfoAccess brokerAccess, String... assetNames);
}
