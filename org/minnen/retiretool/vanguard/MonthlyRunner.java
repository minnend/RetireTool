package org.minnen.retiretool.vanguard;

import java.util.List;

import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class MonthlyRunner implements PortfolioRunner
{
  private List<Sequence> seqs;
  private int            durStatsMonths;
  private int            momentumMonths;

  private class Position
  {
    public String   symbol;
    public double   value;
    public double   targetAllocation;
    public Sequence seq;
    public boolean  bCanHold;

    public Position(String symbol, double value, double targetAllocation, Sequence seq)
    {
      this.symbol = symbol;
      this.value = value;
      this.targetAllocation = targetAllocation;
      this.seq = seq;
      this.bCanHold = true;
    }
  }

  public MonthlyRunner(List<Sequence> seqs, int durStatsMonths, int momentumMonths)
  {
    this.seqs = seqs;
    this.durStatsMonths = durStatsMonths;
    this.momentumMonths = momentumMonths;
  }

  @Override
  public FeatureVec run(DiscreteDistribution portfolio)
  {
    portfolio = portfolio.removeZeroWeights(1e-5);
    String name = portfolio.toStringWithNames(0);
    Sequence returnsMonthly = new Sequence(name);

    long time = TimeLib.toMs(TimeLib.ms2date(seqs.get(0).getStartMS()).minusMonths(1));
    returnsMonthly.addData(new FeatureVec(1, 1.0).setTime(time));

    // Create initial positions.
    Position[] positions = new Position[portfolio.names.length];
    for (int i = 0; i < positions.length; ++i) {
      positions[i] = new Position(portfolio.names[i], portfolio.weights[i], portfolio.weights[i], seqs.get(Sequence
          .findByName(portfolio.names[i], seqs)));
    }

    int nMonths = seqs.get(0).length();
    int monthsSinceRebalance = 0;

    for (int t = 0; t < nMonths; ++t) {
      // Determine if each position is allowed to be held in the current month.
      for (Position pos : positions) {
        pos.bCanHold = canHold(pos.seq, t);
      }

      // Update each position with the next month's returns.
      for (Position pos : positions) {
        if (pos.bCanHold) {
          pos.value *= pos.seq.get(t, 0);
        }
      }
      ++monthsSinceRebalance;

      // Save total return; equivalent to balance since we started with $1.
      time = seqs.get(0).getTimeMS(t);
      double balance = getBalance(positions);
      returnsMonthly.addData(new FeatureVec(1, balance).setTime(time));

      // Rebalance every six months.
      if (monthsSinceRebalance >= 6) {
        for (Position pos : positions) {
          pos.value = pos.targetAllocation * balance;
        }
        monthsSinceRebalance = 0;
        assert Math.abs(balance - getBalance(positions)) < 1e-5;
      }
    }

    return SummaryTools.calcStats(returnsMonthly, durStatsMonths);
  }

  private boolean canHold(Sequence seq, int t)
  {
    if (momentumMonths < 1) return true;
    int iStart = t - momentumMonths;
    if (iStart < 0) return true;

    // Calculate N month momentum.
    double r = 1.0;
    for (int i = iStart; i < t; ++i) {
      r *= seq.get(i, 0);
    }

    // Only allowed to hold assets that have positive momentum.
    return r > 1.0;
  }

  private static double getBalance(Position[] positions)
  {
    double balance = 0.0;
    for (Position pos : positions) {
      balance += pos.value;
    }
    return balance;
  }

}
