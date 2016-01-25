package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.Slippage;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.util.Fixed;

/**
 * Represents a trading broker at which a strategy can open accounts and make trades.
 */
public class Broker
{
  public final BrokerInfoAccess accessObject = new BrokerInfoAccess(this);
  public final SequenceStore    store;

  private final List<Account>   accounts     = new ArrayList<>();
  private final TimeInfo        origTimeInfo;

  private TimeInfo              timeInfo;
  private Slippage              slippage;
  private PriceModel            priceModel;

  public Broker(SequenceStore store, Slippage slippage, Sequence guideSeq)
  {
    this(store, PriceModel.closeModel, slippage, guideSeq);
  }

  public Broker(SequenceStore store, PriceModel priceModel, Slippage slippage, Sequence guideSeq)
  {
    this.store = store;
    this.priceModel = priceModel;
    this.slippage = slippage;
    this.origTimeInfo = new TimeInfo(0, guideSeq);
    this.timeInfo = origTimeInfo;
  }

  public void reset()
  {
    accounts.clear();
    timeInfo = origTimeInfo;
  }

  public int numAccounts()
  {
    return accounts.size();
  }

  public Account getAccount(String name)
  {
    for (Account account : accounts) {
      if (account.name.equals(name)) {
        return account;
      }
    }
    return null;
  }

  public void setPriceModel(PriceModel priceModel)
  {
    // TODO better if price index was automatically selected based on time of day.
    this.priceModel = priceModel;
  }

  public void setTime(TimeInfo timeInfo)
  {
    this.timeInfo = timeInfo;
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

  public Account openAccount(String name, Account.Type accountType, boolean bReinvestDividends)
  {
    return openAccount(name, 0L, accountType, bReinvestDividends);
  }

  public Account openAccount(String name, long startingBalance, Account.Type accountType, boolean bReinvestDividends)
  {
    assert startingBalance >= 0L;

    Account account = new Account(name, this, accountType, bReinvestDividends);
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
    double floatPrice = priceModel.getPrice(seq.get(index));
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
