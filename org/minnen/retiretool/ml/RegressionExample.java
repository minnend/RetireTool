package org.minnen.retiretool.ml;

import org.minnen.retiretool.data.FeatureVec;

/**
 * A single example for a regression problem: y = f(x).
 */
public class RegressionExample
{
  public final FeatureVec x;
  public final double     y;

  public RegressionExample(double x, double y)
  {
    this.x = new FeatureVec(1, x);
    this.y = y;
  }

  public RegressionExample(FeatureVec x, double y)
  {
    this.x = x;
    this.y = y;
  }
}
