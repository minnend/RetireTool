package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.broker.Account;

public abstract class Transaction
{
  public final Account account;
  public final long    time;
  public final String  memo;

  public Transaction(Account account, long time, String memo)
  {
    this.account = account;
    this.time = time;
    this.memo = memo;
  }

  protected String getMemoString()
  {
    if (memo == null || memo.isEmpty()) {
      return "";
    } else {
      return String.format(" [%s]", memo);
    }
  }
}
