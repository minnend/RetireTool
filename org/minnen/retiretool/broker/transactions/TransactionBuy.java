package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.Account;

public class TransactionBuy extends Transaction
{
  public final String name;
  public final double nShares;
  public final double price;
  public final double postBalance;

  public TransactionBuy(Account account, long time, String name, double nShares, String memo)
  {
    super(account, time, memo);
    this.name = name;
    this.nShares = nShares;
    this.price = account.broker.getPrice(name, time);
    this.postBalance = account.getCash() - nShares * price;
  }

  public double getValue()
  {
    return nShares * price;
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Buy: %s %.2f @ $%s%s", Library.formatDate(time), name, nShares,
        FinLib.currencyFormatter.format(price), getMemoString());
  }
}
