package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;

public abstract class FeatureExtractor
{
  /** Name of this feature extractor. */
  public final String           name;

  /** Access object for getting information from a broker. */
  public final BrokerInfoAccess brokerAccess;

  public FeatureExtractor(String name, BrokerInfoAccess brokerAccess)
  {
    this.name = name;
    this.brokerAccess = brokerAccess;
  }

  public abstract FeatureVec calculate(String assetName);

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
