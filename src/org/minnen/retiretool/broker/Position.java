package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.TimeLib;

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
    assert n == nShares;

    return nShares;
  }

  public long getValue()
  {
    long price = account.broker.getPrice(name);
    long value = Fixed.mul(price, getNumShares());

    // TODO for debug
    long sum = 0L;
    for (PositionLot lot : lots) {
      sum += Fixed.mul(price, lot.getNumShares());
    }
    assert sum == value;

    return value;
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

    long longPL = 0L;
    long shortPL = 0L;

    // TODO smarter share matching, e.g. minimize tax based on p/l.
    // TODO could have different matching algo for tax-deferred account.

    // First match with long-term lots.
    long nSharesLeft = lot.getNumShares();
    while (nSharesLeft > 0) {
      PositionLot src = lots.get(0);

      // We only want LTCG here.
      if (!FinLib.isLTG(TimeLib.ms2date(src.purchaseTime), TimeLib.ms2date(lot.purchaseTime))) {
        break;
      }

      long n = Math.min(src.getNumShares(), nSharesLeft);
      nSharesLeft -= n;
      src.sell(n);
      if (src.getNumShares() == 0) {
        lots.remove(0);
      }

      long gain = lot.purchasePrice - src.purchasePrice;
      longPL += gain;
    }
    assert nSharesLeft >= 0;
    assert nSharesLeft == 0
        || !FinLib.isLTG(TimeLib.ms2date(lots.get(0).purchaseTime), TimeLib.ms2date(lot.purchaseTime));

    // Now match youngest positions.
    while (nSharesLeft > 0) {
      int iSrc = lots.size() - 1;
      PositionLot src = lots.get(iSrc);
      assert !FinLib.isLTG(TimeLib.ms2date(src.purchaseTime), TimeLib.ms2date(lot.purchaseTime));

      long n = Math.min(src.getNumShares(), nSharesLeft);
      nSharesLeft -= n;
      src.sell(n);
      if (src.getNumShares() == 0) {
        lots.remove(iSrc);
      }

      long gain = lot.purchasePrice - src.purchasePrice;
      shortPL += gain;
    }
    assert nSharesLeft == 0;

    nShares -= lot.getNumShares();
    long balance = getValue();
    return new Receipt(name, longPL, shortPL, balance);
  }
}
