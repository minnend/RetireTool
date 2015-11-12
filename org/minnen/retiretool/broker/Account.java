package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.FixedPoint;
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
      String divName = position.name + "-Dividends";
      if (store.hasMisc(divName)) {
        Sequence divs = store.getMisc(divName);
        int index = divs.getClosestIndex(timeInfo.time);
        long divTime = divs.getTimeMS(index);
        if (timeInfo.time >= divTime && Library.isSameMonth(timeInfo.time, divTime)) {
          // Did we already pay this dividend?
          long lastDivTime = lastDivPaid.getOrDefault(position.name, Library.TIME_ERROR);
          if (divTime > lastDivTime) {
            lastDivPaid.put(position.name, divTime);

            long div = FixedPoint.toFixed(divs.get(index, 0));
            long value = position.getNumShares() * div;
            System.out.printf("Dividend! %d = [%s] div=%.2f\n", index, Library.formatDate(timeInfo.time), div);
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
    long avgCash = cashSumForMonth / numDaysInMonth;
    long interest = Math.round(avgCash * (mul - 1.0));
    if (interest > 0) {
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
    assert cash == withdraw.postBalance;
  }

  private void apply(TransactionBuy buy)
  {
    cash -= buy.getValue();
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
    assert amount > 0.0;
    TransactionDeposit deposit = new TransactionDeposit(this, broker.getTime(), amount, memo);
    transactions.add(deposit);
    apply(deposit);
  }

  public void withdraw(long amount, String memo)
  {
    assert amount > 0.0;
    TransactionWithdraw withdraw = new TransactionWithdraw(this, broker.getTime(), amount, memo);
    transactions.add(withdraw);
    apply(withdraw);
  }

  public void buyShares(String name, long nShares, String memo)
  {
    long price = broker.getPrice(name, broker.getTime());
    long value = price * nShares;
    assert value <= cash;
    TransactionBuy buy = new TransactionBuy(this, broker.getTime(), name, nShares, memo);
    transactions.add(buy);
    apply(buy);
  }

  public void buyValue(String name, long value, String memo)
  {
    assert value <= cash;
    long price = broker.getPrice(name, broker.getTime());
    buyShares(name, value / price, memo);
  }

  public void sellShares(String name, long nShares, String memo)
  {
    final long time = broker.getTime();
    long price = broker.getPrice(name, time);
    Position position = getPosition(name);
    assert nShares < position.getNumShares() + 1e-4;

    Receipt receipt = position.sub(new PositionLot(name, time, nShares, price));
    receipts.add(receipt);

    if (position.getNumLots() == 0) {
      assert receipt.balance < 1e-4;
      positions.remove(position.name);
    } else {
      assert receipt.balance > 0.0;
    }

    TransactionSell sell = new TransactionSell(this, broker.getTime(), name, nShares, memo);
    transactions.add(sell);
    apply(sell);
  }

  public void sellValue(String name, long value, String memo)
  {
    final long time = broker.getTime();
    final long price = broker.getPrice(name, time);
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

  public void rebalance(Map<String, Long> targetDistribution)
  {
    final long preValue = getValue();

    // Figure out how much money is currently invested.
    Map<String, Long> currentValue = new TreeMap<>();
    long total = 0L;
    long targetSum = 0L;
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
    assert targetSum > 0;
    assert total > 0;
    assert currentValue.size() == targetDistribution.size();

    // Sell positions that are over target.
    for (String name : targetDistribution.keySet()) {
      System.out.printf("Target: %s = %.1f%%\n", name, targetDistribution.get(name) * 100.0 / targetSum);
      if (name.equalsIgnoreCase("Cash")) {
        continue;
      }
      long targetFrac = targetDistribution.get(name) / targetSum;
      long current = currentValue.get(name);
      long currentFrac = FixedPoint.div(current, total);

      // Don't adjust for small changes (1e-3 => 0.1%)
      long fracSell = currentFrac - targetFrac;
      if (fracSell > 1e-3) {
        System.out.printf("Sell| %s: %.3f -> %.3f\n", name, FixedPoint.toFloat(currentFrac),
            FixedPoint.toFloat(targetFrac));
        long sellValue = FixedPoint.mul(fracSell, total);
        sellValue(name, sellValue, null);
      }
    }

    // Buy positions that are under target.
    for (String name : targetDistribution.keySet()) {
      if (name.equalsIgnoreCase("Cash")) {
        continue;
      }
      long targetFrac = FixedPoint.div(targetDistribution.get(name), targetSum);
      long current = currentValue.get(name);
      long currentFrac = FixedPoint.div(current, total);

      // Don't adjust for small changes (1e-3 => 0.1%)
      long fracBuy = targetFrac - currentFrac;
      if (fracBuy > 0) {
        System.out.printf("Buy| %s: %.3f -> %.3f\n", name, FixedPoint.toFloat(currentFrac),
            FixedPoint.toFloat(targetFrac));
        long buyValue = FixedPoint.mul(fracBuy, total);
        buyValue(name, buyValue, null);
      }
    }

    // Make sure we adjusted correctly (debug).
    assert preValue == getValue();
    for (String name : targetDistribution.keySet()) {
      long value = 0L;
      if (name.equalsIgnoreCase("Cash")) {
        value = getCash();
      } else {
        Position position = positions.getOrDefault(name, null);
        if (position != null) {
          value = position.getValue();
        }
      }
      assert FixedPoint.div(value, total) == targetDistribution.get(name);
    }
  }

  public void printPositions()
  {
    System.out.printf("Cash: $%s\n", FinLib.currencyFormatter.format(cash));
    for (Position position : positions.values()) {
      System.out.printf("%s: $%s\n", position.name, FinLib.currencyFormatter.format(position.getValue()));
    }
  }
}
