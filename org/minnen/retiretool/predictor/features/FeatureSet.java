package org.minnen.retiretool.predictor.features;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;

public class FeatureSet extends FeatureExtractor
{
  private final List<FeatureExtractor> extractors = new ArrayList<>();

  public FeatureSet(FeatureExtractor... extractors)
  {
    super("FeatureSet");
    for (int i = 0; i < extractors.length; ++i) {
      assert extractors[i].size() == 1; // TODO remove restriction
      this.extractors.add(extractors[i]);
    }
  }

  public void add(FeatureExtractor extractor)
  {
    extractors.add(extractor);
  }

  public FeatureExtractor get(int i)
  {
    if (i < 0) {
      i = i + extractors.size();
    }
    return extractors.get(i);
  }

  @Override
  public int size()
  {
    // TODO assumes each internal extractor only generates one features.
    return extractors.size();
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, String assetName)
  {
    FeatureVec features = new FeatureVec(assetName, extractors.size());
    features.setTime(brokerAccess.getTime());
    for (int i = 0; i < extractors.size(); ++i) {
      // TODO assumes each internal extractor only generates one features.
      features.set(i, extractors.get(i).calculate(brokerAccess, assetName).get(0));
    }
    return features;
  }

}
