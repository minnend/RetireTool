package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.minnen.retiretool.Fixed;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

public class Broker
{
  private final List<Account>   accounts     = new ArrayList<>();
  public final SequenceStore    store;

  private TimeInfo              timeInfo;

  public final BrokerInfoAccess accessObject = new BrokerInfoAccess(this);

  public Broker(SequenceStore store, long time)
  {
    this.store = store;
    timeInfo = new TimeInfo(time, time, time);
  }

  public void setTime(long time, long prevTime, long nextTime)
  {
    timeInfo = new TimeInfo(time, prevTime, nextTime);
  }

  public long getTime()
  {
    return timeInfo.time;
  }

  public TimeInfo getTimeInfo()
  {
    return timeInfo;
  }

  public void doEndOfDayBusiness()
  {
    // End of day business.
    for (Account account : accounts) {
      account.doEndOfDayBusiness(timeInfo, store);
    }

    // End of month business.
    if (timeInfo.isLastDayOfMonth) {
      // System.out.printf("End of Month: [%s]\n", Library.formatDate(getTime()));
      for (Account account : accounts) {
        account.doEndOfMonthBusiness(timeInfo, store);
      }
    }
  }

  public Account openAccount(Account.Type accountType, boolean bReinvestDividends)
  {
    return openAccount(0L, accountType, bReinvestDividends);
  }

  public Account openAccount(long startingBalance, Account.Type accountType, boolean bReinvestDividends)
  {
    assert startingBalance >= 0L;

    Account account = new Account(this, accountType, bReinvestDividends);
    accounts.add(account);
    if (startingBalance > 0L) {
      account.deposit(startingBalance, "Initial Deposit");
    }
    return account;
  }

  public long getPrice(String name)
  {
    return getPrice(name, getTime());
  }

  public long getPrice(String name, long time)
  {
    assert time <= getTime(); // No peeking into the future.

    Sequence seq = store.getMisc(name);
    int index = seq.getClosestIndex(time);
    double floatPrice = seq.get(index, 0);
    long price = Fixed.round(Fixed.toFixed(floatPrice), Fixed.THOUSANDTH);
    return price;
  }
}
