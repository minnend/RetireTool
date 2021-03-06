package org.minnen.retiretool.predictor.monthly;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.SequenceStoreV1;
import org.minnen.retiretool.util.FinLib;

/** Single-scale momentum predictor */
public class MomentumPredictor extends AssetPredictor
{
  public final int nLookback;
  public final int nSkipRecent;

  public MomentumPredictor(int nLookback, int nSkipRecent, SequenceStoreV1 store)
  {
    super(buildName(nLookback, nSkipRecent), store);
    assert nSkipRecent < nLookback;
    this.nLookback = nLookback;
    this.nSkipRecent = nSkipRecent;
    this.bAllowReuse = true;
    this.bPredictOne = true;
  }

  public MomentumPredictor(int nLookback, SequenceStoreV1 store)
  {
    this(nLookback, 0, store);
  }

  private static String buildName(int nLookback, int nSkipRecent)
  {
    if (nSkipRecent == 0) {
      return "Momentum-" + nLookback;
    } else {
      return String.format("Momentum-%d.%d", nLookback, nSkipRecent);
    }
  }

  @Override
  protected int calcSinglePrediction(Sequence... seqs)
  {
    double bestReturn = 0.0;
    int iSelected = -1;
    for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
      Sequence seq = seqs[iSeq];
      int iLast = seq.length() - 1;
      double r = FinLib.getTotalReturn(seq, iLast - nLookback, iLast - nSkipRecent);
      if (r > bestReturn) {
        iSelected = iSeq;
        bestReturn = r;
      }
    }
    return iSelected;
  }
}
