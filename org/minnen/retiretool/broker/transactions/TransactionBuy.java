package org.minnen.retiretool.broker.transactions;

import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.broker.transactions.Transaction.Flow;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.TimeLib;

public class TransactionBuy extends Transaction
{
  public final String name;
  public final long   nShares;
  public final long   price;
  public final long   postBalance;

  public TransactionBuy(Account account, long time, String name, long nShares, long price, String memo)
  {
    super(account, time, Flow.Internal, memo);
    this.name = name;
    this.nShares = nShares;
    this.price = price;
    this.postBalance = account.getCash() - getValue();
  }

  public long getValue()
  {
    return Fixed.mul(nShares, price);
  }

  @Override
  public String toString()
  {
    return String.format("%11s| Buy: %s %.2f @ $%s ($%s)%s", TimeLib.formatDate(time), name, Fixed.toFloat(nShares),
        Fixed.formatCurrency(price), Fixed.formatCurrency(getValue()), getMemoString());
  }
}
