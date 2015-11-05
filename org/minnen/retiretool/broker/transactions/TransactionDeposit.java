package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.Account;

public class TransactionDeposit extends Transaction
{
  public final double amount;
  public final double postBalance;

  public TransactionDeposit(Account account, long time, double amount)
  {
    super(account, time);
    assert amount > 0.0;
    this.amount = amount;
    this.postBalance = account.getBalance() + amount;
  }

  @Override
  public String toString()
  {
    return String.format("[%s] Deposit: %s -> %s]", Library.formatDate(time), FinLib.formatDollars(amount),
        FinLib.formatDollars(postBalance));
  }
}
