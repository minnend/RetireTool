package org.minnen.retiretool.predictor;

import java.util.Arrays;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Weighted Majority Algorithm Predictor */
public class WMAPredictor extends AssetPredictor
{
  private final AssetPredictor[] predictors;
  private final double[]         weights;
  private final int[]            predictions;
  private final double           alpha;
  private final double           beta;

  public WMAPredictor(String name, AssetPredictor[] predictors, SequenceStore store)
  {
    this(name, predictors, 0.5, 0.01, store);
  }

  public WMAPredictor(String name, AssetPredictor[] predictors, double alpha, double beta, SequenceStore store)
  {
    super(name, store);
    this.predictors = predictors;
    weights = new double[predictors.length];
    Arrays.fill(weights, 1.0 / predictors.length);
    predictions = new int[predictors.length];
    this.alpha = alpha;
    this.beta = beta;
  }

  @Override
  public int selectAsset(Sequence... seqs)
  {
    double wsum = 0.0;
    for (int i = 0; i < predictors.length; ++i) {
      wsum += weights[i];
    }
    assert Math.abs(wsum - 1.0) < 1e-6;

    double sum = 0.0;
    for (int i = 0; i < predictors.length; ++i) {
      predictions[i] = predictors[i].selectAsset(seqs);
      sum += weights[i] * predictions[i];
    }
    return (int) Math.round(sum);
  }

  @Override
  public void feedback(long timeMS, int iCorrect, double r)
  {
    for (int i = 0; i < predictors.length; ++i) {
      if (predictions[i] == iCorrect) {
        weights[i] += beta;
      } else {
        weights[i] *= alpha;
      }
    }

    double wsum = 0.0;
    for (int i = 0; i < predictors.length; ++i) {
      wsum += weights[i];
    }

    for (int i = 0; i < predictors.length; ++i) {
      weights[i] /= wsum;
    }
  }
}
