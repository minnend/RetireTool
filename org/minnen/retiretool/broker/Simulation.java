package org.minnen.retiretool.broker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.Slippage;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.IndexRange;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.TimeLib;

public class Simulation
{
  public static final double                  DistributionEPS = 0.02;
  public static final double                  TargetEPS       = 0.1;

  public static final Random                  rng             = new Random();
  public static final String                  AccountName     = "SimAccount";

  public final SequenceStore                  store;
  public final Sequence                       guideSeq;
  public final Slippage                       slippage;
  public final int                            maxDelay;
  public final Broker                         broker;

  private final double                        startingBalance = 10000.0;

  public Sequence                             returnsDaily;
  public Sequence                             returnsMonthly;
  public Map<LocalDate, DiscreteDistribution> holdings;

  private long                                runKey;
  private int                                 runIndex        = -1;
  private long                                lastRebalance   = TimeLib.TIME_BEGIN;
  private boolean                             bNeedRebalance;
  private int                                 rebalanceDelay;
  private DiscreteDistribution                prevDist;
  private Predictor                           predictor;

  public List<TimeInfo>                       days;

  public Simulation(SequenceStore store, Sequence guideSeq, Slippage slippage, int maxDelay, PriceModel valueModel,
      PriceModel quoteModel)
  {
    this.store = store;
    this.guideSeq = guideSeq;
    this.slippage = slippage;
    this.maxDelay = maxDelay;
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
    // System.out.printf("  Step1: %s  (%f)\n", dist.toStringWithNames(2), sum);
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
    lastRebalance = TimeLib.TIME_BEGIN;
    bNeedRebalance = false;
    rebalanceDelay = 0;
    prevDist = new DiscreteDistribution("cash");
    this.predictor = predictor;

    returnsMonthly = new Sequence(name);
    returnsMonthly.addData(1.0, guideSeq.getStartMS());
    returnsDaily = new Sequence(name);
    returnsDaily.addData(1.0, guideSeq.getStartMS());
    holdings = new TreeMap<>();

    broker.reset();
    broker.openAccount(AccountName, Fixed.toFixed(startingBalance), Account.Type.Roth, true);

    days = new ArrayList<>();

    // TODO support prediction at start instead of on second tick
    // if (predictor != null) {
    // store.lock(TimeLib.TIME_BEGIN, guideSeq.getStartMS(), runKey);
    // DiscreteDistribution targetDist = predictor.selectDistribution();
    // }
  }

  public void runTo(long timeEnd)
  {
    if (timeEnd == TimeLib.TIME_END) {
      timeEnd = guideSeq.getEndMS();
    }

    Account account = broker.getAccount(AccountName);
    DiscreteDistribution targetDist = null;
    PriceModel priceModel = PriceModel.adjOpenModel;

    // System.out.printf("Sim.runTo(Start,%d): [%s] -> [%s]\n", runIndex,
    // TimeLib.formatDate(guideSeq.getTimeMS(runIndex)),
    // TimeLib.formatDate(timeEnd));
    while (runIndex < guideSeq.length() && guideSeq.getTimeMS(runIndex) <= timeEnd) {
      final TimeInfo timeInfo = new TimeInfo(runIndex, guideSeq);
      broker.setNewDay(timeInfo);
      days.add(timeInfo);

      // TODO for debug
      // Calculate return over next week.
      long timeNextWeek = TimeLib.plusBusinessDays(timeInfo.time, 5);
      Map<String, Double> futureReturns = new TreeMap<>();
      for (String assetName : predictor.assetChoices) {
        if (assetName.equals("cash")) continue;
        Sequence seq = store.get(assetName);
        IndexRange range = seq.getIndices(timeInfo.time, timeNextWeek, EndpointBehavior.Closest);
        double p1 = priceModel.getPrice(seq.get(range.iStart));
        double p2 = priceModel.getPrice(seq.get(range.iEnd));
        double r = FinLib.mul2ret(p2 / p1);
        futureReturns.put(assetName, r);
      }
      predictor.futureReturns = futureReturns;

      store.lock(TimeLib.TIME_BEGIN, timeInfo.time, runKey);

      if (holdings.isEmpty()) {
        holdings.put(timeInfo.date, account.getDistribution());
      }

      // Handle case where we buy at the open, not the close.
      if (bNeedRebalance && targetDist != null && rebalanceDelay <= 0) {
        DiscreteDistribution curDist = account.getDistribution(targetDist.names);
        // TODO figure out better approach for minimizing transactions. Some predictors may want to turn this off.
        // DiscreteDistribution submitDist = minimizeTransactions(curDist, targetDist, 4.0);
        DiscreteDistribution submitDist = new DiscreteDistribution(targetDist);

        // TODO improve & test submission distribution code.
        if (!submitDist.isNormalized()) {
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

      // End of day business.
      if (rebalanceDelay > 0) --rebalanceDelay;
      broker.doEndOfDayBusiness();

      // Time for a prediction and possible asset change.
      targetDist = predictor.selectDistribution();

      // TODO clean up wording: rebalance vs active change in target distribution

      // Rebalance if desired distribution changes too much or if it's been more than a year.
      // Note: we're comparing the current request to the previous one, not to the actual
      // distribution in the account, which could change due to price movement.
      boolean bPrevRebalance = bNeedRebalance;
      DiscreteDistribution curDist = account.getDistribution();
      bNeedRebalance = ((timeInfo.time - lastRebalance) / TimeLib.MS_IN_DAY > 363
          || !targetDist.isSimilar(prevDist, DistributionEPS) || !targetDist.isSimilar(curDist, TargetEPS));
      // if (bNeedRebalance) {
      // System.out.printf("Need Rebalance: [%s] vs [%s]   %s vs %s / %s\n", TimeLib.formatDate(timeInfo.time),
      // TimeLib.formatDate(lastRebalance), targetDist, prevDist, curDist);
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
    guideSeq.unlock(runKey);
  }
}
