package org.minnen.retiretool.predictor.daily;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.TimeLib;

public class AdaptivePredictor extends Predictor
{
  private final Random         rng              = new Random();
  private DiscreteDistribution prevDistribution = null;

  private static class MomScore implements Comparable<MomScore>
  {
    public final String name;
    public final double score;

    public MomScore(String name, double score)
    {
      this.name = name;
      this.score = score;
    }

    @Override
    public int compareTo(MomScore mom)
    {
      if (score < mom.score) return -1;
      if (score > mom.score) return 1;
      return 0;
    }
  }

  public AdaptivePredictor(BrokerInfoAccess brokerAccess, String[] assetChoices)
  {
    super("Adaptive", brokerAccess, assetChoices);
    this.predictorType = PredictorType.Distribution;
  }

  private CumulativeStats sim(double[] weights, double[][] returns)
  {
    assert weights.length == returns.length;

    final int n = returns[0].length;
    Sequence seq = new Sequence("Returns");
    LocalDate date = LocalDate.of(1900, Month.JANUARY, 1);
    double balance = 1000.0;
    for (int t = 0; t < n; ++t) {
      double r = 0.0;
      for (int i = 0; i < returns.length; ++i) {
        r += weights[i] * returns[i][t];
      }
      balance *= FinLib.ret2mul(r);

      seq.addData(balance, date);
      date = date.plusMonths(1);
    }

    return CumulativeStats.calc(seq);
  }

  public double[] getWeights(List<MomScore> moms, int n)
  {
    // http://www.joptimizer.com/quadraticProgramming.html
    double[] weights = new double[n];

    final int nLookback = 20;
    double[][] returns = new double[n][];
    for (int i = 0; i < n; ++i) {
      String name = moms.get(i).name;
      if (name.equals("cash")) {
        returns[i] = new double[nLookback - 1];
      } else {
        Sequence seq = brokerAccess.getSeq(name);
        assert seq != null : moms.get(i).name;
        returns[i] = FinLib.getReturns(seq, -nLookback, -1, 0);
      }
    }

    // rng.setSeed(12345L); // TODO
    double bestScore = -1.0;
    // System.out.printf("--- [%s] ---\n", TimeLib.formatDate(brokerAccess.getTime()));
    for (int iter = 0; iter < 100; ++iter) {
      double[] w = rng.nextSimplex(n);
      CumulativeStats cstats = sim(w, returns);
      double score = cstats.cagr;// - cstats.drawdown;
      if (iter == 0 || score > bestScore) {
        System.arraycopy(w, 0, weights, 0, n);
        bestScore = score;
        // System.out.printf("New Best [%d]: %s  %.3f\n", iter, cstats, score);
        // System.out.printf("  [%.1f", w[0]*100);
        // for(int i=1; i<w.length;++i){
        // System.out.printf(",%.1f", w[i]*100);
        // }
        // System.out.println("]");
      }
    }

    return weights;
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    if (prevDistribution != null && !brokerAccess.getTimeInfo().isLastDayOfWeek) {
      distribution.copyFrom(prevDistribution);
      return;
    }

    // Calculate momentum score for each asset.
    final int n = distribution.size();
    List<MomScore> moms = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      String name = assetChoices[i];
      if (name.equals("cash")) {
        moms.add(new MomScore("cash", 0.0));
      } else {
        Sequence seq = brokerAccess.getSeq(name);
        double now = seq.average(-20, -1, 0);
        double before = seq.average(-100, -80, 0);
        double mul = now / before;
        double score = FinLib.mul2ret(mul);
        moms.add(new MomScore(name, score));
      }
    }
    Collections.sort(moms, Collections.reverseOrder());
    int nKeep = 1;
    for (int i = 0; i < n; ++i) {
      MomScore mom = moms.get(i);
      if (mom.score >= 0.0) {
        nKeep = i + 1;
        // System.out.printf("%s: %.3f\n", mom.name, mom.score);
      } else {
        break;
      }
    }
    // System.out.println("---");

    nKeep = Math.min(nKeep, n / 2); // TODO parameterize number / fraction to keep
    // double[] weights = getWeights(moms, nKeep);
    distribution.clear();
    // System.out.printf("[%s] %d\n", TimeLib.formatDate(brokerAccess.getTime()), nKeep);
    for (int i = 0; i < nKeep; ++i) {
      MomScore mom = moms.get(i);
      distribution.set(mom.name, 1.0 / nKeep);// weights[i]);
      // System.out.printf("%s ", name);
    }

    // System.out.println();
    assert distribution.isNormalized();
    // System.out.printf("[%s] %s\n", TimeLib.formatDate(brokerAccess.getTime()), distribution);
    if (prevDistribution == null) {
      prevDistribution = new DiscreteDistribution(distribution);
    } else {
      prevDistribution.copyFrom(distribution);
    }
  }
}
