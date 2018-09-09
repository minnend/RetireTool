package org.minnen.retiretool;

import java.time.LocalDate;
import java.time.Month;

import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.FinLib.DividendMethod;

/*
 * Functions that operate on Shiller's data (S&P price, dividends, CPI, GS10, CAPE).
 * Data: http://www.econ.yale.edu/~shiller/data.htm
 */
public class ShillerOld
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
    if (divMethod == DividendMethod.IGNORE_DIVIDENDS) { // no payments => empty sequence
      return new Sequence("Dividends");
    } else if (divMethod == DividendMethod.MONTHLY || divMethod == DividendMethod.NO_REINVEST_MONTHLY) {
      return getData(DIV, "Dividends", shiller);
    } else {
      assert divMethod == DividendMethod.QUARTERLY || divMethod == DividendMethod.NO_REINVEST_QUARTERLY;

      // Dividends at the end of every quarter (march, june, september, december).
      Sequence seq = new Sequence("Dividends");
      double div = 0.0;
      for (int i = 0; i < shiller.length(); ++i) {
        div += shiller.get(i, ShillerOld.DIV);
        LocalDate date = TimeLib.ms2date(shiller.getTimeMS(i));
        Month month = date.getMonth();
        if (month == Month.MARCH || month == Month.JUNE || month == Month.SEPTEMBER || month == Month.DECEMBER) {
          // Time for a dividend!
          date = date.withDayOfMonth(23);
          date = TimeLib.getClosestBusinessDay(date, false);
          seq.addData(div, TimeLib.toMs(date));
          div = 0.0;
        }
      }
      return seq;
    }
  }
}
