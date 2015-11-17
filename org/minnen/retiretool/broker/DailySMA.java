package org.minnen.retiretool.broker;

import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.data.Sequence;

public class DailySMA
{
  private final Account account;
  private final String  assetName;

  private final double  margin              = 5.0 / 100.0;
  private final int     nLookbackBase       = 50;
  private final int     nLookbackTrigger    = 5;
  private final int     iPrice              = 0;
  private final long    minTimeBetweenFlips = 2 * TimeLib.MS_IN_DAY;

  private int           reloc               = 0;
  private long          timeLastFlip        = TimeLib.TIME_BEGIN;

  public DailySMA(Account account, String assetName)
  {
    this.account = account;
    this.assetName = assetName;
  }

  public void init(TimeInfo timeInfo)
  {
    // Nothing to do.
    timeLastFlip = timeInfo.time - minTimeBetweenFlips - 1L;
  }

  public void step(TimeInfo timeInfo)
  {
    // Nothing to do.
  }

  public boolean predict()
  {
    final long time = account.broker.getTime();
    if (reloc != 0 && time - timeLastFlip < minTimeBetweenFlips) {
      return reloc > 0;
    }

    Sequence seq = account.broker.store.getMisc(assetName);
    final int iLast = seq.length() - 1;
    final int iBase = iLast - nLookbackBase;
    final int iTrigger = iLast - nLookbackTrigger;

    // Not enough data => invest in safe asset.
    if (iBase < 0 || iTrigger < 0) {
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
