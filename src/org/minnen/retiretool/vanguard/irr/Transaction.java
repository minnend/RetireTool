package org.minnen.retiretool.vanguard.irr;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

/** Represents a single transcation in a Vanguard account. */
public class Transaction
{
  public long   date;
  public String description;
  public String fund;
  public double quantity;
  public double price;
  public double amount;

  @Override
  public String toString()
  {
    return String.format("[%s|%s|%s|%.3f|$%s|$%s]", TimeLib.formatDate2(date), description, fund, quantity,
        FinLib.currencyFormatter.format(price), FinLib.currencyFormatter.format(amount));
  }
}
