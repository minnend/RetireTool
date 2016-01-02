package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.TimeLib;

public class TransactionSell extends Transaction
{
  public final String name;
  public final long   nSharesHeld;
  public final long   nSharesSold;
  public final long   price;
  public final long   postBalance;

  public TransactionSell(Account account, long time, String name, long nSharesHeld, long nSharesToSell, long price,
      String memo)
  {
    super(account, time, memo);
    assert nSharesToSell <= nSharesHeld;
    assert nSharesToSell > 0;
    assert price > 0;

    this.name = name;
    this.nSharesHeld = nSharesHeld;
    this.nSharesSold = nSharesToSell;
    this.price = price;
    this.postBalance = account.getCash() + getValue();
  }

  public long getValue()
  {
    return Fixed.mul(nSharesSold, price);
  }

  @Override
  public String toString()
  {
    return String.format(
        "%11s| Sell: %s %.2f (%s) @ $%s ($%s)%s",
        TimeLib.formatDate(time),
        name,
        Fixed.toFloat(nSharesSold),
        nSharesSold == nSharesHeld ? "All" : String.format("%.1f%%",
            Fixed.toFloat(Fixed.div(nSharesSold * 100, nSharesHeld))), Fixed.formatCurrency(price),
        Fixed.formatCurrency(getValue()), getMemoString());
  }
}
