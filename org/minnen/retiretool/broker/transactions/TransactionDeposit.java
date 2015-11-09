package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.Account;

public class TransactionDeposit extends Transaction
{
  public final double amount;
  public final double postBalance;

  public TransactionDeposit(Account account, long time, double amount, String memo)
  {
    super(account, time, memo);
    assert amount > 0.0;
    this.amount = amount;
    this.postBalance = account.getCash() + amount;
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Deposit: $%s -> $%s%s", Library.formatDate(time),
        FinLib.currencyFormatter.format(amount), FinLib.currencyFormatter.format(postBalance), getMemoString());
  }
}
