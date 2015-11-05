package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.Account;

public class TransactionSell extends Transaction
{
  public final String name;
  public final int    nShares;
  public final double price;
  public final double postBalance;

  public TransactionSell(Account account, long time, String name, int nShares)
  {
    super(account, time);
    this.name = name;
    this.nShares = nShares;
    this.price = account.broker.getPrice(name, time);
    this.postBalance = account.getBalance() + nShares * price;
  }

  @Override
  public String toString()
  {
    return String
        .format("[%s] Sell: %s @ %s -> %s]", Library.formatDate(time), name, FinLib.formatDollars(postBalance));
  }
}
