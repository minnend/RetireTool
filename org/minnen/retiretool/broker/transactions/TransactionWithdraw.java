package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.broker.Account;

public class TransactionWithdraw extends Transaction
{
  public final double amount;
  public final double postBalance;

  public TransactionWithdraw(Account account, long time, double amount)
  {
    super(account, time);
    assert amount > 0.0;
    this.amount = amount;
    this.postBalance = account.getBalance() - amount;
  }

  @Override
  public String toString()
  {
    return String.format("[%s] Withdraw: %s -> %s", FinLib.formatDollars(amount), FinLib.formatDollars(postBalance));
  }
}