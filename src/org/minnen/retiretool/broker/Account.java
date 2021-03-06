package org.minnen.retiretool.broker;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.broker.transactions.Transaction;
import org.minnen.retiretool.broker.transactions.Transaction.Flow;
import org.minnen.retiretool.broker.transactions.TransactionBuy;
import org.minnen.retiretool.broker.transactions.TransactionDeposit;
import org.minnen.retiretool.broker.transactions.TransactionOpen;
import org.minnen.retiretool.broker.transactions.TransactionSell;
import org.minnen.retiretool.broker.transactions.TransactionWithdraw;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

public class Account
{
  public enum Type {
    Taxable, Traditional, Roth
  };

  public final String                 name;
  public final Broker                 broker;
  public final Type                   type;
  private final boolean               bReinvestDividends;
  private final List<Transaction>     transactions = new ArrayList<>();
  private final Map<String, Position> positions    = new TreeMap<>();
  private final List<Receipt>         receipts     = new ArrayList<>();
  private final Map<String, Long>     lastDivPaid  = new TreeMap<>();
  private final Map<Long, Long>       valueAtTime  = new TreeMap<>();

  private long                        cash;

  private long                        cashSumForMonth;
  private int                         numDaysForCashSum;

  public Account(String name, Broker broker, Type type, boolean bReinvestDividends)
  {
    this.name = name;
    this.broker = broker;
    this.type = type;
    this.bReinvestDividends = bReinvestDividends;
    transactions.add(new TransactionOpen(this, broker.getTime()));
  }

  public void doEndOfDayBusiness(TimeInfo timeInfo, SequenceStore store)
  {
    payDividends(timeInfo, store);
    valueAtTime.put(timeInfo.time, getValue());

    cashSumForMonth += cash;
    ++numDaysForCashSum;
  }

  public void doEndOfMonthBusiness(TimeInfo timeInfo, SequenceStore store)
  {
    payInterest(timeInfo, store);
  }

  private void payDividends(TimeInfo timeInfo, SequenceStore store)
  {
    for (Position position : positions.values()) {
      String divName = position.name + "-dividends";
      Sequence divs = store.tryGet(divName);
      if (divs == null) continue;

      int index = divs.getClosestIndex(timeInfo.time);
      long divTime = divs.getTimeMS(index);
      if (timeInfo.time >= divTime && TimeLib.isSameMonth(TimeLib.ms2date(timeInfo.time), TimeLib.ms2date(divTime))) {
        // Did we already pay this dividend?
        long lastDivTime = lastDivPaid.getOrDefault(position.name, TimeLib.TIME_ERROR);
        if (divTime > lastDivTime) {
          lastDivPaid.put(position.name, divTime);

          double div = divs.get(index, 0);
          long value = Math.round(position.getNumShares() * div);
          // System.out.printf("Dividend! %d = [%s] div=%.2f\n", index, TimeLib.formatDate(timeInfo.time), div);
          deposit(value, Flow.Internal, "Dividend Payment");
          if (bReinvestDividends) {
            buyValue(position.name, value, "Dividend Reinvestment");
          }
        }
      }
    }
  }

  private boolean payInterest(TimeInfo timeInfo, SequenceStore store)
  {
    // No cash => no interest payment.
    assert cashSumForMonth >= 0;
    if (cashSumForMonth <= 0) {
      numDaysForCashSum = 0;
      return true;
    }

    // Make sure we have interest rate data.
    Sequence rates = store.tryGet("interest-rates");
    if (rates == null) {
      numDaysForCashSum = 0;
      return false;
    }

    int index = rates.getIndexAtOrBefore(timeInfo.time);
    double annualRate = rates.get(index, 0);
    double mul = FinLib.ret2mul(annualRate);

    // Convert annual multiplier to monthly multiplier.
    mul = Math.pow(mul, Library.ONE_TWELFTH);
    int nBusinessDaysInMonth = TimeLib.getNumBusinessDays(YearMonth.from(timeInfo.date));
    assert nBusinessDaysInMonth >= numDaysForCashSum : String.format("nbdim=%d, ndfcs=%d", nBusinessDaysInMonth,
        numDaysForCashSum);
    long avgCash = Math.round((double) cashSumForMonth / nBusinessDaysInMonth);
    long interest = Math.round(avgCash * (mul - 1.0));
    if (interest >= Fixed.PENNY) {
      // System.out.printf("Interest! $%s\n", Fixed.formatCurrency(interest));
      deposit(interest, Flow.Internal, String.format("Interest=%.2f%%", annualRate));
    }

    // Reset accumulators.
    cashSumForMonth = 0;
    numDaysForCashSum = 0;
    return true;
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

  public long getValue(long time)
  {
    return valueAtTime.get(time);
  }

  public long getValue(String name)
  {
    if (name.equals("cash")) {
      return getCash();
    }
    Position position = positions.getOrDefault(name, null);
    return (position == null ? 0L : position.getValue());
  }

  public String[] getPositionNames()
  {
    return positions.keySet().toArray(new String[positions.size()]);
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

  public void deposit(long amount, Transaction.Flow flow, String memo)
  {
    assert amount > 0;
    TransactionDeposit deposit = new TransactionDeposit(this, broker.getTime(), flow, amount, memo);
    transactions.add(deposit);
    apply(deposit);
  }

  public void withdraw(long amount, Transaction.Flow flow, String memo)
  {
    assert amount > 0;
    TransactionWithdraw withdraw = new TransactionWithdraw(this, broker.getTime(), flow, amount, memo);
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

    long price = broker.getBuyPrice(name);
    assert Fixed.mul(price, nShares) <= cash;
    TransactionBuy buy = new TransactionBuy(this, broker.getTime(), name, nShares, price, memo);
    transactions.add(buy);
    apply(buy);
    return true;
  }

  public boolean buyValue(String name, long value, String memo)
  {
    assert value <= cash;
    long price = broker.getBuyPrice(name);
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
    long price = broker.getSellPrice(name);
    Position position = getPosition(name);
    final long nSharesHeld = position.getNumShares();
    assert nShares <= nSharesHeld;
    // System.out.printf("Sell %.2f @ $%s = $%s\n", Fixed.toFloat(nShares), Fixed.formatCurrency(price),
    // Fixed.formatCurrency(Fixed.mul(nShares, price)));

    Receipt receipt = position.sub(new PositionLot(name, time, nShares, price));
    receipts.add(receipt);

    if (position.getNumLots() == 0) {
      assert receipt.balance == 0;
      positions.remove(position.name);
    } else {
      assert receipt.balance > 0;
    }

    TransactionSell sell = new TransactionSell(this, time, name, nSharesHeld, nShares, price, memo);
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
      long price = broker.getSellPrice(name);
      nShares = Math.min(Fixed.divTrunc(value, price), position.getNumShares());
    }
    // System.out.printf("shares: %.2f / %.2f  value=$%s\n", Fixed.toFloat(nShares),
    // Fixed.toFloat(position.getNumShares()), Fixed.formatCurrency(value));
    assert nShares <= position.getNumShares();
    return sellShares(name, nShares, memo);
  }

  public void sellAll(String name, String memo)
  {
    Position position = getPosition(name);
    sellShares(name, position.getNumShares(), memo);
  }

  /** Sell all positions. */
  public void liquidate(String memo)
  {
    // Make copy of positions to avoid concurrent modification.
    String[] names = positions.keySet().toArray(new String[positions.size()]);
    for (String name : names) {
      sellAll(name, memo);
    }
  }

  public DiscreteDistribution getDistribution()
  {
    String[] names = positions.keySet().toArray(new String[positions.size() + 1]);
    names[names.length - 1] = "cash";
    Arrays.sort(names);
    return getDistribution(names);
  }

  public DiscreteDistribution getDistribution(String[] names)
  {
    final long totalValue = getValue();
    DiscreteDistribution dist = new DiscreteDistribution(names);
    for (String name : names) {
      long value = getValue(name);
      double weight = Fixed.toFloat(Fixed.div(value, totalValue));
      dist.set(name, weight);
    }
    return dist;
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
      if (transaction instanceof TransactionBuy || transaction instanceof TransactionSell) {
        System.out.println(transaction);
      }
    }
  }

  public void printInOutFlow()
  {
    for (Transaction transaction : transactions) {
      if (transaction.flow == Flow.InFlow || transaction.flow == Flow.OutFlow) {
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

  public void updatePositions(DiscreteDistribution targetDistribution)
  {
    assert targetDistribution.isNormalized();
    // targetDistribution.clean();

    final long totalValue = getValue();
    // System.out.printf("Rebalance: [%s]\n", TimeLib.formatDate(broker.getTime()));
    // System.out.printf("Rebalance: %s\n", targetDistribution);

    // Sell positions that are over target.
    for (String name : getPositionNames()) {
      double targetFrac = targetDistribution.weight(name);
      assert targetFrac >= 0.0 : String.format("%s: %f", name, targetFrac);
      long currentValue = getValue(name);

      // Only adjust if there is a change.
      long targetValue = Math.round(totalValue * targetFrac);
      long sellValue = Math.min(currentValue, currentValue - targetValue);
      // System.out.printf("Target: %-5s  %f%% ($%s -> $%s => $%s)\n", name, targetFrac * 100.0,
      // Fixed.formatCurrency(currentValue), Fixed.formatCurrency(targetValue), Fixed.formatCurrency(sellValue));
      if (sellValue > 0) {
        // System.out.printf("Sell| %s: %f%% => Value=$%s\n", name, targetFrac * 100.0,
        // Fixed.formatCurrency(sellValue));
        sellValue(name, sellValue, null);
        // assert totalValue == getValue(); // Not true with slippage
      }
    }

    // Buy positions that are under target.
    for (int i = 0; i < targetDistribution.size(); ++i) {
      String name = targetDistribution.names[i];
      if (name.equals("cash")) continue;

      double targetFrac = targetDistribution.weights[i];
      long currentValue = getValue(name);

      // Only adjust if there is a change.
      long targetValue = Math.round(totalValue * targetFrac);
      long buyValue = Math.min(getCash(), targetValue - currentValue);
      if (buyValue > 0) {
        // System.out.printf("Buy| %s: %.3f%% => Value=$%s\n", name, targetFrac, Fixed.formatCurrency(buyValue));
        buyValue(name, buyValue, null);
        // assert totalValue == getValue(); // Not true with slippage
      }
    }
    // assert totalValue == getValue(); // Not true with slippage
  }

  public void printPositions()
  {
    System.out.printf("Cash: $%s\n", Fixed.formatCurrency(cash));
    for (Position position : positions.values()) {
      System.out.printf("%s: $%s\n", position.name, Fixed.formatCurrency(position.getValue()));
    }
  }
}
