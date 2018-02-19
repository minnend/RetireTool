package org.minnen.retiretool.predictor.daily;

import java.util.Arrays;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;

public class GatedPredictor extends Predictor
{
  private final Predictor primaryPredictor;
  private final Predictor gate;
  public final String     safeAsset;

  public GatedPredictor(Predictor primaryPredictor, Predictor gate, BrokerInfoAccess brokerAccess,
      String[] assetChoices)
  {
    super("GatedPredictor", brokerAccess, assetChoices);
    this.primaryPredictor = primaryPredictor;
    this.gate = gate;
    this.predictorType = primaryPredictor.predictorType;
    this.predictors = new Predictor[] { primaryPredictor, gate };

    int iCash = Arrays.asList(assetChoices).indexOf("cash");
    if (iCash < 0) iCash = assetChoices.length - 1;
    this.safeAsset = assetChoices[iCash];
    reset(); // child predictors may have already been used.
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    String gateAsset = gate.selectAsset();
    if (gateAsset.equals(safeAsset)) {
      distribution.clear();
      distribution.set(safeAsset, 1.0);
    } else {
      primaryPredictor.calcDistribution(distribution);
    }
  }
}
