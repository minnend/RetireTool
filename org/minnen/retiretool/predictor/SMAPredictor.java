package org.minnen.retiretool.predictor;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

/** Single-scale Simple Moving Average (SMA) predictor. */
public class SMAPredictor extends AssetPredictor
{
  protected final String priceSeqName;
  protected final int    iPrice;
  protected final int    nMonths;
  protected final double margin;

  protected int          reloc = 0;   // below/at/above = -1/0/1

  public SMAPredictor(int nMonths, String priceSeqName, int iPrice, SequenceStore store)
  {
    this(nMonths, 0.0, priceSeqName, iPrice, store);
  }

  public SMAPredictor(int nMonths, double margin, String priceSeqName, int iPrice, SequenceStore store)
  {
    super(buildName(nMonths, margin), store);

    assert nMonths > 0;
    assert margin >= 0.0;
    assert iPrice >= 0;

    this.nMonths = nMonths;
    this.margin = margin;
    this.priceSeqName = priceSeqName;
    this.iPrice = iPrice;
    this.bAllowReuse = (margin == 0.0); // margin => memory
    this.bPredictOne = true;
  }

  private static String buildName(int nMonths, double margin)
  {
    if (margin == 0.0) {
      return String.format("SMA-%d", nMonths);
    } else {
      return String.format("SMA-%d-%.2f", nMonths, margin);
    }
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
    assert prices.getEndMS() == seqs[0].getEndMS();

    // Calculate trailing moving average.
    int iLast = prices.length() - 1;
    int iFirst = Math.max(0, iLast - nMonths);
    double sma = prices.average(iFirst, iLast).get(iPrice);

    // Test above / below moving average.
    double threshold = sma;
    if (margin > 0.0) {
      threshold -= reloc * (threshold * margin / 100.0);
    }

    double price = prices.get(iLast, iPrice);
    if (price > threshold) {
      reloc = 1;
      return 0;
    } else {
      reloc = 0;
      return 1;
    }
    // return (price > sma ? 0 : 1);
  }
}
