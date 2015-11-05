package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.broker.Account;

public abstract class Transaction
{
  public final Account account;
  public final long    time;

  public Transaction(Account account, long time)
  {
    this.account = account;
    this.time = time;
  }
}
