package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.broker.transactions.Transaction.Flow;
import org.minnen.retiretool.util.Fixed;

public class TransactionWithdraw extends Transaction
{
  public final long amount;
  public final long postBalance;

  public TransactionWithdraw(Account account, long time, Flow flow, long amount, String memo)
  {
    super(account, time, flow, memo);
    assert amount > 0.0;
    this.amount = amount;
    this.postBalance = account.getCash() - amount;
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Withdraw: $%s -> $%s", Fixed.formatCurrency(amount), Fixed.formatCurrency(postBalance),
        getMemoString());
  }
}