package org.minnen.retiretool.predictor.daily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.util.Library;

public class TacticalPredictor extends Predictor
{
  public final ConfigTactical config;
  public final String         safeName;

  public TacticalPredictor(ConfigTactical config, BrokerInfoAccess brokerAccess, String... assetChoices)
  {
    super("Tactical", brokerAccess, assetChoices);
    this.config = config;
    this.predictorType = PredictorType.Distribution;

    assert assetChoices.length > 1;

    int iCash = Arrays.asList(assetChoices).indexOf("cash");
    if (iCash < 0) iCash = assetChoices.length - 1;
    this.safeName = assetChoices[iCash];

    List<Predictor> predictorList = new ArrayList<>();
    for (int i = 0; i < assetChoices.length; ++i) {
      if (i == iCash) continue;
      PredictorConfig cfg = ConfigMulti.buildTactical(config.iPrice, i, iCash);
      Predictor predictor = cfg.build(brokerAccess, assetChoices);
      predictorList.add(predictor);
    }
    this.predictors = predictorList.toArray(new Predictor[predictorList.size()]);
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    // Calculate the combined distribution.
    distribution.clear();
    for (int i = 0; i < predictors.length; ++i) {
      // Each predictor selects either risky or safe asset.
      String name = predictors[i].selectAsset();
      if (name.equals(safeName)) continue;
      int ix = distribution.find(name);
      distribution.weights[ix] += 1.0;  // increase weight for risky asset
    }
    if (Library.sum(distribution.weights) < 1e-4) {
      // If zero weight, put everything into safe asset.
      distribution.set(safeName, 1.0);
    } else { 
      distribution.normalize();
    }
  }
}
