package org.minnen.retiretool.ml.distance;

import org.minnen.retiretool.data.FeatureVec;

public class WeightedL2 implements DistanceMetric
{
  private FeatureVec fvWeights;

  public WeightedL2(double[] weights)
  {
    fvWeights = new FeatureVec(weights);
  }

  public WeightedL2(int D)
  {
    fvWeights = new FeatureVec(D);
    fvWeights.fill(1.0 / D);
  }

  public void setWeights(double[] weights)
  {
    fvWeights.set(weights);
  }

  public FeatureVec getWeights()
  {
    return fvWeights;
  }

  public int getNumDims()
  {
    return fvWeights.getNumDims();
  }

  @Override
  public double distance(FeatureVec x, FeatureVec y)
  {
    final int D = x.getNumDims();
    assert fvWeights.getNumDims() == D;
    assert D == y.getNumDims();
    return x.sub(y)._sqr()._mul(fvWeights).sum();
  }
}
