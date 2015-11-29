package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;

public class MixedPredictor extends Predictor
{
  public final DiscreteDistribution mix;

  public MixedPredictor(Predictor[] predictors, DiscreteDistribution mix, BrokerInfoAccess brokerAccess)
  {
    super("Mixed", brokerAccess, predictors[0].assetChoices);
    assert mix.size() == predictors.length;
    this.predictorType = PredictorType.Distribution;
    this.predictors = predictors;
    this.mix = mix;
    reset(); // child predictors may have already been used.
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    distribution.clear();
    for (int i = 0; i < predictors.length; ++i) {
      double wi = mix.weights[i];
      DiscreteDistribution di = predictors[i].selectDistribution();
      assert di.size() == distribution.size();
      for (int j = 0; j < di.size(); ++j) {
        distribution.weights[j] += wi * di.weights[j];
      }
    }
    distribution.normalize();
  }
}
