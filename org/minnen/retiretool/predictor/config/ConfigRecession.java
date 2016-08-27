package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.RecessionPredictor;

public class ConfigRecession extends PredictorConfig
{
  public final double threshProb;
  public final double threshDiffRecover;
  public final String riskyAsset;
  public final String safeAsset;

  public ConfigRecession(double threshProb, double threshDiffRecover, String riskyAsset, String safeAsset)
  {
    this.threshProb = threshProb;
    this.threshDiffRecover = threshDiffRecover;
    this.riskyAsset = riskyAsset;
    this.safeAsset = safeAsset;
  }

  @Override
  public boolean isValid()
  {
    return true;
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    return new RecessionPredictor(this, brokerAccess, assetNames);
  }

  @Override
  public String toString()
  {
    return String.format("[%.2f, %.2f]", threshProb, threshDiffRecover);
  }
}
