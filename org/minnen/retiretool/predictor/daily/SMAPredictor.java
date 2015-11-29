package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.config.ConfigSMA;

public class SMAPredictor extends Predictor
{
  private final ConfigSMA config;

  /** Relative location: -1 = below threshold; 1 = above threshold. */
  private int             reloc        = 0;
  private long            timeLastFlip = TimeLib.TIME_ERROR;
  private final String    integralName;

  public SMAPredictor(ConfigSMA config, String assetName, String alternativeAsset, BrokerInfoAccess brokerAccess)
  {
    super("SMA", brokerAccess, new String[] { assetName, alternativeAsset });
    this.predictorType = PredictorType.InOut;
    this.config = config;
    this.integralName = assetName + "-integral";
  }

  @Override
  protected boolean calcInOut()
  {
    final long time = brokerAccess.getTime();

    // If it's too soon to change, repeat last decision.
    if (reloc != 0 && (timeLastFlip == TimeLib.TIME_ERROR || time - timeLastFlip < config.minTimeBetweenFlips)) {
      return reloc > 0;
    }

    // Get either the price sequence or the integral sequence.
    final Sequence integral = brokerAccess.tryGetSeq(integralName);
    final Sequence seq = (integral != null ? null : brokerAccess.getSeq(assetChoices[0]));

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
      threshold = integral.get(iBaseB, config.iPrice);
      if (iTriggerA > 0) {
        threshold -= integral.get(iBaseA - 1, config.iPrice);
      }
      threshold /= (iBaseB - iBaseA + 1);

      trigger = integral.get(iTriggerB, config.iPrice);
      if (iTriggerA > 0) {
        trigger -= integral.get(iTriggerA - 1, config.iPrice);
      }
      trigger /= (iTriggerB - iTriggerA + 1);
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
      reloc = newLoc;
      timeLastFlip = time;
    }
    return reloc >= 0;
  }
}
