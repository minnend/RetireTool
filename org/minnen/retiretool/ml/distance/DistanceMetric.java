package org.minnen.retiretool.ml.distance;

import org.minnen.retiretool.data.FeatureVec;

/** Computes a distance between two feature vectors. */
public interface DistanceMetric
{
  public double distance(FeatureVec x, FeatureVec y);
}
