package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Fixed;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.TimeLib;
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
  private final Map<String, Long>     lastDivPaid  = new TreeMap<>();

  private long                        cash;

  private long                        cashSumForMonth;
  private int                         numDaysInMonth;

  public Account(Broker broker, Type type, boolean bReinvestDividends)
  {
    this.broker = broker;
    this.type = type;
    this.bReinvestDividends = bReinvestDividends;
    transactions.add(new TransactionOpen(this, broker.getTime()));
  }

  public void doEndOfDayBusiness(TimeInfo timeInfo, SequenceStore store)
  {
    payDividends(timeInfo, store);

    cashSumForMonth += cash;
    ++numDaysInMonth;
  }

  public void doEndOfMonthBusiness(TimeInfo timeInfo, SequenceStore store)
  {
    payInterest(timeInfo, store);
  }

  private void payDividends(TimeInfo timeInfo, SequenceStore store)
  {
    for (Position position : positions.values()) {
      String divName = position.name + "-dividends";
      if (store.hasMisc(divName)) {
        Sequence divs = store.getMisc(divName);
        int index = divs.getClosestIndex(timeInfo.time);
        long divTime = divs.getTimeMS(index);
        if (timeInfo.time >= divTime && TimeLib.isSameMonth(timeInfo.time, divTime)) {
          // Did we already pay this dividend?
          long lastDivTime = lastDivPaid.getOrDefault(position.name, TimeLib.TIME_ERROR);
          if (divTime > lastDivTime) {
            lastDivPaid.put(position.name, divTime);

            double div = divs.get(index, 0);
            long value = Math.round(position.getNumShares() * div);
            // System.out.printf("Dividend! %d = [%s] div=%.2f\n", index, TimeLib.formatDate(timeInfo.time), div);
            deposit(value, "Dividend Payment");
            if (bReinvestDividends) {
              buyValue(position.name, value, "Dividend Reinvestment");
            }
          }
        }
      }
    }
  }

  private void payInterest(TimeInfo timeInfo, SequenceStore store)
  {
    // No cash => no interest payment.
    assert cashSumForMonth >= 0;
    if (cashSumForMonth < 0) {
      return;
    }

    // Make sure we have interest rate data.
    if (!store.hasMisc("Interest-Rates")) {
      return;
    }

    Sequence rates = store.getMisc("Interest-Rates");
    int index = rates.getIndexAtOrBefore(timeInfo.time);
    double annualRate = rates.get(index, 0);
    double mul = FinLib.ret2mul(annualRate);

    // Convert annual multiplier to monthly multiplier.
    mul = Math.pow(mul, Library.ONE_TWELFTH);
    long avgCash = Math.round((double) cashSumForMonth / numDaysInMonth);
    long interest = Math.round(avgCash * (mul - 1.0));
    if (interest >= Fixed.PENNY) {
      // System.out.printf("Interest! $%s\n", Fixed.formatCurrency(interest));
      deposit(interest, "Interest");
    }

    // Reset accumulators.
    cashSumForMonth = 0;
    numDaysInMonth = 0;
  }

  public long getCash()
  {
    return cash;
  }

  public boolean isTaxDeferred()
  {
    return type == Type.Traditional || type == Type.Roth;
  }

  public long getValue()
  {
    long value = cash;
    for (Position position : positions.values()) {
      value += position.getValue();
    }
    return value;
  }

  public long getValue(String name)
  {
    if (name.equalsIgnoreCase("cash")) {
      return getCash();
    }
    Position position = positions.getOrDefault(name, null);
    return (position == null ? 0L : position.getValue());
  }

  private Position getPosition(String name)
  {
    assert positions.containsKey(name);
    return positions.get(name);
  }

  private void apply(TransactionDeposit deposit)
  {
    cash += deposit.amount;
    assert cash == deposit.postBalance;
  }

  private void apply(TransactionWithdraw withdraw)
  {
    cash -= withdraw.amount;
    assert cash >= 0;
    assert cash == withdraw.postBalance;
  }

  private void apply(TransactionBuy buy)
  {
    cash -= buy.getValue();
    assert cash >= 0;
    assert cash == buy.postBalance;

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
    cash += sell.getValue();
    assert cash == sell.postBalance;
  }

  public void deposit(long amount, String memo)
  {
    assert amount > 0;
    TransactionDeposit deposit = new TransactionDeposit(this, broker.getTime(), amount, memo);
    transactions.add(deposit);
    apply(deposit);
  }

  public void withdraw(long amount, String memo)
  {
    assert amount > 0;
    TransactionWithdraw withdraw = new TransactionWithdraw(this, broker.getTime(), amount, memo);
    transactions.add(withdraw);
    apply(withdraw);
  }

  public boolean buyShares(String name, long nShares, String memo)
  {
    // Only allowed to buy shares in units of 1/100.
    nShares = Fixed.truncate(nShares, Fixed.HUNDREDTH);
    if (nShares == 0) {
      return false;
    }
    assert nShares > 0;

    long price = broker.getPrice(name);
    assert Fixed.mul(price, nShares) <= cash;
    TransactionBuy buy = new TransactionBuy(this, broker.getTime(), name, nShares, memo);
    transactions.add(buy);
    apply(buy);
    return true;
  }

  public boolean buyValue(String name, long value, String memo)
  {
    assert value <= cash;
    long price = broker.getPrice(name);
    long nShares = Fixed.divTrunc(value, price);

    // Don't buy less than 1/10 share.
    if (nShares < Fixed.toFixed(0.1)) {
      return false;
    }

    return buyShares(name, nShares, memo);
  }

  public boolean sellShares(String name, long nShares, String memo)
  {
    // Only allowed to sell shares in units of 1/100.
    nShares = Fixed.truncate(nShares, Fixed.HUNDREDTH);
    if (nShares == 0) {
      return false;
    }
    assert nShares > 0;

    long time = broker.getTime();
    long price = broker.getPrice(name);
    Position position = getPosition(name);
    assert nShares <= position.getNumShares();

    Receipt receipt = position.sub(new PositionLot(name, time, nShares, price));
    receipts.add(receipt);

    if (position.getNumLots() == 0) {
      assert receipt.balance == 0;
      positions.remove(position.name);
    } else {
      assert receipt.balance > 0;
    }

    TransactionSell sell = new TransactionSell(this, time, name, nShares, memo);
    transactions.add(sell);
    apply(sell);
    return true;
  }

  public boolean sellValue(String name, long value, String memo)
  {
    Position position = positions.get(name);
    long positionValue = position.getValue();
    assert value <= positionValue;
    long nShares;
    if (value == positionValue) {
      nShares = position.getNumShares();
    } else {
      long price = broker.getPrice(name);
      nShares = Fixed.divTrunc(value, price);
    }
    return sellShares(name, nShares, memo);
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

  public void printBuySell()
  {
    for (Transaction transaction : transactions) {
      if ((transaction instanceof TransactionBuy || transaction instanceof TransactionSell)
          && !transaction.memo.contains("Interest") && !transaction.memo.contains("Dividend")) {
        System.out.println(transaction);

      }
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

  public void rebalance(Map<String, Double> targetDistribution)
  {
    final long preValue = getValue();
    // System.out.printf("Rebalance.pre: $%s (Price=$%s)\n", Fixed.formatCurrency(preValue),
    // Fixed.formatCurrency(broker.getPrice("Stock")));

    // Figure out how much money is currently invested.
    Map<String, Long> currentValue = new TreeMap<>();
    long total = 0L;
    double targetSum = 0.0;
    for (String name : targetDistribution.keySet()) {
      targetSum += targetDistribution.get(name);

      long value = 0L;
      if (name.equalsIgnoreCase("Cash")) {
        value = getCash();
      } else {
        Position position = positions.getOrDefault(name, null);
        if (position != null) {
          value = position.getValue();
        }
      }
      total += value;
      currentValue.put(name, value);
    }
    assert Math.abs(targetSum - 1.0) < 1e-8;
    assert total > 0;
    assert currentValue.size() == targetDistribution.size();
    assert total == preValue;

    // Sell positions that are over target.
    for (String name : targetDistribution.keySet()) {
      // System.out.printf("Target: %s = %.1f%%\n", name, targetDistribution.get(name) * 100.0 / targetSum);
      if (name.equalsIgnoreCase("Cash")) {
        continue;
      }
      double targetFrac = targetDistribution.get(name);
      long current = currentValue.get(name);

      // Only adjust if there is a change.
      long targetValue = Math.round(total * targetFrac);
      long sellValue = Math.min(getValue(name), current - targetValue);
      if (sellValue > 0) {
        // System.out.printf("Sell| %s: %.3f%% => Value=$%s\n", name, targetFrac, Fixed.formatCurrency(sellValue));
        sellValue(name, sellValue, null);
        assert preValue == getValue();
      }
    }

    // Buy positions that are under target.
    for (String name : targetDistribution.keySet()) {
      if (name.equalsIgnoreCase("Cash")) {
        continue;
      }
      double targetFrac = targetDistribution.get(name);
      long current = currentValue.get(name);

      // Only adjust if there is a change.
      long targetValue = Math.round(total * targetFrac);
      long buyValue = Math.min(getCash(), targetValue - current);
      if (buyValue > 0) {
        // System.out.printf("Buy| %s: %.3f%% => Value=$%s\n", name, targetFrac, Fixed.formatCurrency(buyValue));
        buyValue(name, buyValue, null);
        assert preValue == getValue();
      }
    }

    // Make sure we adjusted correctly (debug).
    assert preValue == getValue();
    for (String name : targetDistribution.keySet()) {
      double actual = Fixed.toFloat(Fixed.div(getValue(name), total));
      double target = targetDistribution.get(name);
      assert Math.abs(actual - target) < 0.01 : String.format("%f vs %f", actual, target);
    }
  }

  public void printPositions()
  {
    System.out.printf("Cash: $%s\n", Fixed.formatCurrency(cash));
    for (Position position : positions.values()) {
      System.out.printf("%s: $%s\n", position.name, Fixed.formatCurrency(position.getValue()));
    }
  }
}
