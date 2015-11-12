package org.minnen.retiretool.broker;

public class PositionLot
{
  public final String name;
  public final long   purchaseTime;
  public final long   purchasePrice;

  private long        nShares;

  public PositionLot(String name, long time, long nShares, long price)
  {
    assert nShares > 0;
    assert price > 0;

    this.name = name;
    this.purchaseTime = time;
    this.nShares = nShares;
    this.purchasePrice = price;
  }

  public long getNumShares()
  {
    return nShares;
  }

  public long getCostBasis()
  {
    assert nShares > 0;
    return nShares * purchasePrice;
  }

  public void sell(long nSold)
  {
    assert nSold > 0;
    assert nSold <= nShares;
    nShares -= nSold;
  }
}
