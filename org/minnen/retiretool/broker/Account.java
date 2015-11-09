package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.broker.transactions.Transaction;
import org.minnen.retiretool.broker.transactions.TransactionBuy;
import org.minnen.retiretool.broker.transactions.TransactionDeposit;
import org.minnen.retiretool.broker.transactions.TransactionOpen;
import org.minnen.retiretool.broker.transactions.TransactionSell;
import org.minnen.retiretool.broker.transactions.TransactionWithdraw;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

public class Account
{
  public enum Type {
    Taxable, Traditional, Roth
  };

  public final Broker                 broker;
  public final Type                   type;
  private final boolean               bReinvestDividends;
  private final List<Transaction>     transactions = new ArrayList<>();
  private final Map<String, Position> positions    = new TreeMap<>();
  private final List<Receipt>         receipts     = new ArrayList<>();

  private double                      cash;

  private double                      cashSumForMonth;
  private int                         numDaysInMonth;

  public Account(Broker broker, Type type, boolean bReinvestDividends)
  {
    this.broker = broker;
    this.type = type;
    this.bReinvestDividends = bReinvestDividends;
    transactions.add(new TransactionOpen(this, broker.getTime()));
  }

  public void doEndOfDayBusiness(long time, SequenceStore store)
  {
    payDividends(time, store);

    cashSumForMonth += cash;
    ++numDaysInMonth;
  }

  public void doEndOfMonthBusiness(long time, SequenceStore store)
  {
    payInterest(time, store);
  }

  private void payDividends(long time, SequenceStore store)
  {
    for (Position position : positions.values()) {
      String divName = position.name + "-Dividends";
      if (store.hasMisc(divName)) {
        Sequence divs = store.getMisc(divName);
        int index = divs.getClosestIndex(time);
        if (Library.isSameDay(time, divs.getTimeMS(index))) {
          double div = divs.get(index, 0);
          double value = position.getNumShares() * div;
          deposit(value, "Dividend Payment");
          if (bReinvestDividends) {
            buyValue(position.name, value, "Dividend Reinvestment");
          }
        }
      }
    }
  }

  private void payInterest(long time, SequenceStore store)
  {
    // No cash => no interest payment.
    if (FinLib.compareCash(cashSumForMonth, 0.0) <= 0) {
      return;
    }

    // Make sure we have interest rate data.
    if (!store.hasMisc("Interest-Rates")) {
      return;
    }

    Sequence rates = store.getMisc("Interest-Rates");
    int index = rates.getIndexAtOrBefore(time);
    double annualRate = rates.get(index, 0);
    double mul = FinLib.ret2mul(annualRate);

    // Convert annual multiplier to monthly multiplier.
    mul = Math.pow(mul, Library.ONE_TWELFTH);
    double avgCash = cashSumForMonth / numDaysInMonth;
    deposit(avgCash * (mul - 1.0), "Interest");

    // Reset accumulators.
    cashSumForMonth = 0.0;
    numDaysInMonth = 0;
  }

  public double getCash()
  {
    return cash;
  }

  public boolean isTaxDeferred()
  {
    return type == Type.Traditional || type == Type.Roth;
  }

  public double getValue()
  {
    long time = broker.getTime();
    double value = cash;
    for (Position position : positions.values()) {
      double price = broker.getPrice(position.name, time);
      value += position.getNumShares() * price;
    }
    return value;
  }

  private Position getPosition(String name)
  {
    assert positions.containsKey(name);
    return positions.get(name);
  }

  private void apply(TransactionDeposit deposit)
  {
    cash += deposit.amount;
    assert FinLib.equiv(cash, deposit.postBalance);
  }

  private void apply(TransactionWithdraw withdraw)
  {
    cash -= withdraw.amount;
    assert FinLib.equiv(cash, withdraw.postBalance);
  }

  private void apply(TransactionBuy buy)
  {
    cash -= buy.getValue();
    assert FinLib.equiv(cash, buy.postBalance);

    Position position = positions.getOrDefault(buy.name, null);
    if (position == null) {
      position = new Position(this, buy.name);
      positions.put(buy.name, position);
    }
    PositionLot lot = new PositionLot(buy.name, buy.time, buy.nShares, buy.price);
    position.add(lot);
  }

  private void apply(TransactionSell sell)
  {
    cash -= sell.getValue();
    assert FinLib.equiv(cash, sell.postBalance);
  }

  public void deposit(double amount, String memo)
  {
    assert amount > 0.0;
    TransactionDeposit deposit = new TransactionDeposit(this, broker.getTime(), amount, memo);
    transactions.add(deposit);
    apply(deposit);
  }

  public void withdraw(double amount, String memo)
  {
    assert amount > 0.0;
    TransactionWithdraw withdraw = new TransactionWithdraw(this, broker.getTime(), amount, memo);
    transactions.add(withdraw);
    apply(withdraw);
  }

  public void buyShares(String name, double nShares, String memo)
  {
    double price = broker.getPrice(name, broker.getTime());
    double value = price * nShares;
    assert FinLib.compareCash(value, cash) <= 0;
    TransactionBuy buy = new TransactionBuy(this, broker.getTime(), name, nShares, memo);
    transactions.add(buy);
    apply(buy);
  }

  public void buyValue(String name, double value, String memo)
  {
    assert FinLib.compareCash(value, cash) <= 0;
    double price = broker.getPrice(name, broker.getTime());
    buyShares(name, value / price, memo);
  }

  public void sellShares(String name, double nShares, String memo)
  {
    final long time = broker.getTime();
    double price = broker.getPrice(name, time);
    Position position = getPosition(name);
    assert nShares <= position.getNumShares();

    Receipt receipt = position.sub(new PositionLot(name, time, nShares, price));
    receipts.add(receipt);

    TransactionSell sell = new TransactionSell(this, broker.getTime(), name, nShares, memo);
    transactions.add(sell);
    apply(sell);
  }

  public void sellValue(String name, double value, String memo)
  {
    final long time = broker.getTime();
    final double price = broker.getPrice(name, time);
    sellShares(name, value / price, memo);
  }

  public void sellAll(String name, String memo)
  {
    Position position = getPosition(name);
    sellShares(name, position.getNumShares(), memo);
  }

  public void printTransactions()
  {
    for (Transaction transaction : transactions) {
      System.out.println(transaction);
    }
  }

  public void printTransactions(long from, long to)
  {
    for (Transaction transaction : transactions) {
      if (transaction.time < from) {
        continue;
      }
      if (transaction.time > to) {
        break;
      }
      System.out.println(transaction);
    }
  }
}
