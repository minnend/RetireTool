package org.minnen.retiretool.predictor.monthly;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.SequenceStoreV1;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;

public class MixedPredictor extends AssetPredictor
{
  private final double[] weights;
  private final double   blendWeight;

  private double[]       prevDist;

  public MixedPredictor(String name, AssetPredictor[] basePredictors, int[] percents, SequenceStoreV1 store)
  {
    this(name, basePredictors, percents, 0.0, store);
  }

  public MixedPredictor(String name, AssetPredictor[] basePredictors, int[] percents, double blendWeight,
      SequenceStoreV1 store)
  {
    super(buildName(name, basePredictors, percents), store);
    assert basePredictors.length == percents.length;

    this.weights = new double[percents.length];
    double sum = Library.sum(percents);
    for (int i = 0; i < percents.length; ++i) {
      this.weights[i] = (double) percents[i] / sum;
    }
    assert Math.abs(Library.sum(this.weights) - 1.0) < 1e-6;
    this.blendWeight = blendWeight;

    this.predictors = basePredictors;
    this.bAllowReuse = false;
    this.bPredictOne = false;
  }

  protected void calcDistribution(double[] distribution, Sequence... seqs)
  {
    for (int iPredictor = 0; iPredictor < predictors.length; ++iPredictor) {
      double[] p = predictors[iPredictor].selectDistribution(seqs);
      for (int i = 0; i < distribution.length; ++i) {
        distribution[i] += weights[iPredictor] * p[i];
      }
    }

    if (blendWeight > 0.0) {
      if (prevDist != null && prevDist.length == distribution.length) {
        for (int i = 0; i < distribution.length; ++i) {
          distribution[i] = prevDist[i] * blendWeight + distribution[i] * (1.0 - blendWeight);
        }
      } else {
        prevDist = new double[distribution.length];
      }
      System.arraycopy(distribution, 0, prevDist, 0, distribution.length);
    }
  }

  private static String buildName(String name, AssetPredictor[] predictors, int[] percents)
  {
    if (name != null) {
      return name;
    }

    assert predictors.length == percents.length;
    String[] names = new String[predictors.length];
    for (int i = 0; i < names.length; ++i) {
      names[i] = predictors[i].name;
    }
    return FinLib.buildMixedName(names, percents);
  }
}
