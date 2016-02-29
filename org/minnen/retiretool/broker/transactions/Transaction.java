package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.broker.Account;

public abstract class Transaction
{
  /**
   * Fund flow for this transaction.
   *
   * <ul>
   * <li>InFlow = funds flow in to the account (e.g. a deposit)
   * <li>OutFlow = funds flow out of the account (e.g. a withdrawal)
   * <li>Internal = funds stay in the account (e.g. buy or sell a stock, or a dividend payment)
   * </ul>
   */
  public enum Flow {
    InFlow, OutFlow, Internal
  }

  public final Account account;
  public final long    time;
  public final Flow    flow;
  public final String  memo;

  public Transaction(Account account, long time, Flow flow, String memo)
  {
    this.account = account;
    this.time = time;
    this.flow = flow;
    this.memo = (memo == null ? "" : memo);
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
