package org.minnen.retiretool.predictor;

import java.util.Arrays;

import org.minnen.retiretool.Library;
import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Abstract base class for asset predictors. */
public abstract class AssetPredictor
{
  public final String        name;
  public final SequenceStore store;

  protected long             lastFeedbackMS = Long.MIN_VALUE;

  /** True for predictors with no state or memory. */
  protected boolean          bAllowReuse    = false;

  /** True for predictors that select one asset (vs. a distribution over assets). */
  protected boolean          bPredictOne    = true;

  /** Reusable distribution array to reduce object creation. */
  private double[]           distribution;

  public AssetPredictor(String name, SequenceStore store)
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
      int iBest = 0;
      for (int i = 1; i < distribution.length; ++i) {
        if (distribution[i] > distribution[iBest]) {
          iBest = i;
        }
      }
      return iBest;
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
      assert Math.abs(Library.sum(distribution) - 1.0) < 1e-6;
    }

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

  public void feedback(long timeMS, int iCorrect, double r)
  {
    // Default behavior is to ignore feedback but protect against rewinds.
    assert bAllowReuse || timeMS > lastFeedbackMS;
    lastFeedbackMS = timeMS;
  }

  public void reset()
  {
    lastFeedbackMS = Long.MIN_VALUE;
  }
}
