package org.minnen.retiretool.predictor;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

/** Single-scale Simple Moving Average (SMA) predictor */
public class SMAPredictor extends AssetPredictor
{
  protected final String priceSeqName;
  protected final int    nMonths;

  public SMAPredictor(int nMonths, String priceSeqName, SequenceStore store)
  {
    super("SMA-" + nMonths, store);
    this.nMonths = nMonths;
    this.priceSeqName = priceSeqName;
  }

  @Override
  public int selectAsset(Sequence... seqs)
  {
    assert seqs.length >= 2; // only seqs[0] and seqs[1] will be selected
    assert seqs[0].getEndMS() == seqs[1].getEndMS();

    Sequence prices = store.getMisc(priceSeqName);
    assert prices.length() > 0;
    assert prices.getEndMS() == seqs[0].getEndMS();

    // Calculate trailing moving average.
    int iLast = prices.length() - 1;
    int iFirst = Math.max(0, iLast - nMonths);
    double sma = prices.average(iFirst, iLast).get(0);

    // Test above / below moving average.
    double price = prices.get(iLast, 0);
    return (price > sma ? 0 : 1);
  }
}
