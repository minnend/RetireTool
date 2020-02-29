package org.minnen.retiretool.explore;

import java.io.IOException;
import java.time.LocalDate;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

/**
 * Determines the first day with the same (dividend-adjusted) price as today. Effectively, buying today is equivalent to
 * buying on that day.
 */
public class TimeMachine
{
  private final static int iPriceDim = FinLib.AdjClose;

  private static void fromToday(Sequence data)
  {
    fromIndex(-1, data);
  }

  private static void fromDate(LocalDate date, Sequence data)
  {
    int index = data.getClosestIndex(TimeLib.toMs(date));
    fromIndex(index, data);
  }

  private static void fromIndex(int index, Sequence data)
  {
    if (index < 0) index += data.length();
    double price = data.get(index, iPriceDim);
    int bestIndex = forPrice(price, data);
    double bestPrice = data.get(bestIndex, iPriceDim);

    System.out.printf("[%s] @ $%.2f\n", TimeLib.formatDate(data.getTimeMS(index)), price);

    long duration = data.getTimeMS(index) - data.getTimeMS(bestIndex);
    if (duration > 0) {
      System.out.printf("[%s] @ $%.2f  ($%.2f)\n", TimeLib.formatDate(data.getTimeMS(bestIndex)), bestPrice,
          data.get(bestIndex + 1, iPriceDim));
      System.out.printf("%s\n", TimeLib.formatDuration(duration));
    } else {
      System.out.println("All time high!");
    }
  }

  private static int forPrice(double priceToday, Sequence data)
  {
    Sequence priceSeq = data.extractDims(iPriceDim);
    double[] prices = priceSeq.extractDim(0);
    double margin = Math.max(0.01, priceToday * 0.002); // within 0.2%
    int bestIndex = 0;
    for (int i = 1; i < prices.length; ++i) {
      // System.out.printf("%d [%s] %.2f\n", i, TimeLib.formatDate(priceSeq.getTimeMS(i)), prices[i]);
      if (prices[i] - priceToday > -margin) {
        bestIndex = i - 1;
        break;
      }
    }
    return bestIndex;
  }

  public static void main(String[] args) throws IOException
  {
    TiingoFund fund = TiingoFund.fromSymbol("SPY", true);
    Sequence data = fund.data;
    System.out.printf("[%s] -> [%s]\n", TimeLib.formatDate(data.getStartMS()), TimeLib.formatDate(data.getEndMS()));

    fromToday(data);

    for (int percentDown : new int[] { 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90 }) {
      double price = data.getLast(iPriceDim) * (1.0 - percentDown / 100.0);
      int x = forPrice(price, data);
      long ms = data.getTimeMS(x);
      String[] split = TimeLib.formatDuration(data.getEndMS() - ms).split(" ");
      System.out.printf("%2d%%: $%6.2f %4s %-6s [%s]\n", percentDown, price, split[0], split[1],
          TimeLib.formatDate2(ms));
    }
  }
}
