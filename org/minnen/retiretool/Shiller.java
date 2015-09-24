package org.minnen.retiretool;

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

  /** @return Sequence containing all inflation (CPI) data. */
  public static Sequence getInflationData(Sequence shiller)
  {
    return getInflationData(shiller, 0, shiller.size() - 1);
  }

  /** @return Sequence containing inflation (CPI) data in the given range (inclusive). */
  public static Sequence getInflationData(Sequence shiller, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += shiller.length();
    }
    if (iEnd < 0) {
      iEnd += shiller.length();
    }
    Sequence cpi = new Sequence("CPI");
    for (int i = iStart; i <= iEnd; ++i) {
      cpi.addData(shiller.get(i, CPI), shiller.getTimeMS(i));
    }
    return cpi;
  }

  /** @return Sequence containing all CAPE data. */
  public static Sequence getCapeData(Sequence shiller)
  {
    return getCapeData(shiller, 0, shiller.size() - 1);
  }

  /** @return Sequence containing CAPE data in the given range (inclusive). */
  public static Sequence getCapeData(Sequence shiller, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += shiller.length();
    }
    if (iEnd < 0) {
      iEnd += shiller.length();
    }
    Sequence cape = new Sequence("CAPE");
    for (int i = iStart; i <= iEnd; ++i) {
      cape.addData(shiller.get(i, CAPE), shiller.getTimeMS(i));
    }
    return cape;
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

  /** @return Sequence containing all bond data. */
  public static Sequence getBondData(Sequence shiller)
  {
    return getBondData(shiller, 0, shiller.size() - 1);
  }

  /** @return Sequence containing bond data in the given range (inclusive). */
  public static Sequence getBondData(Sequence shiller, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += shiller.length();
    }
    if (iEnd < 0) {
      iEnd += shiller.length();
    }
    Sequence seq = new Sequence("10-Year Treasury Notes");
    for (int i = iStart; i <= iEnd; ++i) {
      seq.addData(shiller.get(i, GS10), shiller.getTimeMS(i));
    }
    return seq;
  }
}
