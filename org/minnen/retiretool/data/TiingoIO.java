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
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.minnen.retiretool.tiingo.Tiingo;
import org.minnen.retiretool.tiingo.TiingoFund;
import org.minnen.retiretool.tiingo.TiingoMetadata;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class TiingoIO
{
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
          symbol, Tiingo.auth, startDateString, endDateString);
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
   * Load metadata from Tiingo supported tickers file.
   * 
   * @param file file to load
   * @return List of Tiingo funds
   * @throws IOException if there is a problem reading the file.
   */
  public static List<TiingoFund> loadTickers(File file) throws IOException
  {
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
    return funds;
  }

  public static TiingoMetadata loadMetadata(String symbol)
  {
    File file = new File(Tiingo.metaPath, symbol + "-meta.json");
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
    File file = new File(Tiingo.eodPath, symbol + "-eod.csv");
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
      String[] toks = line.trim().split(",");
      if (toks == null || toks.length != 13) {
        System.err.printf("Error parsing Tiingo data: [%s]\n", line);
        continue;
      }
      for (int i = 0; i < toks.length; ++i) {
        toks[i] = toks[i].trim();
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

  public static String downloadDailyData(String symbol)
  {
    System.out.printf("Download data: %s\n", symbol);
    try {
      URL url = buildDataURL(symbol);
      return IOUtils.toString(url);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static boolean httpGetToFile(URL url, File file) throws IOException
  {
    // https://api.tiingo.com/docs/tiingo/daily
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    con.setRequestProperty("Content-Type", "application/json");
    con.setRequestProperty("Authorization", "Token " + Tiingo.auth);
    if (con.getResponseCode() != 200) {
      System.out.printf("Response: %d - %s\n", con.getResponseCode(), con.getResponseMessage());
      return false;
    }

    String data;
    try (InputStream input = con.getInputStream()) {
      // Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      data = IOUtils.toString(input);
      if (data.startsWith("Error")) {
        System.out.println(data);
        return false;
      }
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write(data);
      return true;
    }
  }

  public static boolean saveFundEodData(TiingoFund fund, boolean replaceExisting) throws IOException
  {
    if (!Tiingo.eodPath.exists()) {
      Tiingo.eodPath.mkdirs();
    }
    URL url = TiingoIO.buildDataURL(fund.ticker);
    File file = new File(Tiingo.eodPath, fund.ticker + "-eod.csv");
    if (replaceExisting || !file.exists()) {
      System.out.printf("%s  [%s] -> [%s]\n", fund.ticker, fund.start, fund.end);
      return TiingoIO.httpGetToFile(url, file);
    }
    return true;
  }

  public static boolean saveFundMetadata(TiingoFund fund, boolean replaceExisting) throws IOException
  {
    if (!Tiingo.metaPath.exists()) {
      Tiingo.metaPath.mkdirs();
    }
    URL url = TiingoIO.buildMetaURL(fund.ticker);
    File file = new File(Tiingo.metaPath, fund.ticker + "-meta.json");
    if (replaceExisting || !file.exists()) {
      System.out.printf("%s  [%s] -> [%s]\n", fund.ticker, fund.start, fund.end);
      return TiingoIO.httpGetToFile(url, file);
    }
    return true;
  }
}
