package org.minnen.retiretool.predictor;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

public class MomentumPredictor extends AssetPredictor
{
  public final int nLookback;

  public MomentumPredictor(int nLookback, SequenceStore store)
  {
    super("Momentum-" + nLookback, store);
    this.nLookback = nLookback;
  }

  @Override
  public int selectAsset(Sequence... seqs)
  {
    double bestReturn = 0.0;
    int iSelected = -1;
    for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
      Sequence seq = seqs[iSeq];
      int iLast = seq.length() - 1;
      double r = FinLib.getReturn(seq, iLast - nLookback, iLast);
      if (r > bestReturn) {
        iSelected = iSeq;
        bestReturn = r;
      }
    }
    return iSelected;
  }
}
