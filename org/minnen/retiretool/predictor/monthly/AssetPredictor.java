package org.minnen.retiretool.predictor.monthly;

import java.util.Arrays;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.SequenceStoreV1;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

/** Abstract base class for asset predictors. */
public abstract class AssetPredictor
{
  public final String          name;
  public final SequenceStoreV1 store;

  public AssetPredictor[]      predictors;

  protected long               lastFeedbackMS = TimeLib.TIME_BEGIN;

  /** True for predictors with no state or memory. */
  protected boolean            bAllowReuse    = false;

  /** True for predictors that select one asset (vs. a distribution over assets). */
  protected boolean            bPredictOne    = true;

  /** Reusable distribution array to reduce object creation. */
  private double[]             distribution;

  public AssetPredictor(String name, SequenceStoreV1 store)
  {
    this.name = name;
    this.store = store;
  }

  public final int selectAsset(Sequence... seqs)
  {
    if (bPredictOne) {
      return calcSinglePrediction(seqs);
    } else {
      selectDistribution(seqs);
      return Library.argmax(distribution);
    }
  }

  public final double[] selectDistribution(Sequence... seqs)
  {
    if (distribution == null || distribution.length != seqs.length) {
      distribution = new double[seqs.length];
    } else {
      Arrays.fill(distribution, 0.0);
    }

    if (bPredictOne) {
      int index = calcSinglePrediction(seqs);
      distribution[index] = 1.0;
    } else {
      calcDistribution(distribution, seqs);
    }

    assert Math.abs(Library.sum(distribution) - 1.0) < 1e-6;
    return distribution;
  }

  protected int calcSinglePrediction(Sequence... seqs)
  {
    throw new RuntimeException("Asset Predictors that select a single asset should override this method.");
  }

  protected void calcDistribution(double[] distribution, Sequence... seqs)
  {
    throw new RuntimeException("Asset Predictors that predict a distribution should override this method.");
  }

  public void feedback(long timeMS, int iCorrect, double observedReturn)
  {
    // Default behavior is to ignore feedback but protect against rewinds.
    assert bAllowReuse || timeMS > lastFeedbackMS;
    lastFeedbackMS = timeMS;
  }

  public void reset()
  {
    lastFeedbackMS = Long.MIN_VALUE;
    if (predictors != null) {
      for (AssetPredictor predictor : predictors) {
        predictor.reset();
      }
    }
  }
}
