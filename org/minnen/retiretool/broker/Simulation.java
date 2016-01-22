package org.minnen.retiretool.broker;

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
  public static final double DistributionEPS = 0.02;
  public static final Random rng             = new Random();

  public final SequenceStore store;
  public final Sequence      guideSeq;
  public final Slippage      slippage;
  public final boolean       bBuyAtNextOpen;
  public final int           maxDelay;
  public final Broker        broker;

  private double             startingBalance = 10000.0;
  public Sequence            returnsDaily;
  public Sequence            returnsMonthly;

  public Simulation(SequenceStore store, Sequence guideSeq)
  {
    this(store, guideSeq, Slippage.None, 0, true);
  }

  public Simulation(SequenceStore store, Sequence guideSeq, Slippage slippage, int maxDelay, boolean bBuyAtNextOpen)
  {
    this.store = store;
    this.guideSeq = guideSeq;
    this.slippage = slippage;
    this.bBuyAtNextOpen = bBuyAtNextOpen;
    this.maxDelay = maxDelay;
    this.broker = new Broker(store, slippage, guideSeq);
  }

  public long getStartMS()
  {
    return guideSeq.getStartMS();
  }

  public long getEndMS()
  {
    return guideSeq.getEndMS();
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

  public Sequence run(Predictor predictor, long timeStart, long timeEnd, String name)
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
    // System.out.printf("Run: [%s] -> [%s] = [%d] -> [%d]\n", TimeLib.formatDate(timeStart),
    // TimeLib.formatDate(timeEnd),
    // range.iStart, range.iEnd);
    final long key = Sequence.Lock.genKey();
    guideSeq.lock(range.iStart, range.iEnd, key);
    Sequence ret = run(predictor, name);
    guideSeq.unlock(key);
    return ret;
  }

  public Sequence run(Predictor predictor)
  {
    return run(predictor, "Returns");
  }

  public Sequence run(Predictor predictor, String name)
  {
    final int T = guideSeq.length();
    final long principal = Fixed.toFixed(startingBalance);
    final boolean bPriceIndexAlwaysZero = (guideSeq.getNumDims() == 1);

    // System.out.printf("Sim.run[T=%d]: [%s] -> [%s]\n", T, TimeLib.formatDate(guideSeq.getStartMS()),
    // TimeLib.formatDate(guideSeq.getEndMS()));

    long prevTime = guideSeq.getStartMS() - TimeLib.MS_IN_DAY;
    long lastRebalance = TimeLib.TIME_BEGIN;
    boolean bNeedRebalance = false;
    DiscreteDistribution prevDist = new DiscreteDistribution("cash");
    DiscreteDistribution targetDist = null;

    returnsMonthly = new Sequence(name);
    returnsMonthly.addData(1.0, guideSeq.getStartMS());
    returnsDaily = new Sequence(name);
    returnsDaily.addData(1.0, guideSeq.getStartMS());

    int rebalanceDelay = 0;

    // TODO support prediction at start instead of on second tick

    broker.reset();
    broker.setPriceIndex(bPriceIndexAlwaysZero ? 0 : FinLib.Close);
    Account account = broker.openAccount(Account.Type.Roth, true);
    final long key = Sequence.Lock.genKey();
    // System.out.printf("key = %d\n", key);
    // System.out.printf("lock store: %d -> %d\n", guideSeq.getStartMS(), guideSeq.getEndMS());
    store.lock(guideSeq.getStartMS(), guideSeq.getEndMS(), key);
    for (int t = 0; t < T; ++t) {
      final long time = guideSeq.getTimeMS(t);
      // System.out.printf(" [t=%d  %s  relock]\n", t, TimeLib.formatDate(time));
      store.relock(TimeLib.TIME_BEGIN, time, key);
      final long nextTime = (t == T - 1 ? TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.ms2date(time))) : guideSeq
          .getTimeMS(t + 1));
      broker.setTime(time, prevTime, nextTime);
      final TimeInfo timeInfo = broker.getTimeInfo();
      // System.out.println(timeInfo);

      // Handle initialization issues at t==0.
      if (t == 0) {
        account.deposit(principal, "Initial Deposit");
      }

      // Handle case where we buy at the open, not the close.
      if (bBuyAtNextOpen) {
        if (bNeedRebalance && targetDist != null && rebalanceDelay <= 0) {
          if (!bPriceIndexAlwaysZero) {
            broker.setPriceIndex(FinLib.Open);
          }

          DiscreteDistribution curDist = account.getDistribution(targetDist.names);
          DiscreteDistribution submitDist = minimizeTransactions(curDist, targetDist, 4.0);

          if (!submitDist.isNormalized()) {
            System.out.printf("Curr: %s\n", curDist.toStringWithNames(2));
            System.out.printf("Prev: %s\n", prevDist.toStringWithNames(0));
            System.out.printf("Want: %s\n", targetDist.toStringWithNames(0));
            System.out.printf("Subm: %s (%f)\n", submitDist.toStringWithNames(2), submitDist.sum());
          }

          account.rebalance(submitDist);
          // account.printTransactions(timeInfo.time, TimeLib.TIME_END);
          // System.out.println();

          lastRebalance = time;
          prevDist = new DiscreteDistribution(submitDist);
          if (!bPriceIndexAlwaysZero) {
            broker.setPriceIndex(FinLib.Close);
          }
        }
      }

      // End of day business.
      if (rebalanceDelay > 0) --rebalanceDelay;
      broker.doEndOfDayBusiness();

      // Time for a prediction and possible asset change.
      // System.out.printf("Predict.start\n");
      targetDist = predictor.selectDistribution();
      // System.out.printf("Predict.end\n");

      // Rebalance if desired distribution changes by more than 2% or if it's been more than a year.
      // Note: we're comparing the current request to the previous one, not to the actual
      // distribution in the account, which could change due to price movement.
      boolean bPrevRebalance = bNeedRebalance;
      bNeedRebalance = ((time - lastRebalance) / TimeLib.MS_IN_DAY > 363 || !targetDist.isSimilar(prevDist,
          DistributionEPS));

      if (maxDelay > 0) {
        if (bNeedRebalance && !bPrevRebalance) {
          // Note: If buying at open, a one day delay is built-in.
          rebalanceDelay = rng.nextInt(maxDelay + 1);
        }
      }

      // Update account at end of the day.
      if (!bBuyAtNextOpen) {
        if (bNeedRebalance && rebalanceDelay <= 0) {
          account.rebalance(targetDist);
          lastRebalance = time;
          prevDist = new DiscreteDistribution(targetDist);
        }
      }

      double value = Fixed.toFloat(Fixed.div(account.getValue(), principal));
      returnsDaily.addData(value, time);
      if (timeInfo.isLastDayOfMonth) {
        returnsMonthly.addData(value, time);
      }
      prevTime = time;
    }
    store.unlock(key);

    return returnsMonthly;
  }
}
