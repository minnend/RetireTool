package org.minnen.retiretool.predictor;

import org.minnen.retiretool.data.Sequence;

public class SMAPredictor extends AssetPredictor
{
  protected final int nMonths;

  public SMAPredictor(int nMonths)
  {
    this.nMonths = nMonths;
    this.name = "SMA-" + nMonths;
  }

  @Override
  public int selectAsset(Sequence... seqs)
  {
    return -1;
  }
}
