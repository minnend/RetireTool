package org.minnen.retiretool.data.tiingo;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.util.TimeLib;

/**
 * Loads the latest "supported tickers" file, filters the funds, and downloads metadata and EOD data for all funds that
 * pass the filters.
 */
public class DownloadFilteredFunds
{
  public static void saveFundMetadata(List<TiingoFund> funds) throws IOException
  {
    for (TiingoFund fund : funds) {
      if (!TiingoIO.saveFundMetadata(fund)) break;
      fund.loadMetadata();
    }
  }

  public static void saveFundEodData(List<TiingoFund> funds, boolean replaceExisting) throws IOException
  {
    for (TiingoFund fund : funds) {
      if (!TiingoIO.updateFundEodData(fund)) break;
    }
  }

  public static void main(String[] args) throws IOException
  {
    if (!TiingoIO.downloadLatestSupportedTickerCSV()) {
      System.err.println("Failed to update supported tickers CSV");
      System.exit(1);
    }

    List<TiingoFund> funds = TiingoIO.loadTickers();
    System.out.printf("Funds: %d\n", funds.size());

    // Only funds that trade in USD.
    funds.removeIf(p -> !p.currency.equalsIgnoreCase("usd"));
    System.out.printf("Funds (USD): %d\n", funds.size());

    // Only mutual funds (or ETFs).
    funds.removeIf(p -> !p.assetType.equalsIgnoreCase("mutual fund") && !p.assetType.equalsIgnoreCase("etf"));
    System.out.printf("Funds (etf / mutual funds): %d\n", funds.size());

    // Only funds with recent date.
    LocalDate today = TimeLib.ms2date(TimeLib.getTime());
    LocalDate endAfter = today.minusDays(5);
    funds.removeIf(p -> !p.end.isAfter(endAfter));
    System.out.printf("Funds (recent): %d\n", funds.size());

    // Only funds with significant history.
    LocalDate startBefore = LocalDate.of(1999, 1, 1);
    funds.removeIf(p -> p.start.isAfter(startBefore));
    System.out.printf("Funds (old): %d\n", funds.size());

    // Only "V" funds.
    funds.removeIf(p -> !p.ticker.startsWith("V"));
    System.out.printf("Funds (V): %d\n", funds.size());

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
    int nMissingMeta = 0;
    int nMissingEod = 0;
    for (TiingoFund fund : funds) {
      File file = TiingoIO.getEodFile(fund.ticker);
      if (!file.exists()) ++nMissingEod;

      TiingoMetadata meta = null;
      file = TiingoIO.getMetadataFile(fund.ticker);
      if (!file.exists()) {
        ++nMissingMeta;
      } else {
        meta = TiingoIO.loadMetadata(fund.ticker);
      }

      System.out.printf("%s  %s\n", fund, meta == null ? "" : meta.name);
    }
    System.out.printf("# missing: meta=%d  eod=%d\n", nMissingMeta, nMissingEod);

    saveFundMetadata(funds);

    // Filter by fund name (not ticker / symbol).
    // funds.removeIf(p -> !p.meta.name.toLowerCase().contains("vanguard"));
    // System.out.printf("Funds (vanguard): %d\n", funds.size());
    // for (TiingoFund fund : funds) {
    // System.out.printf("%s %s\n", fund, fund.meta.name);
    // }

    saveFundEodData(funds, false);
  }
}
