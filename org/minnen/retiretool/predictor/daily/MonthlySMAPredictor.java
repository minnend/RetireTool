package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.config.ConfigMonthlySMA;

/** Makes decision based on monthly data above / below SMA. */
public class MonthlySMAPredictor extends Predictor
{
  private final ConfigMonthlySMA config;
  private final int              assetID;

  public MonthlySMAPredictor(ConfigMonthlySMA config, String assetName, String alternativeAsset, String analysisName,
      BrokerInfoAccess brokerAccess)
  {
    super("SMA", brokerAccess, assetName, alternativeAsset);
    this.predictorType = PredictorType.SelectOne;
    this.config = config;
    this.assetID = brokerAccess.getID(analysisName);
  }

  @Override
  protected String calcSelectOne()
  {
    final long time = brokerAccess.getTime();
    boolean in = calcInOut(time);
    int code = in ? 1 : 0;
    if (timeCodes.isEmpty() || timeCodes.get(timeCodes.size() - 1).code != code) {
      timeCodes.add(new TimeCode(time, code));
    }
    return (in ? assetChoices[0] : assetChoices[1]);
  }

  private boolean calcInOut(long time)
  {
    final Sequence seq = brokerAccess.getSeq(assetID);

    final int iLast = seq.length() - 1;
    assert seq.getTimeMS(iLast) <= time;
    final int iLookback = iLast - config.nLookback;

    // Not enough data => invest in safe asset.
    if (iLookback < 0) return false;

    // Calculate SMA values for base (threshold) and trigger.
    double threshold = seq.average(iLookback, iLast, config.iPrice);
    double trigger = seq.get(iLast, config.iPrice);

    // System.out.printf("%s=[%s] today=[%s] sma: %.2f vs. [%d,%d]=%.2f -> %s\n", seq.getName(),
    // TimeLib.formatDate(seq.getTimeMS(iLast)), TimeLib.formatDate(time), trigger, iLookback, iLast, threshold);

    // Compare trigger to threshold.
    return config.invert ? trigger < threshold : trigger > threshold;
  }
}
