package org.minnen.retiretool.broker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.broker.transactions.Transaction.Flow;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.IndexRange;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;

public class Simulation
{
  public static final double                  DistributionEPS        = 0.02;
  public static final double                  TargetEPS              = 0.1;
  public static final int                     REBALANCE_AFTER_N_DAYS = 363;

  public static final Random                  rng                    = new Random();
  public static final String                  AccountName            = "SimAccount";

  public final SequenceStore                  store;
  public final Sequence                       guideSeq;
  public final Slippage                       slippage;
  public final int                            maxDelay;
  public final Broker                         broker;

  private final double                        startingBalance;
  private final double                        monthlyDeposit;

  private List<TimeInfo>                      timeInfoCache;

  public Sequence                             returnsDaily;
  public Sequence                             returnsMonthly;
  public Map<LocalDate, DiscreteDistribution> holdings;

  private long                                runKey;
  private int                                 runIndex               = -1;
  private long                                lastRebalance          = TimeLib.TIME_BEGIN;
  private boolean                             bNeedRebalance;
  private int                                 rebalanceDelay;
  private DiscreteDistribution                prevDist;
  private Predictor                           predictor;
  private boolean                             bCheckBusinessDay      = true;

  public Simulation(SequenceStore store, Sequence guideSeq)
  {
    this(store, guideSeq, Slippage.None, 0, 10000.0, 0.0, PriceModel.adjCloseModel, PriceModel.adjCloseModel);
  }

  public Simulation(SequenceStore store, Sequence guideSeq, Slippage slippage, int maxDelay, PriceModel valueModel,
      PriceModel quoteModel)
  {
    this(store, guideSeq, slippage, maxDelay, 10000.0, 0.0, valueModel, quoteModel);
  }

  public Simulation(SequenceStore store, Sequence guideSeq, Slippage slippage, int maxDelay, double startingBalance,
      double monthlyDeposit, PriceModel valueModel, PriceModel quoteModel)
  {
    this.store = store;
    this.guideSeq = guideSeq;
    this.slippage = slippage;
    this.maxDelay = maxDelay;
    this.startingBalance = startingBalance;
    this.monthlyDeposit = monthlyDeposit;
    this.broker = new Broker(store, valueModel, quoteModel, slippage, guideSeq);
    this.timeInfoCache = calcTimeInfo(guideSeq);
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

  public boolean checksBusinessDays()
  {
    return bCheckBusinessDay;
  }

  public void setCheckBusinessDays(boolean skip)
  {
    bCheckBusinessDay = skip;
  }

  /**
   * Calculate a distribution that minimizes transaction while coming close to the target.
   * 
   * @param current current distribution
   * @param target target distribution
   * @param tol tolerance percentage so changes less than this will be ignored (1.0 = 1.0%).
   * @return distribution similar to target with small changes ignored
   */
  private DiscreteDistribution minimizeTransactions(DiscreteDistribution current, DiscreteDistribution target,
      double tol)
  {
    // TODO Needs work & testing. Multiple ideas here that aren't working together.
    assert current.size() == target.size();
    assert target.isNormalized() : target;
    final double eps = 1e-4;
    tol /= 100.0; // Convert percentage to fraction
    final int N = target.size();

    // Force small weights to zero.
    for (int i = 0; i < N; ++i) {
      if (Math.abs(target.weights[i]) < 0.005) {
        target.weights[i] = 0.0;
      }
    }

    // System.out.printf("Current: %s\n", current.toStringWithNames(2));
    // System.out.printf(" Target: %s\n", target.toStringWithNames(2));

    DiscreteDistribution dist = new DiscreteDistribution(target);
    boolean[] canChange = new boolean[N];
    for (int i = 0; i < N; ++i) {
      assert target.names[i].equals(current.names[i]);
      double tw = target.weights[i];
      double cw = current.weights[i];
      assert tw == 0.0 || Math.abs(tw) > 0.001;
      if (tw != 0.0) {
        canChange[i] = (Math.abs(cw - tw) > tol);
        if (!canChange[i]) {
          dist.weights[i] = cw;
        }
      }
    }
    double sum = dist.sum();
    // System.out.printf(" Step1: %s (%f)\n", dist.toStringWithNames(2), sum);
    // System.out.print("Can Change:");
    // for (int i = 0; i < N; ++i) {
    // if (!canChange[i]) continue;
    // System.out.printf(" %s", target.names[i]);
    // }
    // System.out.println();

    if (sum < 1.0 - eps) {
      double missing = 1.0 - sum;
      // System.out.printf("UNDER: %f (missing=%f)\n", sum, missing);

      // Try to distribute to assets that are already changing.
      int nAbsorb = 0;
      for (int i = 0; i < N; ++i) {
        if (canChange[i] && dist.weights[i] > 0.0) ++nAbsorb;
      }
      if (nAbsorb > 0) {
        double absorb = missing / nAbsorb;
        for (int i = 0; i < N; ++i) {
          if (canChange[i] && dist.weights[i] > 0.0) dist.weights[i] += absorb;
        }
        missing = 0.0;
      }

      while (missing > eps) {
        // Look for something under target.
        double mostUnder = 0.0;
        int iUnder = -1;
        for (int i = 0; i < N; ++i) {
          double diff = target.weights[i] - dist.weights[i];
          if (diff > mostUnder) {
            mostUnder = diff;
            iUnder = i;
          }
        }
        if (iUnder < 0) {
          // Nothing under target so look for under current.
          for (int i = 0; i < N; ++i) {
            double diff = current.weights[i] - dist.weights[i];
            if (diff > mostUnder) {
              mostUnder = diff;
              iUnder = i;
            }
          }
        }
        double increase = Math.min(missing, mostUnder);
        missing -= increase;
        dist.weights[iUnder] += increase;
      }
    } else if (sum > 1.0 + eps) {
      // System.out.printf("OVER: %f", sum);
      double excess = sum - 1.0;

      // Try to distribute to assets that are already changing.
      int nAbsorb = 0;
      for (int i = 0; i < N; ++i) {
        if (canChange[i] && dist.weights[i] > 0.0) ++nAbsorb;
      }
      if (nAbsorb > 0) {
        double absorb = excess / nAbsorb;
        for (int i = 0; i < N; ++i) {
          if (canChange[i] && dist.weights[i] > 0.0) dist.weights[i] -= absorb;
        }
        excess = 0.0;
      }

      while (excess > eps) {
        // Look for something over the target.
        double mostOver = 0.0;
        int iOver = -1;
        for (int i = 0; i < N; ++i) {
          if (!canChange[i]) continue;
          double diff = dist.weights[i] - target.weights[i];
          if (diff > mostOver) {
            mostOver = diff;
            iOver = i;
          }
        }
        if (iOver < 0) {
          // Nothing over target so look for over current.
          for (int i = 0; i < N; ++i) {
            if (!canChange[i]) continue;
            double diff = dist.weights[i] - current.weights[i];
            if (diff > mostOver) {
              mostOver = diff;
              iOver = i;
            }
          }
        }
        double reduce = Math.min(excess, mostOver);
        System.out.printf(" Reduce: %s %f\n", dist.names[iOver], reduce);
        excess -= reduce;
        dist.weights[iOver] -= reduce;
      }
    }

    return dist;
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
    guideSeq.lock(range.first, range.second, runKey);
    runIndex = 0;
    lastRebalance = TimeLib.TIME_BEGIN;
    bNeedRebalance = false;
    rebalanceDelay = 0;
    prevDist = new DiscreteDistribution("cash");
    this.predictor = predictor;

    returnsMonthly = new Sequence(name);
    returnsDaily = new Sequence(name);
    holdings = new TreeMap<>();

    broker.reset();
    broker.setNewDay(timeInfoCache.get(runIndex));
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

    // System.out.printf("Sim.runTo(Start,%d): [%s] -> [%s]\n", runIndex,
    // TimeLib.formatDate(guideSeq.getTimeMS(runIndex)),
    // TimeLib.formatDate(timeEnd));
    while (runIndex < guideSeq.length() && guideSeq.getTimeMS(runIndex) <= timeEnd) {
      final TimeInfo timeInfo = timeInfoCache.get(runIndex);
      if (bCheckBusinessDay && !timeInfo.isBusinessDay) {
        ++runIndex;
        continue;
      }
      broker.setNewDay(timeInfo);

      // Calculate return over next week.
      // predictor.futureReturns = calcFutureReturns(timeInfo, 5, PriceModel.adjOpenModel);

      if (holdings.isEmpty()) {
        holdings.put(timeInfo.date, account.getDistribution());
      }

      store.lock(TimeLib.TIME_BEGIN, timeInfo.time, runKey);
      if (bNeedRebalance && targetDist != null && rebalanceDelay <= 0) {
        // TODO figure out better approach for minimizing transactions. Some predictors may want to turn this off.
        // DiscreteDistribution submitDist = minimizeTransactions(curDist, targetDist, 4.0);
        DiscreteDistribution submitDist = new DiscreteDistribution(targetDist);

        // TODO improve & test submission distribution code.
        if (!submitDist.isNormalized()) {
          DiscreteDistribution curDist = account.getDistribution(targetDist.names);
          System.out.printf("Curr: %s\n", curDist.toStringWithNames(2));
          System.out.printf("Prev: %s\n", prevDist.toStringWithNames(0));
          System.out.printf("Want: %s\n", targetDist.toStringWithNames(0));
          System.out.printf("Subm: %s (%f)\n", submitDist.toStringWithNames(2), submitDist.sum());
        }

        // System.out.printf("Rebalance! [%s]\n", timeInfo.date);
        account.updatePositions(submitDist);
        lastRebalance = timeInfo.time;
        // TODO should be able to assign submitDist instead of copy-constructor -- test that
        prevDist = new DiscreteDistribution(submitDist);
      }

      // If we have cash, buy shares to move closer to target allocation
      if (account.getCash() > Fixed.ONE) {
        // System.out.printf("[%s] %s\n", TimeLib.formatDate(timeInfo.time),
        // account.getDistribution().toStringWithNames(2));
        buyTowardTargetAllocation(targetDist, account);
        // System.out.printf(" %s\n", account.getDistribution().toStringWithNames(2));
      }

      // End of day business.
      if (rebalanceDelay > 0) --rebalanceDelay;
      broker.doEndOfDayBusiness();
      // TODO should deposit be on last day of month or first?
      if (timeInfo.isLastDayOfMonth && monthlyDeposit > 0.0 && runIndex > 0) {
        account.deposit(Fixed.toFixed(monthlyDeposit), Flow.InFlow, "Monthly Deposit");
      }

      // Time for a prediction and possible asset change.
      targetDist = predictor.selectDistribution();

      // TODO clean up wording: rebalance vs active change in target distribution

      // Rebalance if desired distribution changes too much or if it's been more than a year.
      // Note: we're comparing the current request to the previous one, not to the actual
      // distribution in the account, which could change due to price movement.
      boolean bPrevRebalance = bNeedRebalance;
      DiscreteDistribution curDist = account.getDistribution();
      bNeedRebalance = ((timeInfo.time - lastRebalance) / TimeLib.MS_IN_DAY > REBALANCE_AFTER_N_DAYS
          || !targetDist.isSimilar(prevDist, DistributionEPS) || !targetDist.isSimilar(curDist, TargetEPS));
      // if (bNeedRebalance) {
      // System.out.printf("Need Rebalance: [%s] vs [%s] %s vs %s / %s\n", TimeLib.formatDate(timeInfo.time),
      // TimeLib.formatDate(lastRebalance), targetDist, prevDist, curDist.toStringWithNames(2));
      // }

      if (maxDelay > 0) {
        if (bNeedRebalance && !bPrevRebalance) {
          // Note: If buying at open, a one day delay is built-in.
          rebalanceDelay = rng.nextInt(maxDelay + 1);
        }
      }

      // Update returns and holding information.
      double value = Fixed.toFloat(account.getValue()) / startingBalance;
      returnsDaily.addData(value, timeInfo.time);
      if (timeInfo.isLastDayOfMonth || runIndex == guideSeq.length() - 1) {
        returnsMonthly.addData(value, timeInfo.time);
      }
      if (timeInfo.isFirstDayOfWeek) {
        holdings.put(timeInfo.date, account.getDistribution());
      }

      broker.finishDay();
      store.unlock(runKey);
      ++runIndex;
    }
  }

  public void finishRun()
  {
    Account account = broker.getAccount(AccountName);
    account.liquidate("Liquidate Account");
    guideSeq.unlock(runKey);
    FinLib.normalizeReturns(returnsMonthly);
    FinLib.normalizeReturns(returnsDaily);
  }

  private Map<String, Double> calcFutureReturns(TimeInfo timeInfo, int daysInFuture, PriceModel priceModel)
  {
    long timeNextWeek = TimeLib.plusBusinessDays(timeInfo.time, daysInFuture);
    Map<String, Double> futureReturns = new TreeMap<>();
    for (String assetName : predictor.assetChoices) {
      if (assetName.equals("cash")) continue;
      Sequence seq = store.get(assetName);
      if (seq.getNumDims() > 1) {
        IndexRange range = seq.getIndices(timeInfo.time, timeNextWeek, EndpointBehavior.Closest);
        double p1 = priceModel.getPrice(seq.get(range.first));
        double p2 = priceModel.getPrice(seq.get(range.second));
        double r = FinLib.mul2ret(p2 / p1);
        futureReturns.put(assetName, r);
      }
    }
    return futureReturns;
  }

  /** @return list of TimeInfo objects for the given Sequence. */
  public static List<TimeInfo> calcTimeInfo(Sequence seq)
  {
    List<TimeInfo> cache = new ArrayList<>();
    for (int i = 0; i < seq.length(); ++i) {
      cache.add(new TimeInfo(i, seq));
    }
    return cache;
  }
}
