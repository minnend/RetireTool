package org.minnen.retiretool.predictor.daily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.config.ConfigAdaptive;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.Weighting;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.Library;

public class AdaptivePredictor extends Predictor
{
  private final ConfigAdaptive config;
  private DiscreteDistribution prevDistribution = null;

  private static class MomScore implements Comparable<MomScore>
  {
    public final String name;
    public double       score;

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

    @Override
    public String toString()
    {
      return String.format("%s: %.3f", name, score);
    }
  }

  public AdaptivePredictor(ConfigAdaptive config, BrokerInfoAccess brokerAccess, String... assetChoices)
  {
    super("Adaptive", brokerAccess, assetChoices);
    this.config = config;
    this.predictorType = PredictorType.Distribution;
  }

  private double[] getEqualWeights(int n)
  {
    if (n < 1) return null;
    double[] weights = new double[n];
    Arrays.fill(weights, 1.0 / n);
    return weights;
  }

  private double[][] getReturns(List<MomScore> moms)
  {
    final int n = moms.size();

    final int nLookback = config.nCorrelation;
    assert nLookback > 0;
    double[][] returns = new double[n][];
    for (int i = 0; i < n; ++i) {
      String name = moms.get(i).name;
      assert !name.equals("cash");
      Sequence seq = brokerAccess.getSeq(name);
      assert seq != null : moms.get(i).name;
      returns[i] = FinLib.getReturns(seq, -nLookback, -1, config.iPrice);
      assert returns[i].length == returns[0].length;
    }
    return returns;
  }

  private double[] getMinVarWeights(List<MomScore> moms)
  {
    final int n = moms.size();
    if (n < 1) return null;
    if (n == 1) return new double[] { 1.0 };

    double[] weights = getEqualWeights(n);
    final double uniformWeight = 1.0 / n;

    double maxWeight = (config.maxWeight > 0.0 ? config.maxWeight : 1.0);
    assert maxWeight >= uniformWeight : String.format("%f vs %f", maxWeight, uniformWeight);
    double minWeight = (config.minWeight > 0.0 ? config.minWeight : 0.0);
    assert minWeight >= 0.0 && minWeight <= uniformWeight : String.format("%f vs %f", minWeight, uniformWeight);

    double[][] returns = getReturns(moms);
    // for (int i = 0; i < n; ++i) {
    // System.out.printf("%5s: ", moms.get(i).name);
    // double[] r = returns[i];
    // for (int j = 0; j < r.length; ++j) {
    // assert !Double.isNaN(r[j]);
    // System.out.printf("%5.2f ", r[j]);
    // }
    // System.out.println();
    // }
    // System.out.println();

    double[][] cov = Library.covariance(returns);
    // double[][] corr = Library.correlation(returns);
    // double[] dev = Library.cov2dev(cov);
    // for (int i = 0; i < n; ++i) {
    // System.out.printf("%5s: ", moms.get(i).name);
    // for (int j = 0; j < n; ++j) {
    // assert !Double.isNaN(corr[i][j]);
    // System.out.printf("%5.2f ", corr[i][j]);
    // }
    // System.out.println();
    // }
    // System.out.println();

    double[] mvw = FinLib.minvar(cov, minWeight, maxWeight);
    // System.out.print("MVW: ");
    // for (int i = 0; i < n; ++i) {
    // System.out.printf("%5.3f ", mvw[i]);
    // }
    // System.out.println();
    // System.out.println();
    // double x = FinLib.portfolioDev(weights, dev, corr);
    // double y = FinLib.portfolioDev(mvw, dev, corr);
    // System.out.printf("%f, %f\n", x, y);
    // assert y < x + 1e-6;

    // double alpha = 1.0;
    // for (int i = 0; i < n; ++i) {
    // weights[i] = weights[i] * alpha + mvw[i] * (1.0 - alpha);
    // }
    System.arraycopy(mvw, 0, weights, 0, n);

    // if (n <= 3) {
    // // System.out.print("       ");
    // System.out.printf("%11s|", TimeLib.formatDate(brokerAccess.getTime()));
    // for (int i = 0; i < n; ++i) {
    // System.out.printf("%s:%.2f ", moms.get(i).name, weights[i]);
    // }
    // System.out.printf("|%d\n", n);
    // }

    return weights;
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    if (prevDistribution != null
        && ((config.tradeFreq == TradeFreq.Weekly && !brokerAccess.getTimeInfo().isLastDayOfWeek) || config.tradeFreq == TradeFreq.Monthly
            && !brokerAccess.getTimeInfo().isLastDayOfMonth)) {
      distribution.copyFrom(prevDistribution);
      return;
    }

    // Calculate momentum score for each asset.
    final int n = distribution.size();
    List<MomScore> moms = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      String name = assetChoices[i];
      if (name.equals("cash")) {
        moms.add(new MomScore("cash", -100.0));
      } else {
        Sequence seq = brokerAccess.getSeq(name);
        double now = seq.average(-config.nTriggerA, -config.nTriggerB, config.iPrice);
        double before = seq.average(-config.nBaseA, -config.nBaseB, config.iPrice);
        double mul = now / before;
        double score = FinLib.mul2ret(mul);
        moms.add(new MomScore(name, score));
      }
    }
    Collections.sort(moms, Collections.reverseOrder());

    int nGoodMom = 0;
    for (int i = 0; i < n; ++i) {
      MomScore mom = moms.get(i);
      if (mom.score >= 0.0) {
        nGoodMom = i + 1;
        // System.out.printf("%s: %.3f\n", mom.name, mom.score);
      } else {
        break;
      }
    }
    // System.out.println("---");
    while (moms.size() > nGoodMom) {
      moms.remove(moms.size() - 1);
    }
    assert moms.size() == nGoodMom;

    // Calculate max number of assets to keep and remove rest.
    int nKeep = nGoodMom;
    if (config.maxKeepFrac > 0.0) {
      nKeep = Math.min(nKeep, (int) Math.round(n * config.maxKeepFrac));
    }
    if (config.maxKeep > 0) {
      nKeep = Math.min(nKeep, config.maxKeep);
    }
    while (moms.size() > nKeep) {
      moms.remove(moms.size() - 1);
    }
    assert moms.size() == nKeep;

    double[] weights = (config.weighting == Weighting.Equal ? getEqualWeights(nKeep) : getMinVarWeights(moms));
    distribution.clear();
    // System.out.printf("[%s] %d\n", TimeLib.formatDate(brokerAccess.getTime()), nKeep);
    for (int i = 0; i < nKeep; ++i) {
      MomScore mom = moms.get(i);
      distribution.set(mom.name, weights[i]);
      // System.out.printf("%s ", name);
    }

    // If nothing looks good, hold all cash.
    if (nKeep == 0) {
      distribution.set("cash", 1.0);
    }

    // System.out.printf("[%s] %s\n", TimeLib.formatDate2(brokerAccess.getTime()), distribution.toStringWithNames(2));
    distribution.clean(config.pctQuantum);
    // distribution.sortByName();
    // System.out.printf("              %s\n", distribution.toStringWithNames(0));
    // System.out.printf("[%s] %s (Predictor)\n", TimeLib.formatDate2(brokerAccess.getTime()),
    // distribution.toStringWithNames(0));

    // System.out.println();
    assert distribution.isNormalized();
    if (prevDistribution == null) {
      prevDistribution = new DiscreteDistribution(distribution);
    } else {
      prevDistribution.copyFrom(distribution);
    }
  }

  @Override
  public void reset()
  {
    prevDistribution = null;
  }
}
