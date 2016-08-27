package org.minnen.retiretool.ml.distance;

import org.minnen.retiretool.data.FeatureVec;

/** Computes L1-Norm */
public class L1 implements DistanceMetric
{
  @Override
  public double distance(FeatureVec x, FeatureVec y)
  {
    assert x.getNumDims() == y.getNumDims();
    return x.absdist(y);
  }
}
