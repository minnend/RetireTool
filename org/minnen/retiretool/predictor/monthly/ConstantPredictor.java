package org.minnen.retiretool.predictor.monthly;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.SequenceStoreV1;

/** Asset predictor that always predicts the same index. */
public class ConstantPredictor extends AssetPredictor
{
  public final int iConstantPrediction;

  public ConstantPredictor(String name, int constantPrediction, SequenceStoreV1 store)
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
