package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.Account;

public class TransactionBuy extends Transaction
{
  public final String name;
  public final int    nShares;
  public final double price;
  public final double postBalance;

  public TransactionBuy(Account account, long time, String name, int nShares)
  {
    super(account, time);
    this.name = name;
    this.nShares = nShares;
    this.price = account.broker.getPrice(name, time);
    this.postBalance = account.getBalance() - nShares * price;
  }

  public double getValue()
  {
    return nShares * price;
  }

  @Override
  public String toString()
  {
    return String.format("[%s] Buy: %s @ %s -> %s]", Library.formatDate(time), name, FinLib.formatDollars(postBalance));
  }
}
