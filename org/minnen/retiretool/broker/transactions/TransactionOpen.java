package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.Account;

public class TransactionOpen extends Transaction
{

  public TransactionOpen(Account account, long time)
  {
    super(account, time);
    // TODO Auto-generated constructor stub
  }
  
  @Override
  public String toString()
  {
    return String.format("[%s] Open", Library.formatDate(time));
  }
}
