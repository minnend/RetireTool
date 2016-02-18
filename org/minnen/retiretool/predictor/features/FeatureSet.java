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
    int n = 0;
    for (FeatureExtractor fe : extractors) {
      n += fe.size();
    }
    return n;
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, String assetName)
  {
    FeatureVec features = new FeatureVec(assetName, size());
    features.setTime(brokerAccess.getTime());
    int iDim = 0;
    for (int i = 0; i < extractors.size(); ++i) {
      FeatureExtractor fe = extractors.get(i);
      FeatureVec fv = fe.calculate(brokerAccess, assetName);
      assert fv.getNumDims() == fe.size();
      iDim = features.set(iDim, fv);
    }
    return features;
  }
}
