package org.minnen.retiretool.recession;

import java.io.IOException;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.util.TimeLib;

/**
 * Calculates the yield spread between the 10-year and 2-year treasury notes.
 */
public class YieldSpread
{
  public static Sequence spread = null;

  public static void calculate() throws IOException
  {
    Sequence treasury10 = FredSeries.fromName("10-year-treasury").data;
    Sequence treasury2 = FredSeries.fromName("2-year-treasury").data;
    System.out.printf("%20s: [%s] -> [%s]\n", treasury10.getName(), TimeLib.formatDate(treasury10.getStartMS()),
        TimeLib.formatDate(treasury10.getEndMS()));
    System.out.printf("%20s: [%s] -> [%s]\n", treasury2.getName(), TimeLib.formatDate(treasury2.getStartMS()),
        TimeLib.formatDate(treasury2.getEndMS()));

    long commonStart = TimeLib.calcCommonStart(treasury2, treasury10);
    long commonEnd = TimeLib.calcCommonEnd(treasury2, treasury10);
    treasury2 = treasury2.subseq(commonStart, commonEnd);
    treasury10 = treasury10.subseq(commonStart, commonEnd);
    Sequence treasurySpread = treasury10.sub(treasury2)._mul(10);
    treasurySpread.setName("2-10 Treasury Spread");
    System.out.printf("%20s: [%s] -> [%s]\n", treasurySpread.getName(), TimeLib.formatDate(treasurySpread.getStartMS()),
        TimeLib.formatDate(treasurySpread.getEndMS()));

    YieldSpread.spread = treasurySpread;
  }
}
