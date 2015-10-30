package org.minnen.retiretool.predictor;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Abstract base class for asset predictors. */
public abstract class AssetPredictor
{
  public final String        name;
  public final SequenceStore store;
  private long               lastFeedbackMS = Long.MIN_VALUE;

  public AssetPredictor(String name, SequenceStore store)
  {
    this.name = name;
    this.store = store;
  }

  public abstract int selectAsset(Sequence... seqs);

  public double[] selectDistribution(Sequence... seqs)
  {
    double[] d = new double[seqs.length];
    d[selectAsset(seqs)] = 1.0;
    return d;
  }

  public void feedback(long timeMS, int iCorrect, double r)
  {
    assert timeMS > lastFeedbackMS;
    lastFeedbackMS = timeMS;
    // Default behavior is to ignore feedback.
  }

  public void reset()
  {
    lastFeedbackMS = Long.MIN_VALUE;
  }
}
