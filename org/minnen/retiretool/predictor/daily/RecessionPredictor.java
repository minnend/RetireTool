package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.config.ConfigRecession;
import org.minnen.retiretool.util.TimeLib;

/** Predictor based on a sequence called "recession". */
public class RecessionPredictor extends Predictor
{
  public final ConfigRecession config;

  public RecessionPredictor(ConfigRecession config, BrokerInfoAccess brokerAccess, String[] assetChoices)
  {
    super("AvoidRecession", brokerAccess, assetChoices);
    this.config = config;
    this.predictorType = PredictorType.SelectOne;
  }

  public static long getSafeAccessTime(long now)
  {
    long before = TimeLib.toMs(TimeLib.ms2date(now).minusDays(63)) - 1;
    // System.out.printf("[%s] / [%s]\n", TimeLib.formatDate(now), TimeLib.formatDate(before));
    return before;
  }

  @Override
  protected String calcSelectOne()
  {
    Sequence recession = brokerAccess.getSeq("recession");
    long now = brokerAccess.getTime();
    long before = getSafeAccessTime(now);
    int i = recession.getIndexAtOrBefore(before);
    double prob = recession.get(i, 0);
    double prev = i > 0 ? recession.get(i - 1, 0) : prob;
    double diff = prob - prev;
    if (diff < config.threshDiffRecover) return config.riskyAsset;
    if (prob > config.threshProb) return config.safeAsset;
    return config.riskyAsset;
  }
}
