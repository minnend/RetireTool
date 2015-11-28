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

  public SMAPredictor(ConfigSMA config, String assetName, String alternativeAsset, BrokerInfoAccess brokerAccess)
  {
    super("SMA", brokerAccess, new String[] { assetName, alternativeAsset });
    this.predictorType = PredictorType.InOut;
    this.config = config;
  }

  @Override
  protected boolean calcInOut()
  {
    final long time = brokerAccess.getTime();

    // If it's too soon to change, repeat last decision.
    if (reloc != 0 && (timeLastFlip == TimeLib.TIME_ERROR || time - timeLastFlip < config.minTimeBetweenFlips)) {
      return reloc > 0;
    }

    final Sequence seq = brokerAccess.getPriceSeq(assetChoices[0]);
    final int iLast = seq.length() - 1;
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
    double threshold = seq.average(iBaseA, iBaseB, config.iPrice);
    double trigger = seq.average(iTriggerA, iTriggerB, config.iPrice);

    // Adjust threshold if we're using a trigger margin.
    if (config.margin > 0.0) {
      threshold -= reloc * threshold * config.margin;
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
