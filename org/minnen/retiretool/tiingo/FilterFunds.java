package org.minnen.retiretool.tiingo;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.util.TimeLib;

public class FilterFunds
{
  public static void saveFundMetadata(List<TiingoFund> funds, File dir, boolean replaceExisting) throws IOException
  {
    for (TiingoFund fund : funds) {
      if (!TiingoIO.saveFundMetadata(fund, replaceExisting)) break;
    }
  }

  public static void saveFundEodData(List<TiingoFund> funds, boolean replaceExisting) throws IOException
  {
    for (TiingoFund fund : funds) {
      if (!TiingoIO.saveFundEodData(fund, replaceExisting)) break;
    }
  }

  public static void main(String[] args) throws IOException
  {
    List<TiingoFund> funds = TiingoIO.loadTickers(Tiingo.supportedTickersPath);
    System.out.printf("Funds: %d\n", funds.size());

    // Only funds that trade in USD.
    funds.removeIf(p -> !p.currency.equalsIgnoreCase("usd"));
    System.out.printf("Funds (USD): %d\n", funds.size());

    // Only mutual funds (or ETFs).
    funds.removeIf(p -> !p.assetType.equalsIgnoreCase("mutual fund") && !p.assetType.equalsIgnoreCase("etf"));
    System.out.printf("Funds (etf / mutual funds): %d\n", funds.size());

    // Only funds with recent date.
    LocalDate today = TimeLib.ms2date(TimeLib.getTime());
    LocalDate endAfter = today.minusDays(7);
    funds.removeIf(p -> !p.end.isAfter(endAfter));
    System.out.printf("Funds (recent): %d\n", funds.size());

    // Only funds with significant history.
    LocalDate startBefore = LocalDate.of(1996, 1, 1);
    funds.removeIf(p -> p.start.isAfter(startBefore));
    System.out.printf("Funds (old): %d\n", funds.size());

    // Only Vanguard funds.
    // funds.removeIf(p -> !p.ticker.startsWith("V"));
    // System.out.printf("Funds (V): %d\n", funds.size());

    // Sort by start date.
    funds.sort(new Comparator<TiingoFund>()
    {
      @Override
      public int compare(TiingoFund a, TiingoFund b)
      {
        return a.start.compareTo(b.start);
      }
    });

    // Print funds that pass all tests.
    // for (TiingoFund fund : funds) {
    // System.out.printf("%s [%s] -> [%s]\n", fund.ticker, fund.start, fund.end);
    // }

    saveFundMetadata(funds, Tiingo.metaPath, false);
    saveFundEodData(funds, false);
  }
}
