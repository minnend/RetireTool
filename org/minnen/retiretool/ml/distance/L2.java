package org.minnen.retiretool.ml.distance;

import org.minnen.retiretool.data.FeatureVec;

/** Computes Squared L2-Norm */
public class L2 implements DistanceMetric
{
  @Override
  public double distance(FeatureVec x, FeatureVec y)
  {
    assert x.getNumDims() == y.getNumDims();
    return x.dist2(y);
  }
}
