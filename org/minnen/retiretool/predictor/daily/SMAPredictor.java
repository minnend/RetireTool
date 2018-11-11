package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.util.TimeLib;

public class SMAPredictor extends Predictor
{
  private final ConfigSMA config;

  /** Relative location: -1 = below threshold; 1 = above threshold. */
  private int             reloc        = 0;
  private long            timeLastFlip = TimeLib.TIME_ERROR;
  private final String    assetName;
  private final String    integralName;
  private int             assetID;
  private int             integralID;

  public SMAPredictor(ConfigSMA config, String assetName, String alternativeAsset, BrokerInfoAccess brokerAccess)
  {
    super("SMA", brokerAccess, assetName, alternativeAsset);
    this.predictorType = PredictorType.SelectOne;
    this.config = config;
    this.assetName = assetName;
    this.integralName = assetName + "-integral";
    setBroker(brokerAccess);
  }

  @Override
  protected String calcSelectOne()
  {
    return (calcInOut() ? assetChoices[0] : assetChoices[1]);
  }

  private boolean calcInOut()
  {
    final long time = brokerAccess.getTime();

    // If it's too soon to change, repeat last decision.
    if (reloc != 0 && (timeLastFlip == TimeLib.TIME_ERROR || time - timeLastFlip < config.minTimeBetweenFlips)) {
      return reloc >= 0;
    }

    // Get either the price sequence or the integral sequence.
    final Sequence integral = (integralID >= 0 ? brokerAccess.getSeq(integralID) : null);
    final Sequence seq = (integral != null ? null : brokerAccess.getSeq(assetID));

    final int iLast = (integral != null ? integral.length() : seq.length()) - 1;
    final int iBaseA = iLast - config.nLookbackBaseA;
    final int iBaseB = iLast - config.nLookbackBaseB;
    final int iTriggerA = iLast - config.nLookbackTriggerA;
    final int iTriggerB = iLast - config.nLookbackTriggerB;

    assert iBaseA <= iBaseB;
    assert iTriggerA <= iTriggerB;

    // Not enough data => invest in safe asset.
    if (iBaseA < 0 || iTriggerA < 0) {
      reloc = -1;
      timeLastFlip = time;
      return false;
    }

    // Calculate SMA values for base (threshold) and trigger.
    double threshold, trigger;
    if (integral != null) {
      threshold = integral.integralAverage(iBaseA, iBaseB, config.iPrice);
      trigger = integral.integralAverage(iTriggerA, iTriggerB, config.iPrice);
    } else {
      threshold = seq.average(iBaseA, iBaseB, config.iPrice);
      trigger = seq.average(iTriggerA, iTriggerB, config.iPrice);
    }

    // Adjust threshold if we're using a trigger margin.
    if (config.margin > 0.0) {
      threshold -= reloc * threshold * config.margin / 100.0;
    }

    // Compare trigger to threshold and update state if there is a change.
    int newLoc = (trigger > threshold ? 1 : -1);
    if (reloc != newLoc) {
      // System.out.printf("Change (%d -> %d) @ [%s]\n", reloc, newLoc, TimeLib.formatDate2(time));
      reloc = newLoc;
      timeLastFlip = time;
    }
    return reloc >= 0;
  }

  @Override
  public void reset()
  {
    super.reset();
    reloc = 0;
    timeLastFlip = TimeLib.TIME_ERROR;
  }

  @Override
  public void setBroker(BrokerInfoAccess brokerAccess)
  {
    super.setBroker(brokerAccess);
    if (brokerAccess != null) {
      this.assetID = brokerAccess.getID(assetName);
      this.integralID = brokerAccess.getID(integralName);
    } else {
      this.assetID = this.integralID = -1;
    }
  }
}
