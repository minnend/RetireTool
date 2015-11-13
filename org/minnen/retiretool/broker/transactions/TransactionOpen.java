package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.broker.Account;

public class TransactionOpen extends Transaction
{

  public TransactionOpen(Account account, long time)
  {
    super(account, time, null);
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Open", TimeLib.formatDate(time));
  }
}
