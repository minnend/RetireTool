package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.minnen.retiretool.Library;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

public class Broker
{
  private long                time     = Library.TIME_ERROR;
  private final List<Account> accounts = new ArrayList<>();
  private final SequenceStore store;

  public Broker(SequenceStore store, long timeMS)
  {
    this.store = store;
    this.time = timeMS;
  }

  public void setTime(long time)
  {
    this.time = time;
  }

  public long getTime()
  {
    return time;
  }

  public void doEndOfDayBusiness(long nextTime)
  {
    assert nextTime > time;

    // End of day business.
    for (Account account : accounts) {
      account.doEndOfDayBusiness(time, store);
    }

    // End of month business.
    int month1 = Library.calFromTime(time).get(Calendar.MONTH);
    int month2 = Library.calFromTime(nextTime).get(Calendar.MONTH);
    assert (month1 == month2) || (month1 < 11 && month2 == month1 + 1) || (month1 == 11 && month2 == 0);
    if (month2 != month1) {
      for (Account account : accounts) {
        account.doEndOfMonthBusiness(time, store);
      }
    }
  }

  public Account openAccount(double startingBalance, Account.Type accountType, boolean bReinvestDividends)
  {
    assert startingBalance >= 0.0;

    Account account = new Account(this, accountType, bReinvestDividends);
    accounts.add(account);
    account.deposit(startingBalance, "Initial Deposit");
    return account;
  }

  public double getPrice(String name, long time)
  {
    Sequence seq = store.getMisc(name);
    int index = seq.getClosestIndex(time);
    return seq.get(index, 0);
  }
}
