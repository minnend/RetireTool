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

  public Simulation(SequenceStore store, Sequence guideSeq, Slippage slippage, int maxDelay, boolean bBuyAtNextOpen)
  {
    this.store = store;
    this.guideSeq = guideSeq;
    this.slippage = slippage;
    this.bBuyAtNextOpen = bBuyAtNextOpen;
    this.maxDelay = maxDelay;
    this.broker = new Broker(store, slippage, guideSeq.getStartMS());
  }

  public Sequence run(Predictor predictor)
  {
    return run(predictor, "Returns");
  }

  public long getStartMS()
  {
    return guideSeq.getStartMS();
  }

  public long getEndMS()
  {
    return guideSeq.getEndMS();
  }

  public Sequence run(Predictor predictor, String name)
  {
    final int T = guideSeq.length();
    final long principal = Fixed.toFixed(1000.0);

    long prevTime = guideSeq.getStartMS() - TimeLib.MS_IN_DAY;
    long lastRebalance = TimeLib.TIME_BEGIN;
    boolean bNeedRebalance = false;
    DiscreteDistribution prevDistribution = null;
    DiscreteDistribution desiredDistribution = null;

    returnsMonthly = new Sequence(name);
    returnsMonthly.addData(1.0, guideSeq.getStartMS());
    returnsDaily = new Sequence(name);
    returnsDaily.addData(1.0, guideSeq.getStartMS());

    int rebalanceDelay = 0;

    broker.reset();
    Account account = broker.openAccount(Account.Type.Roth, true);
    for (int t = 0; t < T; ++t) {
      long time = guideSeq.getTimeMS(t);
      store.lock(TimeLib.TIME_BEGIN, time);
      long nextTime = (t == T - 1 ? TimeLib.toNextBusinessDay(time) : guideSeq.getTimeMS(t + 1));
      broker.setTime(time, prevTime, nextTime);
      TimeInfo timeInfo = broker.getTimeInfo();

      // Handle initialization issues at t==0.
      if (t == 0) {
        account.deposit(principal, "Initial Deposit");
      }

      // Handle case where we buy at the open, not the close.
      if (bBuyAtNextOpen) {
        if (bNeedRebalance && desiredDistribution != null && rebalanceDelay <= 0) {
          broker.setPriceIndex(FinLib.Open);
          account.rebalance(desiredDistribution);
          lastRebalance = time;
          if (prevDistribution == null) {
            prevDistribution = new DiscreteDistribution(desiredDistribution);
          } else {
            prevDistribution.copyFrom(desiredDistribution);
          }
          broker.setPriceIndex(FinLib.Close);
        }
      }

      // End of day business.
      if (rebalanceDelay > 0) --rebalanceDelay;
      broker.doEndOfDayBusiness();

      // Time for a prediction and possible asset change.
      desiredDistribution = predictor.selectDistribution();

      // Rebalance if desired distribution changes by more than 2% or if it's been more than a year.
      // Note: we're comparing the current request to the previous one, not to the actual
      // distribution in the account, which could change due to price movement.
      boolean bPrevRebalance = bNeedRebalance;
      bNeedRebalance = ((time - lastRebalance) / TimeLib.MS_IN_DAY > 365 || !desiredDistribution.isSimilar(
          prevDistribution, DistributionEPS));

      if (maxDelay > 0) {
        if (bNeedRebalance && !bPrevRebalance) {
          // Note: If buying at open, a one day delay is built-in.
          rebalanceDelay = rng.nextInt(maxDelay + 1);
        }
      }

      // Update account at end of the day.
      if (!bBuyAtNextOpen) {
        if (bNeedRebalance && rebalanceDelay <= 0) {
          account.rebalance(desiredDistribution);
          lastRebalance = time;
          if (prevDistribution == null) {
            prevDistribution = new DiscreteDistribution(desiredDistribution);
          } else {
            prevDistribution.copyFrom(desiredDistribution);
          }
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
