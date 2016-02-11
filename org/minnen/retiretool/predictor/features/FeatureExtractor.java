package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;

public abstract class FeatureExtractor
{
  /** Name of this feature extractor. */
  public final String name;

  public FeatureExtractor(String name)
  {
    this.name = name;
  }

  public abstract FeatureVec calculate(BrokerInfoAccess brokerAccess, String assetName);

  /** @return number of features calculated by this extractor. */
  public int size()
  {
    return 1;
  }

  @Override
  public String toString()
  {
    return name;
  }
}
