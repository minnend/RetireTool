package org.minnen.retiretool.tiingo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

import org.minnen.retiretool.data.Sequence;

public class TiingoFund
{
  public final static HashMap<String, TiingoFund> fundMap = new HashMap<>();

  public final String                             ticker;
  public final String                             exchange;
  public final String                             assetType;
  public final String                             currency;
  public final LocalDate                          start;
  public final LocalDate                          end;

  public Sequence                                 seq;

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

  public static TiingoFund get(String ticker)
  {
    return fundMap.get(ticker);
  }
}
