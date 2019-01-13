package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;

public class MixedPredictor extends Predictor
{
  public final DiscreteDistribution mix;

  public MixedPredictor(Predictor[] predictors, DiscreteDistribution mix, BrokerInfoAccess brokerAccess,
      String... assetChoices)
  {
    super("Mixed", brokerAccess, assetChoices);
    assert mix.size() == predictors.length;
    this.predictorType = PredictorType.Distribution;
    this.predictors = predictors;
    this.mix = mix;
    reset(); // child predictors may have already been used.
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    // Calculate the combined distribution.
    distribution.clear();
    for (int i = 0; i < predictors.length; ++i) {
      double wi = mix.weights[i];
      DiscreteDistribution dist = predictors[i].selectDistribution();
      for (int j = 0; j < dist.size(); ++j) {
        int iName = distribution.find(dist.names[j]);
        assert iName >= 0 : dist.names[j];
        distribution.weights[iName] += wi * dist.weights[j];
      }
    }
    // System.out.printf("[%s] %s\n", brokerAccess.getTimeInfo().date, distribution.toStringWithNames(2));
    distribution.normalize();
  }
}
