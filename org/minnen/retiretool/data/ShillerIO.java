package org.minnen.retiretool.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

/**
 * Data provided by Robert Shiller: http://www.econ.yale.edu/~shiller/data.htm
 * Unofficial info on interpreting the data: https://www.bogleheads.org/forum/viewtopic.php?t=137706
 * 
 * The APIs in the class work with a CSV file, which must be saved from the excel spreadsheet that Shiller provides.
 */
public class ShillerIO
{
  public enum Dividends {
    INCLUDE, EXCLUDE
  };

  /**
   * Load data from CSV export of Shiller's SNP/CPI excel spreadsheet.
   * 
   * @param file file to load
   * @return Sequence holding Shiller data
   * @throws IOException if there is a problem reading the file.
   */
  public static Sequence loadAll(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Shiller file (%s)", file.getPath()));
    }
    System.out.printf("Loading Shiller data: [%s]\n", file.getPath());
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

          // snp price -- average of closing prices for the month
          double price = Double.parseDouble(toks[1]);

          // snp dividend -- data is annual dollar value, we want monthly
          // note: dividend data is quarterly and linearly interpolated to get monthly data
          double div = Double.parseDouble(toks[2]) / 12.0;

          // cpi
          double cpi = Double.parseDouble(toks[4]);

          // GS10 rate
          double gs10 = Double.parseDouble(toks[6]);

          // CAPE
          double cape = Library.tryParse(toks[10], 0.0);

          long timeMS = TimeLib.toMs(year, month, 1);
          seq.addData(new FeatureVec(5, price, div, cpi, gs10, cape), timeMS);

          // System.out.printf("%d/%d: $%.2f $%.2f $%.2f\n", year,
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

  /** @return Sequence holding monthly S&P prices */
  public static Sequence loadSNP(File file, Dividends dividends) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Shiller file (%s)", file.getPath()));
    }
    System.out.printf("Loading Shiller S&P data: [%s]\n", file.getPath());
    Sequence seq = new Sequence("Shiller S&P Data");
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

          // snp price -- average of closing prices for the month
          double price = Double.parseDouble(toks[1]);

          if (dividends == Dividends.INCLUDE) {
            // snp dividend -- data is annual dollar value, we want monthly
            // note: dividend data is quarterly and linearly interpolated to get monthly data
            double div = Double.parseDouble(toks[2]) / 12.0;
            price += div;
          }

          long timeMS = TimeLib.toMs(year, month, 1);
          seq.addData(new FeatureVec(1, price), timeMS);
        } catch (NumberFormatException e) {
          // Something went wrong so skip this line.
          // System.err.println("Bad Line: " + line);
          continue;
        }

      }
      return seq;
    }
  }
}
