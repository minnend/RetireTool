package org.minnen.retiretool.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.IOUtils;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

public class DataIO
{
  /**
   * Load data from CSV file of <date>,<value>.
   * 
   * @param file file to load
   * @return Sequence with data loaded from the given file.
   */
  public static Sequence loadDateValueCSV(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read CSV file (%s)", file.getPath()));
    }
    System.out.printf("Loading CSV data file: [%s]\n", file.getPath());

    BufferedReader in = new BufferedReader(new FileReader(file));

    Sequence data = new Sequence(file.getName());
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("\"") || line.toLowerCase().startsWith("date")) {
        continue;
      }
      String[] toks = line.trim().split("[,\\s]+");
      if (toks == null || toks.length != 2) {
        System.err.printf("Error parsing CSV data: [%s]\n", line);
        continue;
      }

      // Skip missing data.
      if (toks[1].equals(".") || toks[1].equals("ND")) {
        continue;
      }

      String[] dateFields = toks[0].split("-");
      try {
        int year = Integer.parseInt(dateFields[0]);
        int month = Integer.parseInt(dateFields[1]);
        double rate = Double.parseDouble(toks[1]);

        data.addData(rate, TimeLib.getTime(1, month, year));
      } catch (NumberFormatException e) {
        System.err.printf("Error parsing CSV data: [%s]\n", line);
        continue;
      }
    }
    in.close();
    return data;
  }

  /**
   * Load data from CSV export of Shiller's SNP/CPI excel spreadsheet.
   * 
   * @param file file to load
   * @return true on success
   * @throws IOException if there is a problem reading the file.
   */
  public static Sequence loadShillerData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Shiller file (%s)", file.getPath()));
    }
    System.out.printf("Loading data file: [%s]\n", file.getPath());
    Sequence seq = new Sequence("Shiller Financial Data");
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        try {
          String[] toks = line.trim().split(",");
          if (toks == null || toks.length < 5) {
            continue; // want at least: date, p, d, e, cpi
          }

          // date
          double date = Double.parseDouble(toks[0]);
          int year = (int) Math.floor(date);
          int month = (int) Math.round((date - year) * 100);

          // snp price
          double price = Double.parseDouble(toks[1]);

          // snp dividend -- data is annual yield, we want monthly
          double div = Double.parseDouble(toks[2]) / 12.0;

          // cpi
          double cpi = Double.parseDouble(toks[4]);

          // GS10 rate
          double gs10 = Double.parseDouble(toks[6]);

          // CAPE
          double cape = Library.tryParse(toks[10], 0.0);

          long timeMS = TimeLib.getTime(1, month, year);
          seq.addData(new FeatureVec(5, price, div, cpi, gs10, cape), timeMS);

          // System.out.printf("%d/%d:  $%.2f  $%.2f  $%.2f\n", year,
          // month, price, div, cpi);

        } catch (NumberFormatException nfe) {
          // something went wrong so skip this line
          System.err.println("Bad Line: " + line);
          continue;
        }

      }
      return seq;
    }
  }

  /**
   * Load data from a Yahoo CSV file.
   * 
   * data,open,high,low,close,volume,adj close
   * 
   * @param file file to load
   * @return Sequence with data loaded from the given file.
   */
  public static Sequence loadYahooData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Yahoo CSV file (%s)", file.getPath()));
    }
    System.out.printf("Loading Yahoo data file: [%s]\n", file.getPath());

    int iDate = -1;
    int iOpen = -1;
    int iClose = -1;
    int iLow = -1;
    int iHigh = -1;
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
          else if (field.equals("adjclose")) iAdjClose = i;
        }
        continue;
      }

      try {
        long time = parseDate(toks[iDate]);
        double open = Double.parseDouble(toks[iOpen]);
        double high = Double.parseDouble(toks[iHigh]);
        double low = Double.parseDouble(toks[iLow]);
        double close = Double.parseDouble(toks[iClose]);
        double adjClose = Double.parseDouble(toks[iAdjClose]);
        FeatureVec fv = new FeatureVec(5);
        fv.set(FinLib.Open, open);
        fv.set(FinLib.High, high);
        fv.set(FinLib.Low, low);
        fv.set(FinLib.Close, close);
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

  /**
   * Load data from a CSV file.
   * 
   * @param file file to load
   * @param dims dimensions (columns) to load, not counting the data in column 0 (zero-based indices)
   * @return Sequence with data loaded from the given file.
   */
  public static Sequence loadCSV(File file, int[] dims) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read CSV file (%s)", file.getPath()));
    }
    System.out.printf("Loading CSV file: [%s]\n", file.getPath());

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
      for (int i = 0; i < toks.length; ++i) {
        toks[i] = toks[i].trim();
      }
      if (toks[0].toLowerCase().startsWith("date")) {
        continue;
      }

      try {
        long time = parseDate(toks[0]);
        FeatureVec v = new FeatureVec(dims.length);
        for (int d = 0; d < dims.length; ++d) {
          v.set(d, Double.parseDouble(toks[dims[d]]));
        }
        data.addData(v, time);
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

  public static URL buildYahooURL(String symbol)
  {
    try {
      String address = String.format(
          "http://ichart.yahoo.com/table.csv?s=%s&a=0&b=1&c=1900&d=11&e=31&f=2050&g=d&ignore=.csv", symbol);
      return new URL(address);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static String downloadDailyDataFromYahoo(String symbol)
  {
    System.out.printf("Download data: %s\n", symbol);
    try {
      URL url = buildYahooURL(symbol);
      return IOUtils.toString(url);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static File downloadDailyDataFromYahoo(File path, String symbol, long replaceAge)
  {
    try {
      if (path.isDirectory()) {
        path = new File(path, symbol + ".csv");
      }
      if (path.exists()) {
        if (!path.isFile() || !path.canWrite()) {
          System.err.printf("Path is not a writeable file (%s).\n", path.getPath());
          return null;
        }
        if (replaceAge > 0L) {
          long age = TimeLib.getTime() - path.lastModified();
          if (age < replaceAge) {
            System.out.printf("Recent file already exists (%s @ %s).\n", path.getName(), TimeLib.formatDuration(age));
            return path;
          }
        }
      }
      System.out.printf("Download data: %s\n", symbol);
      URL url = buildYahooURL(symbol);
      try (InputStream input = url.openStream()) {
        Files.copy(input, path.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      return path;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private static long parseDate(String date) throws NumberFormatException
  {
    String[] dateFields = date.split("-");

    int year = Integer.parseInt(dateFields[0]);
    int month = Integer.parseInt(dateFields[1]);
    int day = Integer.parseInt(dateFields[2]);

    return TimeLib.getTime(day, month, year);
  }
}
