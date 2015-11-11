package org.minnen.retiretool.broker;

public class PositionLot
{
  public final String name;
  public final long   purchaseTime;
  public final double purchasePrice;

  private double      nShares;

  public PositionLot(String name, long time, double nShares, double price)
  {
    assert nShares > 0.0;
    assert price > 0.0;

    this.name = name;
    this.purchaseTime = time;
    this.nShares = nShares;
    this.purchasePrice = price;
  }

  public double getNumShares()
  {
    return nShares;
  }

  public double getCostBasis()
  {
    assert nShares > 0;
    return nShares * purchasePrice;
  }

  public void sell(double nSold)
  {
    assert nSold > 0.0;
    assert nSold <= nShares;
    nShares -= nSold;
  }
}
