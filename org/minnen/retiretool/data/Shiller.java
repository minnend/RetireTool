package org.minnen.retiretool.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.FinLib.DividendMethod;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

/**
 * Data provided by Robert Shiller: http://www.econ.yale.edu/~shiller/data.htm Unofficial info on interpreting the data:
 * https://www.bogleheads.org/forum/viewtopic.php?t=137706
 * 
 * The APIs in the class work with a CSV file, which must be saved from the excel spreadsheet that Shiller provides.
 */
public class Shiller
{
  public static int                        PRICE = 0;
  public static int                        DIV   = 1;
  public static int                        CPI   = 2;
  public static int                        GS10  = 3;
  public static int                        CAPE  = 4;

  private static final Map<File, Sequence> cache = new HashMap<File, Sequence>();

  /**
   * Load data from CSV export of Shiller's SNP/CPI excel spreadsheet.
   * 
   * @param file file to load
   * @return Sequence holding Shiller data
   * @throws IOException if there is a problem reading the file.
   */
  public static Sequence loadAll(File file) throws IOException
  {
    Sequence seq = cache.get(file);
    if (seq != null) return seq;

    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Shiller file (%s)", file.getPath()));
    }
    System.out.printf("Loading Shiller data: [%s]\n", file.getPath());
    seq = new Sequence("Shiller Financial Data");
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        try {
          String[] toks = line.trim().split(",");
          if (toks == null || toks.length < 5) {
            continue; // want at least: date, p, d, e, cpi
          }

          // date - odd parsing because 2017.1 = October 2017.
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

          // System.out.printf("%d/%d: $%.2f $%.2f $%.2f\n", year, month, price, div, cpi);
        } catch (NumberFormatException nfe) {
          // System.err.println("Bad Line: " + line);
          if (seq.isEmpty()) continue;
          else break;
        }

      }

      cache.put(file, seq);
      return seq;
    }
  }

  /**
   * Load S&P 500 price data based on Shiller's monthly data.
   * 
   * Each price is a monthly average. If dividends are included, older prices are adjusted to make the returns accurate
   * if dividends were re-invested at the end of each month.
   * 
   * @return Sequence with one dimension holding monthly S&P prices
   */
  public static Sequence loadSNP(File file, DividendMethod divMethod) throws IOException
  {
    Sequence snp = Shiller.loadAll(file);
    return FinLib.calcSnpReturns(snp, 0, -1, divMethod);
  }

  public static void main(String[] args) throws IOException
  {
    Sequence snpNoDivs = Shiller.loadSNP(DataIO.shiller, DividendMethod.NO_REINVEST_MONTHLY);
    Sequence snpWithDivs = Shiller.loadSNP(DataIO.shiller, DividendMethod.MONTHLY);

    System.out.printf("  No divs (%d): [%s] -> [%s]\n", snpNoDivs.length(), TimeLib.formatDate(snpNoDivs.getStartMS()),
        TimeLib.formatDate(snpNoDivs.getEndMS()));
    System.out.printf("With divs (%d): [%s] -> [%s]\n", snpWithDivs.length(),
        TimeLib.formatDate(snpWithDivs.getStartMS()), TimeLib.formatDate(snpWithDivs.getEndMS()));

    double total = FinLib.getTotalReturn(snpNoDivs);
    double annual = FinLib.getAnnualReturn(total, snpNoDivs.getLengthMonths());
    System.out.printf("Total Returns (w/o divs): %9.2f => %.3f%%\n", total, annual);

    total = FinLib.getTotalReturn(snpWithDivs);
    annual = FinLib.getAnnualReturn(total, snpWithDivs.getLengthMonths());
    System.out.printf("  Total Returns (w/divs): %9.2f => %.3f%%\n", total, annual);
  }
}
