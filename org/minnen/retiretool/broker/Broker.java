package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.Slippage;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;

public class Broker
{
  public final BrokerInfoAccess accessObject = new BrokerInfoAccess(this);
  public final SequenceStore    store;
  public final Slippage         slippage;

  private final List<Account>   accounts     = new ArrayList<>();
  private final long            originalTime;

  private TimeInfo              timeInfo;
  private int                   iPrice       = FinLib.Close;

  public Broker(SequenceStore store, Slippage slippage, long time)
  {
    this.store = store;
    this.originalTime = time;
    this.slippage = slippage;
    timeInfo = new TimeInfo(time);
  }

  public void reset()
  {
    accounts.clear();
    store.unlock();
    timeInfo = new TimeInfo(originalTime);
  }

  public int numAccounts()
  {
    return accounts.size();
  }

  public Account getAccount(int i)
  {
    return accounts.get(i);
  }

  public void setPriceIndex(int index)
  {
    // TODO better if price index was automatically selected based on time of day.
    iPrice = index;
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

    Sequence seq = store.get(name);
    int index = seq.getClosestIndex(time);
    double floatPrice = seq.get(index, iPrice);
    long price = Fixed.round(Fixed.toFixed(floatPrice), Fixed.THOUSANDTH);
    return price;
  }

  public long getBuyPrice(String name)
  {
    return slippage.applyToBuy(getPrice(name));
  }

  public long getSellPrice(String name)
  {
    return slippage.applyToSell(getPrice(name));
  }
}
