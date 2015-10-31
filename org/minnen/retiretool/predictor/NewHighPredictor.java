package org.minnen.retiretool.predictor;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

public class NewHighPredictor extends AssetPredictor
{
  protected final int nMonths;

  public NewHighPredictor(int nMonths, SequenceStore store)
  {
    super(String.format("NewHigh[%d]", nMonths), store);
    assert nMonths >= 1;
    this.nMonths = nMonths;
    this.bAllowReuse = true;
    this.bPredictOne = true;
  }

  @Override
  protected int calcSinglePrediction(Sequence... seqs)
  {
    assert seqs.length == 2;
    assert seqs[0].getEndMS() == seqs[1].getEndMS();

    boolean bIsNewHigh = true;
    int iLast = seqs[0].length() - 1;
    double lastPrice = seqs[0].get(iLast, 0);
    for (int i = 1; i <= nMonths; ++i) {
      double price = seqs[0].get(iLast - i, 0);
      if (lastPrice <= price) {
        bIsNewHigh = false;
        break;
      }
    }

    return (bIsNewHigh ? 0 : 1);
  }
}
