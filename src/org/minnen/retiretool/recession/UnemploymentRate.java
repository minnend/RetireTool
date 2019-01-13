package org.minnen.retiretool.recession;

import java.io.IOException;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.util.TimeLib;

public class UnemploymentRate
{
  public static Sequence unrate;
  public static Sequence unrateSMA;

  public static void calculate() throws IOException
  {
    Sequence unrate = FredSeries.fromName("unemployment rate").data;
    System.out.printf("Unemployment: [%s] -> [%s]\n", TimeLib.formatDate(unrate.getStartMS()),
        TimeLib.formatDate(unrate.getEndMS()));

    int nMonthsUnrateSMA = 10;
    Sequence unrateSMA = unrate.calcSMA(nMonthsUnrateSMA);

    UnemploymentRate.unrate = unrate;
    UnemploymentRate.unrateSMA = unrateSMA;
  }
}
