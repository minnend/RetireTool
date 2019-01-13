package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.util.TimeLib;

public class TransactionOpen extends Transaction
{

  public TransactionOpen(Account account, long time)
  {
    super(account, time, Flow.Internal, null);
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Open", TimeLib.formatDate(time));
  }
}
