package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.Random;

public abstract class PredictorConfig
{
  public static final Random rng = new Random();

  public final int           iPredictIn;
  public final int           iPredictOut;

  public PredictorConfig()
  {
    this(0, 1);
  }

  public PredictorConfig(int iPredictIn, int iPredictOut)
  {
    this.iPredictIn = iPredictIn;
    this.iPredictOut = iPredictOut;
  }

  public abstract boolean isValid();

  /** @return a perturbed version of this configuration. */
  public abstract PredictorConfig genPerturbed();

  public abstract Predictor build(BrokerInfoAccess brokerAccess, String... assetNames);
}
