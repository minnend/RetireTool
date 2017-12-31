package org.minnen.retiretool.tiingo;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.util.TimeLib;

public class TiingoFund
{
  public final static HashMap<String, TiingoFund> fundMap = new HashMap<>();

  public final String                             ticker;
  public final String                             exchange;
  public final String                             assetType;
  public final String                             currency;
  public final LocalDate                          start;
  public final LocalDate                          end;

  public Sequence                                 data;
  public TiingoMetadata                           meta;

  public TiingoFund(String ticker, String exchange, String assetType, String currency, LocalDate start, LocalDate end)
  {
    this.ticker = ticker;
    this.exchange = exchange;
    this.assetType = assetType;
    this.currency = currency;
    this.start = start;
    this.end = end;
    fundMap.put(ticker, this);
  }

  public static TiingoFund fromString(String info)
  {
    String[] toks = info.split(",");
    if (toks.length != 6) {
      // System.err.printf("Error parsing tiingo fund string: [%s]\n", info);
      return null;
    }
    for (int i = 0; i < toks.length; ++i) {
      toks[i] = toks[i].trim();
      if (toks[i].isEmpty()) return null;
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    LocalDate start = LocalDate.parse(toks[4], formatter);
    LocalDate end = LocalDate.parse(toks[5], formatter);
    return new TiingoFund(toks[0], toks[1], toks[2], toks[3], start, end);
  }

  public boolean loadData()
  {
    if (data != null) return true;
    try {
      if (!TiingoIO.saveFundMetadata(this)) return false;
      if (!TiingoIO.saveFundEodData(this, false)) return false;

      data = TiingoIO.loadEodData(ticker);
      meta = TiingoIO.loadMetadata(ticker);

      // We trust data from "supported tickers" file more than individual meta files.
      meta.end = end;
      meta.start = start;

      LocalDate dataStart = TimeLib.ms2date(data.getStartMS());
      LocalDate dataEnd = TimeLib.ms2date(data.getEndMS());

      // If start date is earlier, download the entire data file again.
      if (start.isBefore(dataStart)) {
        System.out.printf("New start time: %s  [%s] -> [%s]\n", ticker, dataStart, start);
        if (!TiingoIO.saveFundEodData(this, true)) {
          System.err.printf("Failed to download EOD data (%s)\n", ticker);
          return false;
        }

        // Reload after downloading most recent data.
        data = TiingoIO.loadEodData(ticker);
        dataStart = TimeLib.ms2date(data.getStartMS());
        dataEnd = TimeLib.ms2date(data.getEndMS());
      }

      if (end.isAfter(dataEnd)) {
        long nDays = ChronoUnit.DAYS.between(dataEnd, end);
        System.out.printf("New end time: %s  [%s] -> [%s]  (%d days)\n", ticker, dataEnd, end, nDays);
        if (!TiingoIO.updateFundEodData(this)) {
          System.err.printf("Failed to update EOD data (%s)\n", ticker);
          return false;
        }
      }

      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public static TiingoFund get(String ticker)
  {
    return fundMap.get(ticker);
  }
}
