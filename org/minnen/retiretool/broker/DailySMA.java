package org.minnen.retiretool.broker;

import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.data.Sequence;

public class DailySMA
{
  private final Account account;
  private final String  assetName;

  private final double  margin;
  private final int     nLookbackBase;
  private final int     nLookbackTrigger;
  private final int     iPrice;
  private final long    minTimeBetweenFlips;

  /** Relative location: -1 = below threshold; 1 = above threshold. */
  private int           reloc        = 0;
  private long          timeLastFlip = TimeLib.TIME_ERROR;

  public DailySMA(int nLookbackTrigger, int nLookbackBase, double margin, String assetName, Account account)
  {
    this(nLookbackTrigger, nLookbackBase, margin, 0L, 0, assetName, account);
  }

  public DailySMA(int nLookbackTrigger, int nLookbackBase, double margin, long minTimeBetweenFlips, int iPrice,
      String assetName, Account account)
  {
    this.nLookbackTrigger = nLookbackTrigger;
    this.nLookbackBase = nLookbackBase;
    this.margin = margin / 100.0;
    this.minTimeBetweenFlips = minTimeBetweenFlips;
    this.iPrice = iPrice;
    this.account = account;
    this.assetName = assetName;
  }

  public void init(TimeInfo timeInfo)
  {
    // Nothing to do.
  }

  public void step(TimeInfo timeInfo)
  {
    // Nothing to do.
  }

  public boolean predict()
  {
    final long time = account.broker.getTime();

    // If it's too soon to change, repeat last decision.
    if (reloc != 0 && (timeLastFlip == TimeLib.TIME_ERROR || time - timeLastFlip < minTimeBetweenFlips)) {
      return reloc > 0;
    }

    final Sequence seq = account.broker.store.getMisc(assetName);
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
