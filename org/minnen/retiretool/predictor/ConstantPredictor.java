package org.minnen.retiretool.predictor;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

/** Asset predictor that always predicts the same index. */
public class ConstantPredictor extends AssetPredictor
{
  public final int iConstantPrediction;

  public ConstantPredictor(String name, int constantPrediction, SequenceStore store)
  {
    super(name, store);
    this.bAllowReuse = true;
    this.bPredictOne = true;
    this.iConstantPrediction = constantPrediction;
  }

  @Override
  protected int calcSinglePrediction(Sequence... seqs)
  {
    return iConstantPrediction;
  }
}
