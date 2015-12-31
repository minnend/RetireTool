package org.minnen.retiretool.broker;

import org.minnen.retiretool.Slippage;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
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

  public Sequence run(Predictor predictor)
  {
    return run(predictor, "Returns");
  }

  private DiscreteDistribution genDistAvoidSmallChanges(DiscreteDistribution current, DiscreteDistribution target)
  {
    assert current.size() == target.size();
    DiscreteDistribution dist = new DiscreteDistribution(target);
    for (int i = 0; i < target.size(); ++i) {
      double dw = target.weights[i];
      double cw = current.weights[i];
      if (dw > 0.0 && Math.abs(cw - dw) < 0.05) {
        dist.weights[i] = cw;
      }
    }
    double sum = dist.sum();
    if (sum < 1.0 - 1e-6) {
      double missing = 1.0 - sum;
      while (missing > 1e-6) {
        double mostUnder = 0.0;
        int iUnder = -1;
        for (int i = 0; i < target.size(); ++i) {
          double diff = target.weights[i] - dist.weights[i];
          if (diff > mostUnder) {
            mostUnder = diff;
            iUnder = i;
          }
        }
        double increase = Math.min(missing, mostUnder);
        missing -= increase;
        dist.weights[iUnder] += increase;
      }
    } else if (sum > 1.0 + 1e-6) {
      double excess = sum - 1.0;
      while (excess > 1e-6) {
        double mostOver = 0.0;
        int iOver = -1;
        for (int i = 0; i < target.size(); ++i) {
          double diff = dist.weights[i] - target.weights[i];
          if (diff > mostOver) {
            mostOver = diff;
            iOver = i;
          }
        }
        double reduce = Math.min(excess, mostOver);
        excess -= reduce;
        dist.weights[iOver] -= reduce;
      }
    }

    return dist;
  }

  public Sequence run(Predictor predictor, String name)
  {
    final int T = guideSeq.length();
    final long principal = Fixed.toFixed(10000.0);
    final boolean bPriceIndexAlwaysZero = (guideSeq.getNumDims() == 1);

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
    for (int t = 0; t < T; ++t) {
      final long time = guideSeq.getTimeMS(t);
      store.lock(TimeLib.TIME_BEGIN, time);
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

          DiscreteDistribution curDist = account.getDistribution(predictor.assetChoices);
          DiscreteDistribution submitDist = genDistAvoidSmallChanges(curDist, targetDist);

          // System.out.printf("Curr: %s\n", curDist);
          // System.out.printf("Prev: %s\n", prevDist);
          // System.out.printf("Want: %s\n", targetDist);
          // System.out.printf("Subm: %s (%f)\n", submitDist, submitDist.sum());

          account.rebalance(submitDist);
          // curDist = account.getDistribution(predictor.assetChoices);
          // System.out.printf(" New: %s\n", curDist);
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
      targetDist = predictor.selectDistribution();

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

      store.unlock();

      double value = Fixed.toFloat(Fixed.div(account.getValue(), principal));
      returnsDaily.addData(value, time);
      if (timeInfo.isLastDayOfMonth) {
        returnsMonthly.addData(value, time);
      }
      prevTime = time;
    }

    return returnsMonthly;
  }
}
