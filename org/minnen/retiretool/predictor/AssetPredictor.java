package org.minnen.retiretool.predictor;

import org.minnen.retiretool.data.Sequence;

public abstract class AssetPredictor
{
  protected String name;

  public String getName()
  {
    return name;
  }

  public abstract int selectAsset(Sequence... seqs);
}
