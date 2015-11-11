package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.FinLib;

public class Position
{
  public final Account            account;
  public final String             name;
  private final List<PositionLot> lots = new ArrayList<>();

  private double                  nShares;

  public Position(Account account, String name)
  {
    this.account = account;
    this.name = name;
  }

  public double getNumShares()
  {
    // TODO recompute nShares for debug
    double n = 0;
    for (PositionLot lot : lots) {
      n += lot.getNumShares();
    }
    assert Math.abs(n - nShares) < 1e-6;

    return nShares;
  }

  public double getValue()
  {
    double price = account.broker.getPrice(name);
    return price * getNumShares();
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
    assert lot.getNumShares() <= nShares;

    double longPL = 0.0;
    double shortPL = 0.0;

    // TODO smarter share matching.

    // First match with long-term lots.
    double nSharesLeft = lot.getNumShares();
    while (nSharesLeft > 0) {
      PositionLot src = lots.get(0);

      // We only want LTCG here.
      if (!FinLib.isLTG(src.purchaseTime, lot.purchaseTime)) {
        break;
      }

      double n = Math.min(src.getNumShares(), nSharesLeft);
      nSharesLeft -= n;
      src.sell(n);
      if (src.getNumShares() == 0) {
        lots.remove(0);
      }

      double gain = lot.purchasePrice - src.purchasePrice;
      longPL += gain;
    }
    assert nSharesLeft >= 0;
    assert nSharesLeft == 0 || !FinLib.isLTG(lots.get(0).purchaseTime, lot.purchaseTime);

    // First match with long-term lots.
    while (nSharesLeft > 0) {
      int iSrc = lots.size() - 1;
      PositionLot src = lots.get(iSrc);
      assert !FinLib.isLTG(src.purchaseTime, lot.purchaseTime);

      double n = Math.min(src.getNumShares(), nSharesLeft);
      nSharesLeft -= n;
      src.sell(n);
      if (src.getNumShares() == 0) {
        lots.remove(iSrc);
      }

      double gain = lot.purchasePrice - src.purchasePrice;
      shortPL += gain;
    }
    assert nSharesLeft == 0;

    nShares -= lot.getNumShares();
    return new Receipt(name, longPL, shortPL);
  }
}
