package org.minnen.retiretool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;

public class DataIO
{
  /**
   * Load data from CSV file of <data>,<value>.
   * 
   * @param fname name of file to load
   * @return Sequence with data loaded from the given file.
   */
  public static Sequence loadDateValueCSV(String fname) throws IOException
  {
    File file = new File(fname);
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read CSV file (%s)", fname));
    }
    System.out.printf("Loading CSV data file: [%s]\n", fname);

    BufferedReader in = new BufferedReader(new FileReader(fname));

    Sequence data = new Sequence(file.getName());
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("\"")) {
        continue;
      }
      String[] toks = line.trim().split("[,\\s]+");
      if (toks == null || toks.length != 2) {
        System.err.printf("Error parseing CSV data: [%s]\n", line);
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
        data.addData(rate, cal.getTimeInMillis());
      } catch (NumberFormatException e) {
        System.err.printf("Error parseing CSV data: [%s]\n", line);
        continue;
      }
    }
    in.close();
    return data;
  }

  /**
   * Load data from CSV export of Shiller's SNP/CPI excel spreadsheet.
   * 
   * @param filename name of file to load
   * @return true on success
   * @throws IOException if there is a problem reading the file.
   */
  public static Sequence loadShillerData(String filename) throws IOException
  {
    File file = new File(filename);
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Shiller file (%s)", filename));
    }
    System.out.printf("Loading data file: [%s]\n", filename);
    Sequence seq = new Sequence("Shiller Financial Data");
    try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
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
}
