package org.minnen.retiretool.broker;

public class PositionLot
{
  public final String name;
  public final long   time;
  public final double price;

  private int         nShares;

  public PositionLot(String name, long time, int nShares, double price)
  {
    this.name = name;
    this.time = time;
    this.nShares = nShares;
    this.price = price;
  }

  public int getNumShares()
  {
    return nShares;
  }

  public double getValue()
  {
    assert nShares > 0;
    return nShares * price;
  }

  public void sell(int nSold)
  {
    assert nSold <= nShares;
    nShares -= nSold;
  }
}
