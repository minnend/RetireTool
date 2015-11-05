package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.FinLib;

public class Position
{
  public final Account            account;
  public final String             name;
  private final List<PositionLot> lots = new ArrayList<>();

  private int                     nShares;

  public Position(Account account, String name)
  {
    this.account = account;
    this.name = name;
  }

  public int getNumShares()
  {
    return nShares;
  }

  public void add(PositionLot lot)
  {
    assert lot.name.equals(name);

    lots.add(lot);
    nShares += lot.getNumShares();
  }

  public Receipt sub(PositionLot lot)
  {
    assert lot.name.equals(name);

    double longGain = 0.0;
    double shortGain = 0.0;
    double longLoss = 0.0;
    double shortLoss = 0.0;

    // TODO smarter share matching.
    int nSharesLeft = lot.getNumShares();
    while (nSharesLeft > 0) {
      PositionLot src = lots.get(0);

      int n = Math.min(src.getNumShares(), nSharesLeft);
      nSharesLeft -= n;
      if (src.getNumShares() <= n) {
        lots.remove(0);
      } else {
        src.sell(n);
      }

      double gain = lot.price - src.price;
      double value = Math.abs(gain * n);
      if (FinLib.isLTG(src.time, lot.time)) {
        if (gain < 0.0) {
          longLoss += value;
        } else {
          longGain += value;
        }
      } else { // short-term gain/loss
        if (gain < 0.0) {
          shortLoss += value;
        } else {
          shortGain += value;
        }
      }
    }

    nShares -= lot.getNumShares();
    return new Receipt(name, longGain, shortGain, longLoss, shortLoss);
  }
}
