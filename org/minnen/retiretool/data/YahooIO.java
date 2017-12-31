package org.minnen.retiretool.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Month;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

/**
 * API for downloading and updating data from Yahoo! Finance.
 */
public class YahooIO
{
  public static final File yahooDir = new File(DataIO.financePath, "yahoo");

  /**
   * Load data from a Yahoo CSV file.
   * 
   * data,open,high,low,close,volume,adj close
   * 
   * @param file file to load
   * @return Sequence with data loaded from the given file.
   */
  public static Sequence loadData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Yahoo CSV file (%s)", file.getPath()));
    }
    // System.out.printf("Loading Yahoo data file: [%s]\n", file.getPath());

    int iDate = -1;
    int iOpen = -1;
    int iClose = -1;
    int iLow = -1;
    int iHigh = -1;
    int iVolume = -1;
    int iAdjClose = -1;

    BufferedReader in = new BufferedReader(new FileReader(file));
    String name = file.getName().replaceFirst("[\\.][^\\\\/\\.]+$", "");
    Sequence data = new Sequence(name);
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      String[] toks = line.trim().split(",");
      if (toks == null || toks.length != 7) {
        System.err.printf("Error parsing Yahoo data: [%s]\n", line);
        continue;
      }
      for (int i = 0; i < toks.length; ++i) {
        toks[i] = toks[i].trim();
      }

      // Parse the header.
      if (iDate < 0) {
        for (int i = 0; i < toks.length; ++i) {
          String field = toks[i].toLowerCase().replace(" ", "");
          if (field.equals("date")) iDate = i;
          else if (field.equals("open")) iOpen = i;
          else if (field.equals("high")) iHigh = i;
          else if (field.equals("low")) iLow = i;
          else if (field.equals("close")) iClose = i;
          else if (field.equals("volume")) iVolume = i;
          else if (field.equals("adjclose")) iAdjClose = i;
        }
        continue;
      }

      try {
        long time = TimeLib.parseDate(toks[iDate]);
        double open = Double.parseDouble(toks[iOpen]);
        double high = Double.parseDouble(toks[iHigh]);
        double low = Double.parseDouble(toks[iLow]);
        double close = Double.parseDouble(toks[iClose]);
        double volume = Double.parseDouble(toks[iVolume]);
        double adjClose = Double.parseDouble(toks[iAdjClose]);
        FeatureVec fv = new FeatureVec(6);
        fv.set(FinLib.Open, open);
        fv.set(FinLib.High, high);
        fv.set(FinLib.Low, low);
        fv.set(FinLib.Close, close);
        fv.set(FinLib.Volume, volume);
        fv.set(FinLib.AdjClose, adjClose);
        data.addData(fv, time);
      } catch (NumberFormatException e) {
        System.err.printf("Error parsing CSV data: [%s]\n", line);
        continue;
      }
    }
    in.close();
    if (data.getStartMS() > data.getEndMS()) {
      data.reverse();
    }
    return data;
  }

  public static void saveYahooData(Sequence seq, File file) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("Date,Open,High,Low,Close,Volume,Adj Close\n");
      String[] fields = new String[7];
      for (FeatureVec v : seq) {
        fields[0] = TimeLib.formatYMD(v.getTime());
        fields[1] = String.format("%.4f", v.get(FinLib.Open));
        fields[2] = String.format("%.4f", v.get(FinLib.High));
        fields[3] = String.format("%.4f", v.get(FinLib.Low));
        fields[4] = String.format("%.4f", v.get(FinLib.Close));
        fields[5] = String.format("%.0f", v.get(FinLib.Volume));
        fields[6] = String.format("%.4f", v.get(FinLib.AdjClose));
        writer.write(String.join(",", fields) + "\n");
      }
    }
  }

  /**
   * @deprecated The Yahoo web API changed so it's unlikely that this API still works. Use Tiingo instead.
   */
  public static URL buildURL(String symbol)
  {
    return buildURL(symbol, LocalDate.of(1900, Month.JANUARY, 1));
  }

  /**
   * @deprecated The Yahoo web API changed so it's unlikely that this API still works. Use Tiingo instead.
   */
  public static URL buildURL(String symbol, LocalDate startDate)
  {
    try {
      // http://stackoverflow.com/questions/754593/source-of-historical-stock-data
      // http://web.archive.org/web/20140325063520/http://www.gummy-stuff.org/Yahoo-data.htm
      String address = String.format(
          "https://ichart.yahoo.com/table.csv?s=%s&a=%d&b=%d&c=%d&d=11&e=31&f=2050&g=d&ignore=.csv", symbol,
          startDate.getMonthValue() - 1, startDate.getDayOfMonth(), startDate.getYear());
      return new URL(address);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * @deprecated The Yahoo web API changed so it's unlikely that this API still works. Use Tiingo instead.
   */
  public static boolean updateDailyData(String symbol, long replaceAgeMs)
  {
    File file = YahooIO.getFile(symbol);
    if (!file.exists()) {
      file = YahooIO.downloadDailyData(symbol, 0);
      return (file != null);
    }

    if (!DataIO.isFileOlder(file, replaceAgeMs)) {
      // System.out.printf("Recent file already exists (%s).\n", file.getName());
      return false;
    }

    // File exists so try to load it.
    Sequence seqOld = null;
    try {
      seqOld = YahooIO.loadData(file);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    if (seqOld == null || seqOld.getEndMS() == TimeLib.TIME_ERROR) return false;

    // Download new data.
    LocalDate startDate = TimeLib.ms2date(seqOld.getEndMS());
    System.out.printf("Update data: %s [%s]\n", symbol, startDate);
    try {
      URL url = buildURL(symbol, startDate);
      File tmpFile = File.createTempFile(String.format("yahoo-%s-", symbol), null);
      if (!DataIO.copyUrlToFile(url, tmpFile)) return false;
      Sequence seqNew = YahooIO.loadData(tmpFile);
      assert seqNew.getStartMS() == seqOld.getEndMS();

      // Add new data to old sequence.
      for (int i = 1; i < seqNew.length(); ++i) {
        seqOld.addData(seqNew.get(i));
      }

      tmpFile.delete();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }

    // Write new file.
    try {
      seqOld.reverse(); // Yahoo data has newest days first
      File newFile = new File(file.getAbsolutePath() + ".new");
      YahooIO.saveYahooData(seqOld, newFile);

      // We have the new file so delete old and rename.
      file.delete();
      newFile.renameTo(file);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  /**
   * Download Yahoo financial data into the given file, or create a file if the path is a directory.
   * 
   * @deprecated The Yahoo web API changed so it's unlikely that this API still works. Use Tiingo instead.
   * 
   * @param symbol symbol to download
   * @param replaceAgeMs If the file exists and is older than this value (in ms), replace it; otherwise, don't download
   *          new data.
   * @return File in which that data is stored or null if there was an error.
   */
  public static File downloadDailyData(String symbol, long replaceAgeMs)
  {
    File file = getFile(symbol);
    try {
      if (!DataIO.shouldDownloadUpdate(file, replaceAgeMs)) return file;
    } catch (IOException e) {
      return null;
    }
    System.out.printf("Download data: %s\n", symbol);
    URL url = buildURL(symbol);
    DataIO.copyUrlToFile(url, file);
    return file;
  }

  public static File getFile(String symbol)
  {
    if (!yahooDir.exists() && !yahooDir.mkdirs()) return null;
    return new File(yahooDir, symbol + ".csv");
  }
}
