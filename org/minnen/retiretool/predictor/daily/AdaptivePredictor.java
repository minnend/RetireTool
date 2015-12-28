package org.minnen.retiretool.predictor.daily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;

public class AdaptivePredictor extends Predictor
{
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

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    if (prevDistribution != null && !brokerAccess.getTimeInfo().isLastDayOfWeek) {
      distribution.copyFrom(prevDistribution);
      return;
    }

    int n = distribution.size();

    // Calculate momentum score for each asset.
    List<MomScore> moms = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      String name = assetChoices[i];
      if (name.equals("cash")) {
        moms.add(new MomScore("cash", 1.0));
      } else {
        Sequence seq = brokerAccess.getSeq(name);
        double now = seq.average(-20, -1, 0);
        double before = seq.average(-100, -80, 0);
        double score = now / before;
        moms.add(new MomScore(name, score));
      }
    }
    Collections.sort(moms, Collections.reverseOrder());
    int nKeep = 1;
    for (int i = 0; i < n; ++i) {
      MomScore mom = moms.get(i);
      if (mom.score >= 1.0) {
        nKeep = i + 1;
      }
      // System.out.printf("%s: %.3f\n", mom.name, mom.score);
    }
    // System.out.println("---");

    nKeep = Math.min(nKeep, n / 2);
    double w = 1.0 / nKeep;
    distribution.clear();
    // System.out.printf("[%s] ", TimeLib.formatDate(brokerAccess.getTime()));
    for (int i = 0; i < nKeep; ++i) {
      String name = moms.get(i).name;
      distribution.set(name, w);
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
