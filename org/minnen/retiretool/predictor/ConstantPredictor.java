package org.minnen.retiretool.predictor;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Asset predictor that always predicts the same index. */
public class ConstantPredictor extends AssetPredictor
{
  public final int constantPrediction;

  public ConstantPredictor(String name, int constantPrediction, SequenceStore store)
  {
    super(name, store);
    this.bAllowReuse = true;
    this.constantPrediction = constantPrediction;
  }

  @Override
  public int selectAsset(Sequence... seqs)
  {
    return constantPrediction;
  }
}
