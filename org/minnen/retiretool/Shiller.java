package org.minnen.retiretool;

import java.util.Calendar;

import org.minnen.retiretool.FinLib.DividendMethod;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;

/*
 * Functions that operate on Shiller's data (S&P price, dividends, CPI, GS10, CAPE).
 * Data: http://www.econ.yale.edu/~shiller/data.htm
 */
public class Shiller
{
  public static int PRICE = 0;
  public static int DIV   = 1;
  public static int CPI   = 2;
  public static int GS10  = 3;
  public static int CAPE  = 4;

  public static Sequence getData(int dim, String name, Sequence shiller)
  {
    return getData(dim, name, shiller, 0, shiller.size() - 1);
  }

  public static Sequence getData(int dim, String name, Sequence shiller, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += shiller.length();
    }
    if (iEnd < 0) {
      iEnd += shiller.length();
    }
    Sequence seq = new Sequence(name);
    for (int i = iStart; i <= iEnd; ++i) {
      seq.addData(shiller.get(i, dim), shiller.getTimeMS(i));
    }
    return seq;
  }

  /** @return Sequence containing all stock and dividend data. */
  public static Sequence getStockData(Sequence shiller)
  {
    return getStockData(shiller, 0, shiller.size() - 1);
  }

  /** @return Sequence containing stock and dividend data in the given range (inclusive). */
  public static Sequence getStockData(Sequence shiller, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += shiller.length();
    }
    if (iEnd < 0) {
      iEnd += shiller.length();
    }
    Sequence seq = new Sequence("S&P");
    for (int i = iStart; i <= iEnd; ++i) {
      seq.addData(new FeatureVec(2, shiller.get(i, PRICE), shiller.get(i, DIV)), shiller.getTimeMS(i));
    }
    return seq;
  }

  public static Sequence getDividendPayments(Sequence shiller, DividendMethod divMethod)
  {
    if (divMethod == DividendMethod.NO_REINVEST) { // no payments => empty sequence
      return new Sequence("Dividends");
    } else if (divMethod == DividendMethod.MONTHLY) {
      return getData(DIV, "Dividends", shiller);
    } else {
      assert divMethod == DividendMethod.QUARTERLY;

      // Dividends at the end of every quarter (march, june, september, december).
      Sequence seq = new Sequence("Dividends");
      Calendar cal = TimeLib.now();
      double div = 0.0;
      for (int i = 0; i < shiller.length(); ++i) {
        long timeMS = shiller.getTimeMS(i);
        div += shiller.get(i, Shiller.DIV);
        cal.setTimeInMillis(timeMS);
        int month = cal.get(Calendar.MONTH);
        if (month % 3 == 2) { // time for a dividend!
          timeMS = FinLib.getClosestBusinessDay(timeMS, 23, false);
          seq.addData(div, timeMS);
          div = 0.0;
        }
      }
      return seq;
    }
  }
}
