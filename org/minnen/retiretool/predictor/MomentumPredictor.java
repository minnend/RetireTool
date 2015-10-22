package org.minnen.retiretool.predictor;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Single-scale momentum predictor */
public class MomentumPredictor extends AssetPredictor
{
  public final int nLookback;
  public final int nSkipRecent;

  public MomentumPredictor(int nLookback, int nSkipRecent, SequenceStore store)
  {
    super(buildName(nLookback, nSkipRecent), store);
    assert nSkipRecent < nLookback;
    this.nLookback = nLookback;
    this.nSkipRecent = nSkipRecent;
  }

  public MomentumPredictor(int nLookback, SequenceStore store)
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
  public int selectAsset(Sequence... seqs)
  {
    double bestReturn = 0.0;
    int iSelected = -1;
    for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
      Sequence seq = seqs[iSeq];
      int iLast = seq.length() - 1;
      double r = FinLib.getReturn(seq, iLast - nLookback, iLast - nSkipRecent);
      if (r > bestReturn) {
        iSelected = iSeq;
        bestReturn = r;
      }
    }
    return iSelected;
  }
}
