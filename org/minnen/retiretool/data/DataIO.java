package org.minnen.retiretool.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Calendar;

import org.minnen.retiretool.Library;

public class DataIO
{
  /**
   * Load data from CSV file of <data>,<value>.
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
      if (line.isEmpty() || line.startsWith("\"")) {
        continue;
      }
      String[] toks = line.trim().split("[,\\s]+");
      if (toks == null || toks.length != 2) {
        System.err.printf("Error parsing CSV data: [%s]\n", line);
        continue;
      }

      String[] dateFields = toks[0].split("-");
      try {
        int year = Integer.parseInt(dateFields[0]);
        int month = Integer.parseInt(dateFields[1]);
        double rate = Double.parseDouble(toks[1]);

        Calendar cal = Library.now();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        data.addData(rate, cal.getTimeInMillis());
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
          double div = Double.parseDouble(toks[2]) / 12;

          // cpi
          double cpi = Double.parseDouble(toks[4]);

          // GS10 rate
          double gs10 = Double.parseDouble(toks[6]);

          // CAPE
          double cape = Library.tryParse(toks[10], 0.0);

          Calendar cal = Library.now();
          cal.set(Calendar.YEAR, year);
          cal.set(Calendar.MONTH, month - 1);
          cal.set(Calendar.DAY_OF_MONTH, 1);
          cal.set(Calendar.HOUR_OF_DAY, 8);
          cal.set(Calendar.MINUTE, 0);
          cal.set(Calendar.SECOND, 0);
          cal.set(Calendar.MILLISECOND, 0);
          seq.addData(new FeatureVec(5, price, div, cpi, gs10, cape), cal.getTimeInMillis());

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
      if (toks[0].toLowerCase().startsWith("date")) {
        continue;
      }

      String[] dateFields = toks[0].split("-");
      try {
        int year = Integer.parseInt(dateFields[0]);
        int month = Integer.parseInt(dateFields[1]);
        int day = Integer.parseInt(dateFields[2]);
        double adjClose = Double.parseDouble(toks[6]);

        Calendar cal = Library.now();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        data.addData(adjClose, cal.getTimeInMillis());
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

  public static String buildYahooURL(String symbol)
  {
    return String.format("http://ichart.yahoo.com/table.csv?s=%s&a=0&b=1&c=1900&d=11&e=31&f=2050&g=d&ignore=.csv",
        symbol);
  }

  public static boolean downloadDailyDataFromYahoo(File dir, String... symbols)
  {
    try {
      for (String symbol : symbols) {
        System.out.printf("Download data: %s\n", symbol);
        String address = buildYahooURL(symbol);
        File file = new File(dir, symbol + ".csv");
        URL url = new URL(address);
        ReadableByteChannel rbc = Channels.newChannel(url.openStream());
        FileOutputStream fos = new FileOutputStream(file);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fos.close(); // TODO properly close resource even if there's an exception
      }
    } catch (Exception e) {
      System.err.printf("Failed to download yahoo data (%s)\n", e);
      return false;
    }
    return true;
  }
}
