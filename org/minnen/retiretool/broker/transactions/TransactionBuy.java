package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Fixed;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.Account;

public class TransactionBuy extends Transaction
{
  public final String name;
  public final long   nShares;
  public final long   price;
  public final long   postBalance;

  public TransactionBuy(Account account, long time, String name, long nShares, String memo)
  {
    super(account, time, memo);
    this.name = name;
    this.nShares = nShares;
    this.price = account.broker.getPrice(name, time);
    this.postBalance = account.getCash() - getValue();
  }

  public long getValue()
  {
    return Fixed.mul(nShares, price);
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Buy: %s %.2f @ $%s ($%s)%s", Library.formatDate(time), name,
        Fixed.toFloat(nShares), Fixed.formatCurrency(price), Fixed.formatCurrency(getValue()),
        getMemoString());
  }
}
