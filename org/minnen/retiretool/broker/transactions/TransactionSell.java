package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.Fixed;
import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.broker.Account;

public class TransactionSell extends Transaction
{
  public final String name;
  public final long   nShares;
  public final long   price;
  public final long   postBalance;

  public TransactionSell(Account account, long time, String name, long nShares, long price, String memo)
  {
    super(account, time, memo);
    this.name = name;
    this.nShares = nShares;
    this.price = price;
    this.postBalance = account.getCash() + getValue();
  }

  public long getValue()
  {
    return Fixed.mul(nShares, price);
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Sell: %s %.2f @ $%s ($%s)%s", TimeLib.formatDate(time), name, Fixed.toFloat(nShares),
        Fixed.formatCurrency(price), Fixed.formatCurrency(getValue()), getMemoString());
  }
}
