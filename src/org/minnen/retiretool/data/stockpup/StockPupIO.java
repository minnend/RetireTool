package org.minnen.retiretool.data.stockpup;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;

public class StockPupIO
{
  public static File getPath()
  {
    return new File(DataIO.getFinancePath(), "stockpup");
  }

  public static String getFilename(String symbol)
  {
    return String.format("%s_quarterly_financial_data.csv", symbol.toUpperCase());
  }

  public static URL getURL(String symbol)
  {
    try {
      String link = "http://www.stockpup.com/data/" + getFilename(symbol);
      return new URL(link);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static File getFile(String symbol)
  {
    File path = getPath();
    if (!path.exists() && !path.mkdirs()) return null;
    return new File(path, getFilename(symbol));
  }

  public static File download(String symbol, long replaceAgeMs)
  {
    File file = getFile(symbol);
    try {
      if (!DataIO.shouldDownloadUpdate(file, replaceAgeMs)) return file;
    } catch (IOException e) {
      System.err.println(e.getMessage());
      return null;
    }
    System.out.printf("Download StockPup fundamental data: %s\n", symbol);
    if (!DataIO.copyUrlToFile(getURL(symbol), file)) return null;
    return file;
  }

  public static Sequence loadFundamentals(String symbol, long replaceAgeMs) throws IOException
  {
    File file = download(symbol, replaceAgeMs);
    if (file == null || !file.exists()) return null;
    return DataIO.loadCSV(file, null);
  }

  public static void main(String[] args) throws IOException
  {
    Sequence data = loadFundamentals("AAPL", 0);
    System.out.println(data);
    assert data.getDimNames().size() == data.getNumDims();
  }
}
