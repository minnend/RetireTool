package org.minnen.retiretool.recession;

import java.io.IOException;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.util.TimeLib;

public class InitialClaims
{
  public static Sequence claims;
  public static Sequence claimsSMA;

  public static void calculate() throws IOException
  {
    Sequence icsa = FredSeries.fromName("unemployment initial claims").data;
    System.out.printf("Initial claims: [%s] -> [%s]\n", TimeLib.formatDate(icsa.getStartMS()),
        TimeLib.formatDate(icsa.getEndMS()));

    int nMonthsSMA = 40;  // 10 months of weekly data 
    Sequence icsaSMA = icsa.calcSMA(nMonthsSMA);

    InitialClaims.claims = icsa;
    InitialClaims.claimsSMA = icsaSMA;
  }
}
