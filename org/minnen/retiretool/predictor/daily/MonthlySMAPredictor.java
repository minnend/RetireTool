package org.minnen.retiretool.predictor.daily;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.config.ConfigMonthlySMA;
import org.minnen.retiretool.util.TimeLib;

/** Makes decision based on monthly data above / below SMA. */
public class MonthlySMAPredictor extends Predictor
{
  private final ConfigMonthlySMA config;
  private final int              assetID;
  private int                    nRisky        = 0;
  private int                    nSafe         = 0;
  private long                   lastMonthTime = TimeLib.TIME_ERROR;
  private boolean                lastInOut     = false;

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
    lastInOut = calcInOut(time);
    int code = lastInOut ? 1 : 0;
    if (timeCodes.isEmpty() || timeCodes.get(timeCodes.size() - 1).code != code) {
      // System.out.printf(" new timecode: [%s] %d\n", TimeLib.formatDate(time), code);
      timeCodes.add(new TimeCode(time, code));
    }
    return (lastInOut ? assetChoices[0] : assetChoices[1]);
  }

  private boolean calcInOut(long time)
  {
    LocalDate date = TimeLib.ms2date(time).with(TemporalAdjusters.firstDayOfMonth());
    long monthTime = TimeLib.toMs(date);
    if (monthTime == lastMonthTime) { // same month so repeat result
      return lastInOut;
    } else {
      lastMonthTime = monthTime;
    }

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
    boolean isRisky = config.invert ? trigger < threshold : trigger > threshold;
    // System.out.printf("%s [%s] %s -- ", config.invert ? "invert" : "no-invert", TimeLib.formatDate(time),
    // isRisky ? "risky" : "safe");

    if (isRisky) {
      nSafe = 0;
      ++nRisky;
      if (nRisky < config.nMinRisky) {
        isRisky = false; // not risky long enough so revert to safe
      }
    } else {
      nRisky = 0;
      ++nSafe;
      if (nSafe < config.nMinSafe) {
        isRisky = true; // not safe long enough so revert to risky
      }
    }

    // System.out.printf("[%d / %d] => %s\n", nRisky, nSafe, isRisky ? "risky" : "safe");

    return isRisky;
  }

  public void reset()
  {
    nRisky = nSafe = 0;
  }
}
