package org.minnen.retiretool.tiingo;

import java.time.LocalDate;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

public class TiingoMetadata
{
  public final static HashMap<String, TiingoMetadata> fundMap = new HashMap<>();

  public final String                                 name;
  public final String                                 ticker;
  public final String                                 description;
  public final String                                 exchange;

  public LocalDate                                    start;
  public LocalDate                                    end;

  public TiingoMetadata(String name, String ticker, String description, String exchange, LocalDate start, LocalDate end)
  {
    this.name = name;
    this.ticker = ticker;
    this.description = description;
    this.exchange = exchange;
    this.start = start;
    this.end = end;
    fundMap.put(ticker, this);
  }

  public static TiingoMetadata fromString(String json)
  {
    try {
      JSONObject obj = new JSONObject(json);
      return new TiingoMetadata(obj.getString("name"), obj.getString("ticker"), obj.getString("description"),
          obj.getString("exchangeCode"), LocalDate.parse(obj.getString("startDate")),
          LocalDate.parse(obj.getString("endDate")));
    } catch (JSONException e) {
      System.err.println(e);
      return null;
    }
  }

  @Override
  public String toString()
  {
    return String.format("[%s: %s  [%s] -> [%s]]", ticker, name, start, end);
  }

  public static TiingoMetadata get(String ticker)
  {
    return fundMap.get(ticker);
  }
}
