package org.minnen.retiretool.ml;

import org.minnen.retiretool.data.FeatureVec;

public abstract class RegressionFV
{
  public double predict(double x)
  {
    return predict(new FeatureVec(1, x));
  }

  public abstract double predict(FeatureVec x);
}
