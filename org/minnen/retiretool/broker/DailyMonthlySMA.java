package org.minnen.retiretool.broker;

import java.util.Date;

import org.minnen.retiretool.Library;
import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.NewHighPredictor;

public class DailyMonthlySMA
{
  private final Account  account;
  private final String   name;

  private final double   margin       = 1.0 / 100.0;
  private final int      nLookback    = 2;
  private final double[] monthlyMeans = new double[12];
  private final int      iPrice       = 0;

  private double         currentValue;
  private double         currentSum;
  private int            currentN;

  private final Sequence snp          = new Sequence("SNP.DailySMA");

  public DailyMonthlySMA(Account account, String name)
  {
    this.account = account;
    this.name = name;
  }

  public Sequence getSNP()
  {
    return snp;
  }

  public void init(TimeInfo timeInfo)
  {
    Sequence seq = account.broker.store.getMisc(name);
    assert seq.getEndMS() <= timeInfo.time;

    // Fill in monthly means.
    assert TimeLib.isSameDay(seq.getEndMS(), timeInfo.time);
    double sum = 0.0;
    int n = 0;
    int nCurrentAge = 0;
    long ta = timeInfo.time, tb = timeInfo.time;
    for (int i = seq.length() - 1; i >= 0; --i) {
      final double price = seq.get(i, iPrice);
      final long time = seq.getTimeMS(i);
      final int nMonthsBetween = TimeLib.monthsBetween(timeInfo.time, time);
      assert nMonthsBetween >= nCurrentAge;
      if (nMonthsBetween == nCurrentAge) {
        tb = time;
        sum += price;
        ++n;
      } else {
        assert nMonthsBetween == nCurrentAge + 1;
        assert n > 0;
        double mean = sum / n;

        if (nCurrentAge >= 1) {
          monthlyMeans[nCurrentAge - 1] = mean;
          long ms = TimeLib.toFirstOfMonth(tb);
          // System.out.printf("i=%d [%s]: %.2f\n", i, TimeLib.sdfTime.format(new Date(ms)), mean);
          // System.out.printf(" %d: [%s] -> [%s]\n", nCurrentAge, TimeLib.formatDate(tb), TimeLib.formatDate(ta));
          snp.addData(mean, ms);
        }

        ta = tb = time;

        if (nCurrentAge == 0) {
          currentSum = sum;
          currentN = n;
          currentValue = mean;
        }
        sum = 0.0;
        n = 0;

        // We're done after 12 months.
        if (nMonthsBetween > 12) {
          break;
        }

        nCurrentAge = nMonthsBetween;
      }
    }

    // Include data for last (really first) month.
    if (n > 0) {
      // System.out.printf("%d: [%s] -> [%s]\n", nCurrentAge, TimeLib.formatDate(tb), TimeLib.formatDate(ta));
      double mean = sum / n;
      monthlyMeans[nCurrentAge - 1] = mean;
      // System.out.printf(" %d: [%s] -> [%s] (Last / leftover)\n", nCurrentAge, TimeLib.formatDate(tb),
      // TimeLib.formatDate(ta));
      long ms = TimeLib.toFirstOfMonth(ta / 2 + tb / 2);
      snp.addData(mean, ms);
    }

    assert nCurrentAge == 12;
    assert snp.length() == 12;
    snp.reverse();

    // System.out.println("Monthly Means after Init:");
    // for (int i = 0; i < monthlyMeans.length; ++i) {
    // System.out.printf(" %d: %.2f\n", i, monthlyMeans[i]);
    // }
  }

  public void step(TimeInfo timeInfo)
  {
    Sequence seq = account.broker.store.getMisc(name);
    int index = seq.getClosestIndex(timeInfo.time);
    assert Math.abs(timeInfo.time - seq.getTimeMS(index)) < 8 * TimeLib.MS_IN_HOUR;

    // Incorporate price from today.
    double price = seq.get(index, iPrice);
    currentSum += price;
    ++currentN;
    currentValue = currentSum / currentN;

    // if ((timeInfo.time >= TimeLib.getTime(31, 11, 1950)) && (timeInfo.time < TimeLib.getTime(11, 1, 1951))) {
    // System.out.printf("Step [%s]: %.2f / %d = %.2f (%.2f)\n", TimeLib.formatDate(timeInfo.time), currentSum,
    // currentN,
    // currentValue, price);
    // }

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
      // System.out.printf("End of Month: [%s] %.2f (%d)\n", TimeLib.formatDate(timeInfo.time), currentValue, currentN);
      long ms = TimeLib.toFirstOfMonth(timeInfo.time - 5 * TimeLib.MS_IN_DAY);
      assert ms >= snp.getEndMS();
      if (ms == snp.getEndMS()) {
        snp.set(snp.length() - 1, 0, currentValue);
      } else {
        snp.addData(currentValue, ms);
      }
    }
  }

  public boolean predict()
  {
    long time = account.broker.getTime();

    double mean = Library.sum(monthlyMeans, 0, nLookback) / (nLookback + 1);
    double ratio = currentValue / mean;

    // System.out.printf(" sma: %.2f  price=%.2f\n", mean, currentValue);
    // System.out.printf(" %d: [%.2f", nLookback, monthlyMeans[0]);
    // for (int i = 1; i <= nLookback; ++i)
    // System.out.printf(", %.2f", monthlyMeans[i]);
    // System.out.println("]");

    // System.out.printf("Predict[%s]: %.2f, %.3f\n", TimeLib.formatDate(account.broker.getTime()), mean, ratio);
    // System.out.printf("Predict [%s]: %s\n", TimeLib.formatDate(time), ratio >= 1.0 ? "Risky" : "Safe");

    // TODO incorporate margin
    return ratio >= 1.0;

  }
}
