package org.minnen.retiretool.broker;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.Slippage;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.TimeLib;

/**
 * Represents a trading broker at which a strategy can open accounts and make trades.
 */
public class Broker
{
  public static class PriceQuote
  {
    public final long time;
    public final long price;

    public PriceQuote(long time, long price)
    {
      this.time = time;
      this.price = price;
    }
  }

  public enum TimeOfDay {
    MarketOpen, AfterMarketClosed;
  }

  public final BrokerInfoAccess         accessObject = new BrokerInfoAccess(this);
  public final SequenceStore            store;

  private final List<Account>           accounts     = new ArrayList<>();
  private final Map<String, PriceQuote> priceQuotes  = new HashMap<>();
  private final TimeInfo                origTimeInfo;

  private TimeInfo                      timeInfo;
  private Slippage                      slippage;

  /** Price model used to give a quote while the market is open. */
  private PriceModel                    quoteModel;

  /** Price model used when the market is closed (or when the request is for a day other than "today"). */
  private PriceModel                    valueModel;

  /** The broker works with daily data, but we still want to distinguish market-open vs. after-market-close. */
  private TimeOfDay                     timeOfDay;

  public Broker(SequenceStore store, Slippage slippage, Sequence guideSeq)
  {
    this(store, PriceModel.closeModel, PriceModel.closeModel, slippage, guideSeq);
  }

  public Broker(SequenceStore store, PriceModel valueModel, PriceModel quoteModel, Slippage slippage, Sequence guideSeq)
  {
    this.store = store;
    this.valueModel = valueModel;
    this.quoteModel = quoteModel;
    this.slippage = slippage;
    this.origTimeInfo = new TimeInfo(0, guideSeq);
    this.timeInfo = origTimeInfo;

    System.out.printf("Broker: value=%s   quote=%s\n", valueModel, quoteModel);
  }

  public Broker(SequenceStore store, Slippage slippage, long timeStart)
  {
    this(store, PriceModel.closeModel, PriceModel.closeModel, slippage, timeStart);
  }

  public Broker(SequenceStore store, PriceModel valueModel, PriceModel quoteModel, Slippage slippage, long timeStart)
  {
    this.store = store;
    this.valueModel = valueModel;
    this.quoteModel = quoteModel;
    this.slippage = slippage;
    this.origTimeInfo = new TimeInfo(timeStart);
    this.timeInfo = origTimeInfo;
  }

  public void reset()
  {
    accounts.clear();
    priceQuotes.clear();
    timeInfo = origTimeInfo;
    timeOfDay = TimeOfDay.MarketOpen;
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

  public void setQuoteModel(PriceModel quoteModel)
  {
    this.quoteModel = quoteModel;
  }

  public void setValueModel(PriceModel valueModel)
  {
    this.valueModel = valueModel;
  }

  public void setPriceModels(PriceModel valueModel, PriceModel quoteModel)
  {
    setQuoteModel(quoteModel);
    setValueModel(valueModel);
  }

  public void setNewDay(TimeInfo timeInfo)
  {
    this.timeInfo = timeInfo;
    timeOfDay = TimeOfDay.MarketOpen;
  }

  public long getTime()
  {
    return timeInfo.time;
  }

  public TimeInfo getTimeInfo()
  {
    return timeInfo;
  }

  public TimeOfDay getTimeOfDay()
  {
    return timeOfDay;
  }

  /**
   * Handle business at end of day (E.g. pay dividends and interest).
   * 
   * A strategy may access the broker for the current day after this point. For example, dividends and interest are paid
   * at the end of the day, and then a strategy might analyze the situation to make predictions and decide on buy/sell
   * orders for tomorrow.
   */
  public void doEndOfDayBusiness()
  {
    // End of day means the market is now closed.
    timeOfDay = TimeOfDay.AfterMarketClosed;

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

  /**
   * Finish any administrative / infrastructure work for the current day.
   * 
   * Nothing should access the broker for the current day after this call.
   */
  public void finishDay()
  {
    assert timeOfDay == TimeOfDay.AfterMarketClosed; // Dont't "finish" until after end-of-day business.

    // Price quotes are only valid for one day.
    priceQuotes.clear();
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

  /** @return Price for current broker time (today). */
  public long getPrice(String name)
  {
    return getPrice(name, getTime());
  }

  /**
   * Return the price for the given asset and day.
   * 
   * @param name return price for this asset.
   * @param time return price for this day.
   * @return Price for the given day (and time-of-day if today).
   */
  public long getPrice(String name, long time)
  {
    // Price requests depend on the day, not the time so all times should be exactly at noon.
    LocalDateTime dateTime = TimeLib.ms2time(time);
    assert dateTime.getHour() == 12 && dateTime.getMinute() == 0 && dateTime.getSecond() == 0
        && dateTime.getNano() == 0;

    // TODO for debug
    // assert time <= getTime(); // No peeking into the future.
    if (time == timeInfo.time && timeOfDay == TimeOfDay.MarketOpen) {
      // Price quotes may be semi-random but we want the same value for each request.
      PriceQuote quote = priceQuotes.getOrDefault(name, null);
      if (quote != null) {
        assert quote.time == time : String.format("%s: [%s] vs [%s]", name, TimeLib.formatTime(time),
            TimeLib.formatTime(quote.time));
        return quote.price;
      } else {
        // Price quote is not cached so calculate it.
        long price = getPrice(name, quoteModel, time);
        priceQuotes.put(name, new PriceQuote(time, price));
        return price;
      }
    } else {
      // After the market is closed, report the "value" price (typically Close or AdjClose).
      assert timeOfDay == TimeOfDay.AfterMarketClosed;
      return getPrice(name, valueModel, time);
    }
  }

  /**
   * Calculate and return the price for the given asset at the given time using the given price model.
   * 
   * This function does not use cached values and does not cache the result internally.
   */
  private long getPrice(String name, PriceModel priceModel, long time)
  {
    Sequence seq = store.get(name);
    int index = seq.getClosestIndex(time);
    double floatPrice = priceModel.getPrice(seq.get(index));
    return Fixed.round(Fixed.toFixed(floatPrice), Fixed.THOUSANDTH);
  }

  /** @return Price to buy the given asset today (includes slippage) */
  public long getBuyPrice(String name)
  {
    return slippage.applyToBuy(getPrice(name));
  }

  /** @return Price to sell the given asset today (includes slippage) */
  public long getSellPrice(String name)
  {
    return slippage.applyToSell(getPrice(name));
  }
}
