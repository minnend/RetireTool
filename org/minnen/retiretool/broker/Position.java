package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.FinLib;

public class Position
{
  public final Account            account;
  public final String             name;
  private final List<PositionLot> lots = new ArrayList<>();

  private long                    nShares;

  public Position(Account account, String name)
  {
    this.account = account;
    this.name = name;
  }

  public int getNumLots()
  {
    return lots.size();
  }

  public long getNumShares()
  {
    // TODO recompute nShares for debug
    long n = 0;
    for (PositionLot lot : lots) {
      assert lot.getNumShares() > 0;
      n += lot.getNumShares();
    }
    assert Math.abs(n - nShares) < 1e-6;

    return nShares;
  }

  public long getValue()
  {
    long price = account.broker.getPrice(name);
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
    assert lot.getNumShares() < nShares + 1e-4;

    long longPL = 0L;
    long shortPL = 0L;

    // TODO smarter share matching.

    // First match with long-term lots.
    long nSharesLeft = lot.getNumShares();
    while (nSharesLeft > 1e-4) {
      PositionLot src = lots.get(0);

      // We only want LTCG here.
      if (!FinLib.isLTG(src.purchaseTime, lot.purchaseTime)) {
        break;
      }

      long n = Math.min(src.getNumShares(), nSharesLeft);
      nSharesLeft -= n;
      src.sell(n);
      if (src.getNumShares() < 1e-4) {
        lots.remove(0);
      }

      long gain = lot.purchasePrice - src.purchasePrice;
      longPL += gain;
    }
    assert nSharesLeft >= 0;
    assert nSharesLeft == 0 || !FinLib.isLTG(lots.get(0).purchaseTime, lot.purchaseTime);

    // First match with long-term lots.
    while (nSharesLeft > 1e-4) {
      int iSrc = lots.size() - 1;
      PositionLot src = lots.get(iSrc);
      assert !FinLib.isLTG(src.purchaseTime, lot.purchaseTime);

      long n = Math.min(src.getNumShares(), nSharesLeft);
      nSharesLeft -= n;
      src.sell(n);
      if (src.getNumShares() < 1e-4) {
        lots.remove(iSrc);
      }

      long gain = lot.purchasePrice - src.purchasePrice;
      shortPL += gain;
    }
    assert nSharesLeft < 1e-4;

    nShares -= lot.getNumShares();
    long balance = getValue();
    return new Receipt(name, longPL, shortPL, balance);
  }
}
