package org.minnen.retiretool.vanguard.irr;

import org.minnen.retiretool.util.FinLib;

public class Holding
{
  public long   planNumber;
  public String planName;
  public String fund;
  public double shares;
  public double price;
  public double totalValue;

  @Override
  public String toString()
  {
    return String.format("[%s  %.3f @ $%s = $%s]", fund, shares, FinLib.currencyFormatter.format(price),
        FinLib.currencyFormatter.format(totalValue));
  }
}
