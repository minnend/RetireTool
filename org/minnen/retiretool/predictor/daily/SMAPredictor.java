package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.Sequence;

public class SMAPredictor extends Predictor
{
  private final double margin;
  private final int    nLookbackBase;
  private final int    nLookbackTrigger;
  private final int    iPrice;
  private final long   minTimeBetweenFlips;

  /** Relative location: -1 = below threshold; 1 = above threshold. */
  private int          reloc        = 0;
  private long         timeLastFlip = TimeLib.TIME_ERROR;

  public SMAPredictor(int nLookbackTrigger, int nLookbackBase, double margin, String assetName,
      String alternativeAsset, BrokerInfoAccess brokerAccess)
  {
    this(nLookbackTrigger, nLookbackBase, margin, 0L, 0, assetName, alternativeAsset, brokerAccess);
  }

  public SMAPredictor(int nLookbackTrigger, int nLookbackBase, double margin, long minTimeBetweenFlips, int iPrice,
      String assetName, String alternativeAsset, BrokerInfoAccess brokerAccess)
  {
    super("SMA", brokerAccess, new String[] { assetName, alternativeAsset });
    this.predictorType = PredictorType.InOut;
    this.nLookbackTrigger = nLookbackTrigger;
    this.nLookbackBase = nLookbackBase;
    this.margin = margin / 100.0;
    this.minTimeBetweenFlips = minTimeBetweenFlips;
    this.iPrice = iPrice;
  }

  @Override
  protected boolean calcInOut()
  {
    final long time = brokerAccess.getTime();

    // If it's too soon to change, repeat last decision.
    if (reloc != 0 && (timeLastFlip == TimeLib.TIME_ERROR || time - timeLastFlip < minTimeBetweenFlips)) {
      return reloc > 0;
    }

    final Sequence seq = brokerAccess.getPriceSeq(assetChoices[0]);
    final int iLast = seq.length() - 1;
    final int iBase = iLast - nLookbackBase;
    final int iTrigger = iLast - nLookbackTrigger;

    // Not enough data => invest in safe asset.
    if (iBase < 0 || iTrigger < 0) {
      reloc = -1;
      return false;
    }

    double threshold = seq.average(iBase, iLast).get(iPrice);
    double trigger = seq.average(iTrigger, iLast).get(iPrice);

    // Test above / below moving average.
    if (margin > 0.0) {
      threshold -= reloc * threshold * margin;
    }

    int newLoc = (trigger > threshold ? 1 : -1);
    if (reloc != newLoc) {
      reloc = newLoc;
      timeLastFlip = time;
    }
    return reloc >= 0;
  }
}
