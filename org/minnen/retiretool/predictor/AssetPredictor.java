package org.minnen.retiretool.predictor;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Abtract base class for asset predictors. */
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

  public void feedback(long timeMS, int iCorrect, double r)
  {
    assert timeMS > lastFeedbackMS;
    lastFeedbackMS = timeMS;
    // Default behavior is to ignore feedback.
  }
}
