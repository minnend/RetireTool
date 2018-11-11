package org.minnen.retiretool.tactical;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.broker.transactions.Transaction.Flow;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.IndexRange;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;

/**
 * Fork of Simulation with stripped down functionality to improve speed. In practice, only ~20% faster.
 */
public class FastSim
{
  public static final double DistributionEPS        = 0.02;
  public static final double TargetEPS              = 0.1;
  public static final int    REBALANCE_AFTER_N_DAYS = 363;

  public static final Random rng                    = new Random();
  public static final String AccountName            = "SimAccount";

  public final SequenceStore store;
  public final Sequence      guideSeq;
  public final Slippage      slippage;
  public final Broker        broker;

  private final double       startingBalance;
  private final double       monthlyDeposit;

  public Sequence            returnsDaily;
  public Sequence            returnsMonthly;

  private long               runKey;
  private int                runIndex               = -1;
  private Predictor          predictor;

  public FastSim(SequenceStore store, Sequence guideSeq)
  {
    this(store, guideSeq, Slippage.None, 10000.0, 0.0, PriceModel.adjCloseModel, PriceModel.adjCloseModel);
  }

  public FastSim(SequenceStore store, Sequence guideSeq, Slippage slippage, PriceModel valueModel,
      PriceModel quoteModel)
  {
    this(store, guideSeq, slippage, 10000.0, 0.0, valueModel, quoteModel);
  }

  public FastSim(SequenceStore store, Sequence guideSeq, Slippage slippage, double startingBalance,
      double monthlyDeposit, PriceModel valueModel, PriceModel quoteModel)
  {
    this.store = store;
    this.guideSeq = guideSeq;
    this.slippage = slippage;
    this.startingBalance = startingBalance;
    this.monthlyDeposit = monthlyDeposit;
    this.broker = new Broker(store, valueModel, quoteModel, slippage, guideSeq);
  }

  public long getStartMS()
  {
    return guideSeq.getStartMS();
  }

  public long getEndMS()
  {
    return guideSeq.getEndMS();
  }

  public long getSimTime()
  {
    return guideSeq.getTimeMS(runIndex);
  }

  public void setPredictor(Predictor predictor)
  {
    this.predictor = predictor;
  }

  public Sequence run(Predictor predictor)
  {
    return run(predictor, "Returns");
  }

  public Sequence run(Predictor predictor, String name)
  {
    return run(predictor, TimeLib.TIME_BEGIN, TimeLib.TIME_END, name);
  }

  public Sequence run(Predictor predictor, long timeStart, String name)
  {
    return run(predictor, timeStart, TimeLib.TIME_END, name);
  }

  public Sequence run(Predictor predictor, long timeStart, long timeEnd, String name)
  {
    setupRun(predictor, timeStart, timeEnd, name);
    runTo(timeEnd);
    finishRun();
    return returnsMonthly;
  }

  public void setupRun(Predictor predictor, long timeStart, long timeEnd, String name)
  {
    assert timeStart != TimeLib.TIME_ERROR && timeEnd != TimeLib.TIME_ERROR;
    predictor.setBroker(broker.accessObject);
    // System.out.printf("Run1: [%s] -> [%s] = [%s] -> [%s] (%d, %d)\n", TimeLib.formatDate(timeStart),
    // TimeLib.formatDate(timeEnd), TimeLib.formatDate(guideSeq.getStartMS()),
    // TimeLib.formatDate(guideSeq.getEndMS()), timeEnd, guideSeq.getStartMS());
    if (timeStart == TimeLib.TIME_BEGIN) {
      timeStart = guideSeq.getStartMS();
    }
    if (timeEnd == TimeLib.TIME_END) {
      timeEnd = guideSeq.getEndMS();
    }
    IndexRange range = guideSeq.getIndices(timeStart, timeEnd, EndpointBehavior.Closest);
    runKey = Sequence.Lock.genKey();
    guideSeq.lock(range.iStart, range.iEnd, runKey);
    runIndex = 0;
    this.predictor = predictor;

    returnsMonthly = new Sequence(name);
    returnsMonthly.addData(1.0, guideSeq.getStartMS());
    returnsDaily = new Sequence(name);
    returnsDaily.addData(1.0, guideSeq.getStartMS());

    broker.reset();
    broker.setNewDay(new TimeInfo(runIndex, guideSeq));
    broker.openAccount(AccountName, Fixed.toFixed(startingBalance), Account.Type.Roth, true);

    // TODO support prediction at start instead of on second tick
    // if (predictor != null) {
    // store.lock(TimeLib.TIME_BEGIN, guideSeq.getStartMS(), runKey);
    // DiscreteDistribution targetDist = predictor.selectDistribution();
    // }
  }

  private void buyTowardTargetAllocation(DiscreteDistribution targetDist, Account account)
  {
    if (targetDist == null) return;

    Map<String, Long> cashToAdd = new HashMap<>();
    long totalToAdd = Fixed.ZERO;
    DiscreteDistribution currentDist = account.getDistribution();
    long currentValue = account.getValue();
    for (int i = 0; i < targetDist.size(); ++i) {
      String name = targetDist.names[i];
      if (name.equals("cash")) continue;
      double targetWeight = targetDist.weights[i];
      int j = currentDist.find(name);
      double currentWeight = j < 0 ? 0.0 : currentDist.weights[j];
      double missingWeight = Math.max(targetWeight - currentWeight, 0.0);

      long valueNeeded = Math.round(currentValue * missingWeight);
      totalToAdd += valueNeeded;
      cashToAdd.put(name, valueNeeded);
    }

    final long cash = account.getCash();
    if (totalToAdd > cash) {
      long remaining = cash;
      for (Map.Entry<String, Long> entry : cashToAdd.entrySet()) {
        double percent = (double) entry.getValue() / totalToAdd;
        long adjustedValue = (long) Math.ceil(percent * cash);
        adjustedValue = Math.min(adjustedValue, remaining);
        assert adjustedValue >= 0;
        remaining -= adjustedValue;
        assert remaining >= 0;
        entry.setValue(adjustedValue);
      }
    } else if (totalToAdd < cash) {
      long totalExcess = cash - totalToAdd;
      long remaining = totalExcess;
      for (Map.Entry<String, Long> entry : cashToAdd.entrySet()) {
        long add = (long) Math.round(totalExcess * targetDist.get(entry.getKey()));
        add = Math.min(add, remaining);
        entry.setValue(entry.getValue() + add);
        remaining -= add;
      }
    }

    // Buy extra assets.
    long sum = 0;
    for (Map.Entry<String, Long> entry : cashToAdd.entrySet()) {
      long value = entry.getValue();
      assert value >= 0;
      if (value > 0) {
        assert value <= cash;
        account.buyValue(entry.getKey(), value, "Buy toward target allocation");
        sum += value;
      }
    }
    assert sum >= 0;
    assert sum <= cash;
  }

  public void runTo(long timeEnd)
  {
    if (timeEnd == TimeLib.TIME_END) {
      timeEnd = guideSeq.getEndMS();
    }

    Account account = broker.getAccount(AccountName);
    DiscreteDistribution targetDist = null;

    final int lastIndex = Math.min(guideSeq.length() - 1, guideSeq.getIndexAtOrBefore(timeEnd));
    for (; runIndex <= lastIndex; ++runIndex) {
      final TimeInfo timeInfo = new TimeInfo(runIndex, guideSeq);
      // Note: fast simulation doesn't check for business days.
      broker.setNewDay(timeInfo);

      store.lock(TimeLib.TIME_BEGIN, timeInfo.time, runKey);
      targetDist = predictor.selectDistribution();
      account.updatePositions(targetDist);

      broker.doEndOfDayBusiness();
      if (timeInfo.isLastDayOfMonth && monthlyDeposit > 0.0 && runIndex > 0) {
        account.deposit(Fixed.toFixed(monthlyDeposit), Flow.InFlow, "Monthly Deposit");
      }

      // Update returns and holding information.
      double value = Fixed.toFloat(account.getValue()) / startingBalance;
      returnsDaily.addData(value, timeInfo.time);
      if (timeInfo.isLastDayOfMonth || runIndex == guideSeq.length() - 1) {
        returnsMonthly.addData(value, timeInfo.time);
      }

      broker.finishDay();
      store.unlock(runKey);
    }
  }

  public void finishRun()
  {
    Account account = broker.getAccount(AccountName);
    account.liquidate("Liquidate Account");
    guideSeq.unlock(runKey);
  }
}
