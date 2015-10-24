package org.minnen.retiretool.predictor;

import java.util.Arrays;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Winner Take All Predictor */
public class WTAPredictor extends AssetPredictor
{
  private final AssetPredictor[] predictors;
  private final double[]         weights;
  private final int[]            predictions;
  private final double           alpha;
  private final double           beta;

  public WTAPredictor(String name, AssetPredictor[] predictors, SequenceStore store)
  {
    this(name, predictors, 0.9, 0.1, store);
  }

  public WTAPredictor(String name, AssetPredictor[] predictors, double alpha, double beta, SequenceStore store)
  {
    super(name, store);
    this.predictors = predictors;
    weights = new double[predictors.length];
    Arrays.fill(weights, 1.0);
    predictions = new int[predictors.length];
    this.alpha = alpha;
    this.beta = beta;
  }

  @Override
  public int selectAsset(Sequence... seqs)
  {
    int iWinner = 0;
    for (int i = 0; i < predictors.length; ++i) {
      predictions[i] = predictors[i].selectAsset(seqs);
      if (weights[i] > weights[iWinner]) {
        iWinner = i;
      }
    }
    return predictions[iWinner];
  }

  @Override
  public void feedback(long timeMS, int iCorrect, double r)
  {
    for (int i = 0; i < predictors.length; ++i) {
      weights[i] *= alpha;
      if (predictions[i] == iCorrect) {
        weights[i] += beta;
      }
    }
  }
}
