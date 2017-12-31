package org.minnen.retiretool.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.data.tiingo.TiingoMetadata;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class TiingoIO
{
  public static final String                 auth                 = System.getenv("tiingo.auth");

  public static final File                   tiingoPath           = new File(DataIO.financePath, "tiingo");
  public static final File                   metaPath             = new File(tiingoPath, "meta");
  public static final File                   eodPath              = new File(tiingoPath, "eod");

  public static final String                 supportedTickersUrl  = "https://apimedia.tiingo.com/docs/tiingo/daily/supported_tickers.zip";
  public static final File                   supportedTickersPath = new File(tiingoPath,
      "tiingo_supported_tickers.csv");

  private static Map<File, List<TiingoFund>> cacheFunds           = new HashMap<File, List<TiingoFund>>();

  public static void clearMetadataCache()
  {
    cacheFunds.clear();
  }

  public static URL buildDataURL(String symbol)
  {
    return buildDataURL(symbol, LocalDate.of(1900, Month.JANUARY, 1));
  }

  public static URL buildDataURL(String symbol, LocalDate startDate)
  {
    try {
      // https://api.tiingo.com/docs/tiingo/daily
      String startDateString = String.format("%04d-%02d-%02d", startDate.getYear(), startDate.getMonthValue(),
          startDate.getDayOfMonth());
      LocalDate endDate = TimeLib.ms2date(TimeLib.getTime());
      String endDateString = String.format("%04d-%02d-%02d", endDate.getYear(), endDate.getMonthValue(),
          endDate.getDayOfMonth());
      String address = String.format(
          "https://api.tiingo.com/tiingo/daily/%s/prices?token=%s&format=csv&resampleFreq=daily&startDate=%s&endDate=%s",
          symbol, TiingoIO.auth, startDateString, endDateString);
      // System.out.printf("URL: %s\n", address);
      return new URL(address);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static URL buildMetaURL(String symbol)
  {
    try {
      // https://api.tiingo.com/docs/tiingo/daily
      String address = "https://api.tiingo.com/tiingo/daily/" + symbol;
      // System.out.printf("URL: %s\n", address);
      return new URL(address);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Load metadata from the default Tiingo supported tickers file.
   * 
   * @return List of Tiingo funds
   * @throws IOException if there is a problem reading the file.
   */
  public static List<TiingoFund> loadTickers() throws IOException
  {
    return loadTickers(TiingoIO.supportedTickersPath);
  }

  /**
   * Load metadata from Tiingo supported tickers file.
   * 
   * @param file file to load
   * @return List of Tiingo funds
   * @throws IOException if there is a problem reading the file.
   */
  private static List<TiingoFund> loadTickers(File file) throws IOException
  {
    if (cacheFunds.containsKey(file)) {
      return cacheFunds.get(file);
    }

    if (!downloadLatestSupportedTickerCSV()) {
      throw new IOException("Failed to download latest supported tickers CSV");
    }
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read tiingo file (%s)", file.getPath()));
    }
    System.out.printf("Loading tiingo file: [%s]\n", file.getPath());
    List<TiingoFund> funds = new ArrayList<TiingoFund>();
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.startsWith("ticker")) continue; // skip header
        TiingoFund fund = TiingoFund.fromString(line);
        if (fund == null) continue;
        funds.add(fund);
      }
    }

    cacheFunds.put(file, funds);
    return funds;
  }

  public static File getMetadataFile(String symbol)
  {
    return new File(TiingoIO.metaPath, symbol + "-meta.json");
  }

  public static File getEodFile(String symbol)
  {
    return new File(TiingoIO.eodPath, symbol + "-eod.csv");
  }

  public static TiingoMetadata loadMetadata(String symbol)
  {
    File file = getMetadataFile(symbol);
    try {
      List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1);
      String json = String.join("\n", lines);
      return TiingoMetadata.fromString(json);
    } catch (IOException e) {
      System.err.printf("Failed to load Tiingo metadata: %s\n", symbol);
      System.err.println(e);
      return null;
    }
  }

  public static Sequence loadEodData(String symbol) throws IOException
  {
    File file = getEodFile(symbol);
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Tiingo CSV file (%s)", file.getPath()));
    }

    int iDate = -1;
    int iClose = -1;
    int iHigh = -1;
    int iLow = -1;
    int iOpen = -1;
    int iVolume = -1;
    int iAdjClose = -1;
    int iAdjHigh = -1;
    int iAdjLow = -1;
    int iAdjOpen = -1;
    int iAdjVolume = -1;
    int iDivCash = -1;
    int iSplitFactor = -1;

    Sequence seq = new Sequence(symbol);
    BufferedReader in = new BufferedReader(new FileReader(file));
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      String[] toks = DataIO.splitCSV(line, ",");
      if (toks == null || toks.length != 13) {
        System.err.printf("Error parsing Tiingo data: [%s]\n", line);
        continue;
      }

      // Parse the header.
      if (iDate < 0) {
        for (int i = 0; i < toks.length; ++i) {
          String field = toks[i].toLowerCase();
          if (field.equals("date")) iDate = i;
          else if (field.equals("close")) iClose = i;
          else if (field.equals("high")) iHigh = i;
          else if (field.equals("low")) iLow = i;
          else if (field.equals("open")) iOpen = i;
          else if (field.equals("volume")) iVolume = i;
          else if (field.equals("adjclose")) iAdjClose = i;
          else if (field.equals("adjhigh")) iAdjHigh = i;
          else if (field.equals("adjlow")) iAdjLow = i;
          else if (field.equals("adjopen")) iAdjOpen = i;
          else if (field.equals("adjvolume")) iAdjVolume = i;
          else if (field.equals("divcash")) iDivCash = i;
          else if (field.equals("splitfactor")) iSplitFactor = i;
        }
        continue;
      }

      try {
        long time = TimeLib.toMs(LocalDate.parse(toks[iDate]));
        double close = Double.parseDouble(toks[iClose]);
        double high = Double.parseDouble(toks[iHigh]);
        double low = Double.parseDouble(toks[iLow]);
        double open = Double.parseDouble(toks[iOpen]);
        double volume = Double.parseDouble(toks[iVolume]);
        double adjClose = Double.parseDouble(toks[iAdjClose]);
        double adjHigh = Double.parseDouble(toks[iAdjHigh]);
        double adjLow = Double.parseDouble(toks[iAdjLow]);
        double adjOpen = Double.parseDouble(toks[iAdjOpen]);
        double adjVolume = Double.parseDouble(toks[iAdjVolume]);
        double divCash = Double.parseDouble(toks[iDivCash]);
        double splitFactor = Double.parseDouble(toks[iSplitFactor]);

        FeatureVec fv = new FeatureVec(12);
        fv.set(FinLib.Open, open);
        fv.set(FinLib.High, high);
        fv.set(FinLib.Low, low);
        fv.set(FinLib.Close, close);
        fv.set(FinLib.Volume, volume);
        fv.set(FinLib.AdjClose, adjClose);
        fv.set(FinLib.AdjHigh, adjHigh);
        fv.set(FinLib.AdjLow, adjLow);
        fv.set(FinLib.AdjOpen, adjOpen);
        fv.set(FinLib.AdjVolume, adjVolume);
        fv.set(FinLib.DivCash, divCash);
        fv.set(FinLib.SplitFactor, splitFactor);
        seq.addData(fv, time);
      } catch (NumberFormatException e) {
        System.err.printf("Error parsing Tiingo CSV data: [%s]\n", line);
        continue;
      }
    }
    in.close();
    if (seq.getStartMS() > seq.getEndMS()) {
      seq.reverse();
    }
    return seq;
  }

  private static String httpGetToString(URL url) throws IOException
  {
    // https://api.tiingo.com/docs/tiingo/daily
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    con.setRequestProperty("Content-Type", "application/json");
    con.setRequestProperty("Authorization", "Token " + TiingoIO.auth);
    if (con.getResponseCode() != 200) {
      System.out.printf("Response: %d - %s\n", con.getResponseCode(), con.getResponseMessage());
      return null;
    }

    try (InputStream input = con.getInputStream()) {
      return IOUtils.toString(input);
    }
  }

  private static boolean httpGetToFile(URL url, File file) throws IOException
  {
    String data = httpGetToString(url);
    if (data == null) return false;

    if (data.startsWith("Error")) {
      System.out.println(data);
      return false;
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write(data);
      return true;
    }
  }

  public static boolean saveFundEodData(TiingoFund fund, boolean replaceExisting) throws IOException
  {
    if (!TiingoIO.eodPath.exists()) {
      TiingoIO.eodPath.mkdirs();
    }
    URL url = TiingoIO.buildDataURL(fund.ticker);
    File file = getEodFile(fund.ticker);
    if (replaceExisting || !file.exists()) {
      System.out.printf("Downloading: %s (eod)  [%s] -> [%s]\n", fund.ticker, fund.start, fund.end);
      return TiingoIO.httpGetToFile(url, file);
    }
    return true;
  }

  public static boolean updateFundEodData(TiingoFund fund) throws IOException
  {
    File file = getEodFile(fund.ticker);
    if (!file.exists()) {
      return saveFundEodData(fund, true);
    }

    List<String> lines = Files.readAllLines(file.toPath());
    String header = lines.get(0).trim();
    String lastLine = lines.get(lines.size() - 1);
    String[] toks = DataIO.splitCSV(header);
    assert toks[0].toLowerCase().equals("date");

    toks = DataIO.splitCSV(lastLine);
    LocalDate lastDate = LocalDate.parse(toks[0]);

    URL url = buildDataURL(fund.ticker, lastDate);
    String data = httpGetToString(url);

    String[] newLines = DataIO.splitCSV(data, "\n");
    if (newLines.length < 3) return true; // no new data

    if (!newLines[0].equals(header)) {
      System.out.printf("New header => replace entire file (%s)\n", fund.ticker);
      System.out.printf("Old: %s\n", header);
      System.out.printf("New: %s\n", newLines[0]);
      return saveFundEodData(fund, true);
    }

    if (!lastLine.equals(newLines[1])) {
      System.out.printf("Old last line doesn't match new first line (%s)\n", fund.ticker);
      System.out.printf("Old: %s\n", lastLine);
      System.out.printf("New: %s\n", newLines[1]);
      return saveFundEodData(fund, true);
    }

    // Add new lines to old lines (skip header and one line overlap).
    for (int i = 2; i < newLines.length; ++i) {
      lines.add(newLines[i]);
    }
    Files.write(file.toPath(), lines);
    return true;
  }

  public static boolean saveFundMetadata(TiingoFund fund) throws IOException
  {
    return saveFundMetadata(fund, 10 * TimeLib.MS_IN_DAY);
  }

  public static boolean saveFundMetadata(TiingoFund fund, long replaceAgeMs) throws IOException
  {
    if (!TiingoIO.metaPath.exists()) {
      TiingoIO.metaPath.mkdirs();
    }
    URL url = TiingoIO.buildMetaURL(fund.ticker);
    File file = TiingoIO.getMetadataFile(fund.ticker);
    if (!file.exists() || DataIO.shouldDownloadUpdate(file, replaceAgeMs)) {
      System.out.printf("%5s (meta)  [%s] -> [%s]\n", fund.ticker, fund.start, fund.end);
      return TiingoIO.httpGetToFile(url, file);
    }
    return true;
  }

  public static boolean updateData(String[] symbols) throws IOException
  {
    // TODO Implement data update.
    TiingoIO.loadTickers(TiingoIO.supportedTickersPath);
    for (String symbol : symbols) {
      TiingoFund fund = TiingoFund.get(symbol);
      if (!TiingoIO.saveFundMetadata(fund)) return false;
      if (!TiingoIO.saveFundEodData(fund, false)) return false;
    }
    return true;
  }

  public static boolean downloadLatestSupportedTickerCSV()
  {
    File zip = new File(tiingoPath, "supported_tickers.zip");
    try {
      if (!DataIO.shouldDownloadUpdate(zip, 24 * TimeLib.MS_IN_HOUR)) return true;
    } catch (IOException e) {
      return false;
    }

    System.out.print("Downloading supported tickers CSV... ");
    if (!DataIO.copyUrlToFile(supportedTickersUrl, zip)) {
      System.out.println("FAILED.");
      return false;
    } else {
      System.out.println("done.");
    }

    try {
      System.out.print("Unzipping supported tickers CSV... ");
      DataIO.unzipFile(zip, null);
      System.out.println("done.");
    } catch (IOException e) {
      System.out.println("FAILED.");
      return false;
    }

    File file = new File(tiingoPath, "supported_tickers.csv");
    if (!file.exists()) {
      System.err.println("Expected supported_tickers.csv file is missing!");
      return false;
    }

    if (supportedTickersPath.exists() && !supportedTickersPath.delete()) {
      System.err.println("Failed to delete old supported tickers CSV.");
      return false;
    }
    if (!file.renameTo(supportedTickersPath)) {
      System.err.println("Failed to rename new supported tickers CSV.");
      return false;
    }

    return true;
  }
}
