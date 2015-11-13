package org.minnen.retiretool.broker;

import org.minnen.retiretool.Library;
import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.data.Sequence;

public class DailySMA
{
  private final Account  account;
  private final String   name;

  private final double   margin       = 1.0 / 100.0;
  private final int      nLookback    = 10;
  private final double[] monthlyMeans = new double[13];
  private final int      iPrice       = 0;

  private double         currentValue;
  private double         currentSum;
  private int            currentN;

  public DailySMA(Account account, String name)
  {
    this.account = account;
    this.name = name;
  }

  public void init(TimeInfo timeInfo)
  {
    Sequence seq = account.broker.store.getMisc(name);
    assert seq.getEndMS() <= timeInfo.time;

    // Fill in monthly means.
    assert TimeLib.isSameDay(seq.getEndMS(), timeInfo.time);
    // System.out.printf("SMA Init: [%s]\n", TimeLib.formatDate(seq.getEndMS()));
    int i = seq.length() - 1;
    double sum = 0.0;
    int n = 0;
    int nCurrentAge = 0;
    for (; i >= 0; --i) {
      double price = seq.get(i, iPrice);
      int nMonthsBetween = TimeLib.monthsBetween(timeInfo.time, seq.getTimeMS(i));
      assert nMonthsBetween >= nCurrentAge;
      if (nMonthsBetween == nCurrentAge) {
        sum += price;
        ++n;
      } else {
        assert nMonthsBetween == nCurrentAge + 1;
        assert n > 0;
        monthlyMeans[nCurrentAge] = sum / n;
        if (nCurrentAge == 0) {
          currentSum = sum;
          currentN = n;
          currentValue = monthlyMeans[nCurrentAge];
        }
        // System.out.printf("SMA.init: %d -> %.2f (%d) [%s] [%s]\n", nCurrentAge, monthlyMeans[nCurrentAge - 1], n,
        // TimeLib.formatDate(seq.getTimeMS(i)), TimeLib.formatDate(seq.getTimeMS(i+1)));
        sum = 0.0;
        n = 0;

        // We're done after 12 months.
        if (nMonthsBetween > 12) {
          break;
        }
      }
      nCurrentAge = nMonthsBetween;
    }
    assert nCurrentAge == 12;
  }

  public void step(TimeInfo timeInfo)
  {
    Sequence seq = account.broker.store.getMisc(name);
    int index = seq.getClosestIndex(timeInfo.time);

    // Incorporate price from today.
    double price = seq.get(index, iPrice);
    currentSum += price;
    ++currentN;
    currentValue = currentSum / currentN;

    // System.out.printf("[%s]: %.2f (%d)\n", TimeLib.formatDate(timeInfo.time), currentValue, currentN);

    if (timeInfo.isLastDayOfMonth) {
      currentSum = 0.0;
      currentN = 0;

      // Add newest monthly mean to list.
      // TODO Use a more efficient data structure (circular queue).
      for (int i = monthlyMeans.length - 1; i > 0; --i) {
        monthlyMeans[i] = monthlyMeans[i - 1];
      }
      monthlyMeans[0] = currentValue;

      // System.out.printf("[%s] %.2f", TimeLib.formatDate(timeInfo.time), monthlyMeans[0]);
      // for (int i=1; i<monthlyMeans.length; ++i) {
      // System.out.printf(", %.2f", monthlyMeans[i]);
      // }
      // System.out.println();
    }
  }

  public boolean predict()
  {
    double mean = Library.sum(monthlyMeans, 0, nLookback) / (nLookback + 1);
    double ratio = currentValue / mean;
    // System.out.printf("Predict[%s]: %.2f, %.3f\n", TimeLib.formatDate(account.broker.getTime()), mean, ratio);
    return ratio >= 1.0; // TODO incorporate margin
  }
}
