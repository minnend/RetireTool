package org.minnen.retiretool.predictor.daily;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.ml.ClassificationModel;
import org.minnen.retiretool.ml.rank.Ranker;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

public class AdaptivePredictor extends Predictor
{
  public final boolean             DEBUG            = false;

  public final FeatureExtractor    featureExtractor;
  public final ClassificationModel absoluteClassifier;
  public final ClassificationModel pairwiseClassifier;
  public final Ranker              ranker;
  public final TradeFreq           tradeFreq        = TradeFreq.Weekly;
  public final double              maxKeepFrac      = 0.5;
  public final int                 maxKeep          = 4;
  public final int                 pctQuantum       = 1;

  private DiscreteDistribution     prevDistribution = null;

  public AdaptivePredictor(FeatureExtractor featureExtractor, ClassificationModel absoluteClassifier,
      ClassificationModel pairwiseClassifier, Ranker ranker, BrokerInfoAccess brokerAccess, String[] assetChoices)
  {
    super("Adaptive", brokerAccess, assetChoices);
    this.featureExtractor = featureExtractor;
    this.absoluteClassifier = absoluteClassifier;
    this.pairwiseClassifier = pairwiseClassifier;
    this.ranker = ranker;
    this.predictorType = PredictorType.Distribution;
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    if (prevDistribution != null
        && ((tradeFreq == TradeFreq.Weekly && !brokerAccess.getTimeInfo().isLastDayOfWeek) || tradeFreq == TradeFreq.Monthly
            && !brokerAccess.getTimeInfo().isLastDayOfMonth)) {
      distribution.copyFrom(prevDistribution);
      return;
    }

    if (DEBUG) {
      System.out.printf("[%s]\n", TimeLib.formatDate2(brokerAccess.getTime()));
    }

    // Calculate features for each asset.
    int n = distribution.size();
    List<FeatureVec> features = new ArrayList<>();
    List<double[]> posProbs = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      String assetName = assetChoices[i];
      if (assetName.equals("cash")) continue;
      FeatureVec x = featureExtractor.calculate(brokerAccess, assetName);
      assert x.getName().equals(assetName);

      // See if this asset passes the absolute test.
      double[] probs = new double[2];
      int k = absoluteClassifier.predict(x, probs);
      // int k = (x.get(10) > 0.0 ? 1 : 0);
      if (DEBUG) {
        System.out.printf(" %s: [%.1f, %.1f] %6.2f%%  %s\n", x.getName(), 100.0 * probs[0], 100.0 * probs[1],
            futureReturns.get(assetName), k == 1 ? "Up" : "Down");
      }
      assert k == 0 || k == 1;
      if (k == 0) continue;

      features.add(x);
      posProbs.add(probs);
    }
    List<FeatureVec> origFeatureList = new ArrayList<>();
    origFeatureList.addAll(features);

    // Sort the assets by predicted value.
    n = features.size();
    int[] rank = null;
    double[] scores = null;
    if (n > 1) {
      if (pairwiseClassifier != null) {
        // Compute win record for each remaining asset.
        int[][] wins = new int[n][n];
        for (int i = 0; i < n; ++i) {
          FeatureVec fi = features.get(i);
          for (int j = i + 1; j < n; ++j) {
            FeatureVec fj = features.get(j);
            FeatureVec fdiff = fi.sub(fj);
            int k = pairwiseClassifier.predict(fdiff);
            // int k = (fdiff.get(10) > 0.0 ? 1 : 0);
            // System.out.printf("%f -> %d\n", fdiff.get(10), k);
            if (k == 1) { // asset[i] beat asset[j]
              wins[i][j] = 1;
              wins[j][i] = -1;
            } else { // asset[i] lost to asset[j]
              assert k == 0;
              wins[i][j] = -1;
              wins[j][i] = 1;
            }
          }
        }
        if (DEBUG) {
          for (int i = 0; i < n; ++i) {
            System.out.printf(" %s: ", features.get(i).getName());
            for (int j = 0; j < n; ++j) {
              System.out.printf(" %s", wins[i][j] > 0 ? "W" : wins[i][j] < 0 ? "L" : "-");
            }
            System.out.printf(" %6.2f%%\n", futureReturns.get(features.get(i).getName()));
          }
        }

        // Rank the assets based on their win record.
        rank = ranker.rank(wins);
        scores = ranker.getScores();

      } else {
        // No pairwise classifier so use the "positive" probability to rank.
        scores = new double[posProbs.size()];
        for (int i = 0; i < scores.length; ++i) {
          scores[i] = posProbs.get(i)[1];
        }
        rank = Library.sort(scores.clone(), false);
      }

      // Reorder features according to inferred ranking (best comes first).
      for (int i = 0; i < n; ++i) {
        features.set(i, origFeatureList.get(rank[i]));
        if (DEBUG) {
          System.out.printf(" %d: %s (%.3f, %.2f%%)\n", i + 1, features.get(i).getName(), scores[rank[i]],
              futureReturns.get(features.get(i).getName()));
        }
      }
    }

    // Calculate the maximum number of funds we can hold.
    int nMaxKeep = n;
    if (maxKeepFrac > 0.0) {
      nMaxKeep = Math.min(nMaxKeep, (int) Math.round(n * maxKeepFrac));
    }
    if (maxKeep > 0) {
      nMaxKeep = Math.min(nMaxKeep, maxKeep);
    }
    int nKeep = Math.min(n, nMaxKeep);

    if (nKeep > 0) {
      // Remove all but the best nKeep assets.
      while (features.size() > nKeep) {
        features.remove(features.size() - 1);
      }
      assert features.size() == nKeep;

      // Build distribution over the best assets.
      distribution.clear();
      double uniformWeight = 1.0 / nKeep;
      for (int i = 0; i < nKeep; ++i) {
        FeatureVec x = features.get(i);
        distribution.set(x.getName(), uniformWeight);
      }
      distribution.clean(pctQuantum);
    } else {
      // If nothing looks good, hold all cash.
      distribution.set("cash", 1.0);
    }
    if (DEBUG) {
      // System.out.printf("[%s] %s\n", TimeLib.formatDate2(brokerAccess.getTime()), distribution.toStringWithNames(2));
      System.out.printf(" %s\n", distribution.toStringWithNames(2));
    }

    // Update previous distribution.
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
    super.reset();
    prevDistribution = null;
  }
}
