package org.minnen.retiretool.predictor;

import org.minnen.retiretool.Library;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

/** Single-scale Simple Moving Average (SMA) predictor. */
public class SMAPredictor extends AssetPredictor
{
  protected final String priceSeqName;
  protected final int    iPrice;
  protected final int    nMonths;

  public SMAPredictor(int nMonths, String priceSeqName, int iPrice, SequenceStore store)
  {
    super("SMA-" + nMonths, store);
    this.nMonths = nMonths;
    this.priceSeqName = priceSeqName;
    this.iPrice = iPrice;
    this.bAllowReuse = true;
    this.bPredictOne = true;
  }

  public int getNumMonths()
  {
    return nMonths;
  }

  @Override
  protected int calcSinglePrediction(Sequence... seqs)
  {
    assert seqs.length == 2;
    assert seqs[0].getEndMS() == seqs[1].getEndMS();

    Sequence prices = store.getMisc(priceSeqName);
    assert prices.length() > 0;
    assert prices.getEndMS() == seqs[0].getEndMS() : String.format("%s vs. %s", Library.formatDate(prices.getEndMS()),
        Library.formatDate(seqs[0].getEndMS()));

    // Calculate trailing moving average.
    int iLast = prices.length() - 1;
    int iFirst = Math.max(0, iLast - nMonths);
    double sma = prices.average(iFirst, iLast).get(iPrice);

    // Test above / below moving average.
    double price = prices.get(iLast, iPrice);
    return (price > sma ? 0 : 1);
  }
}
