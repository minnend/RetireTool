package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.Fixed;
import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.broker.Account;

public class TransactionDeposit extends Transaction
{
  public final long amount;
  public final long postBalance;

  public TransactionDeposit(Account account, long time, long amount, String memo)
  {
    super(account, time, memo);
    assert amount > 0.0;
    this.amount = amount;
    this.postBalance = account.getCash() + amount;
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Deposit: $%s -> $%s%s", TimeLib.formatDate(time),
        Fixed.formatCurrency(amount), Fixed.formatCurrency(postBalance), getMemoString());
  }
}
