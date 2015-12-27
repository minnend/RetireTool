package org.minnen.retiretool;

import java.io.File;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.util.TimeLib;

public class AdaptiveAlloc
{
  public static final String[] fundSymbols = new String[] { "SPY", "QQQ", "EWU", "EWG", "EWJ", "XLK", "XLE" };

  public static void main(String[] args)
  {
    File dataDir = new File("g:/research/finance/yahoo/");
    if (!dataDir.exists()) {
      dataDir.mkdirs();
    }

    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(dataDir, symbol);
      DataIO.updateDailyDataFromYahoo(file, symbol, 2 * TimeLib.MS_IN_HOUR);
    }
  }
}
