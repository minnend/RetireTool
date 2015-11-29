package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;

public class MixedPredictor extends Predictor
{
  public MixedPredictor(Predictor[] predictors, DiscreteDistribution distribution, BrokerInfoAccess brokerAccess)
  {
    super("Mixed", brokerAccess, distribution.names);
    this.predictorType = PredictorType.Distribution;
    reset();  // child predictors may have already been used.
  }
}
