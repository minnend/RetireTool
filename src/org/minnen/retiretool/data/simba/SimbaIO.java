package org.minnen.retiretool.data.simba;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.TimeLib;

public class SimbaIO
{
  public static final File latestFileNominal = new File(DataIO.getFinancePath(), "simba-2019b-nominal.csv");
  public static final File latestFileReal    = new File(DataIO.getFinancePath(), "simba-2019b-real.csv");

  public static Map<String, SimbaFund> loadSimbaData(Inflation inflation) throws IOException
  {
    File file = (inflation == Inflation.Real ? latestFileReal : latestFileNominal);
    return loadSimbaData(file);
  }

  public static Map<String, SimbaFund> loadSimbaData(File file) throws IOException
  {
    String[] symbols = null; // VTSMX, VIVAX, etc.
    String[] descriptions = null; // TSM, LCV, etc.
    Sequence[] returnSeqs = null;

    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Simba CSV file (%s)", file.getPath()));
    }
    System.out.printf("Loading Simba CSV data file: [%s]\n", file.getPath());
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      int nLines = 0;
      int nCols = 0;
      int prevYear = -1;
      while ((line = in.readLine()) != null) {
        // Remove line comment.
        int iComment = line.indexOf('#');
        if (iComment >= 0) line = line.substring(0, iComment);

        line = line.trim();
        if (line.isEmpty()) continue;
        ++nLines;
        String[] toks = line.trim().split(",");
        if (nCols == 0) {
          nCols = toks.length;
        } else if (toks.length != nCols) {
          throw new IOException(
              String.format("Inconsistent number of columns: %d vs. %d (line=%d)\n", toks.length, nCols, nLines));
        }
        // System.out.printf("%d: %d |%s\n", nLines, toks.length, line);

        if (nLines == 1) {
          assert toks[0].equals("Name") || toks[0].isEmpty();
          descriptions = new String[toks.length - 1];
          System.arraycopy(toks, 1, descriptions, 0, descriptions.length);
        } else if (nLines == 2) {
          assert toks[0].equals("Symbol") || toks[0].isEmpty();
          symbols = new String[toks.length - 1];
          System.arraycopy(toks, 1, symbols, 0, descriptions.length);
          returnSeqs = new Sequence[symbols.length];
          for (int i = 0; i < symbols.length; ++i) {
            returnSeqs[i] = new Sequence(descriptions[i]); // TODO use name or symbol?
          }
        } else {
          try {
            int year = Integer.parseInt(toks[0]);
            if (prevYear > 0 && year != prevYear + 1) {
              throw new IOException(
                  String.format("Non-consecutive year: %d after %d (line=%d)\n", year, prevYear, nLines));
            }
            for (int i = 1; i < toks.length; ++i) {
              if (toks[i].isEmpty()) continue;
              double r = Double.parseDouble(toks[i]);
              double m = FinLib.ret2mul(r); // easier to work with multipliers (1.4 = 40% growth)
              long ms = TimeLib.toMs(year, Month.JANUARY, 1);
              if (!returnSeqs[i - 1].isEmpty()) {
                long prevMs = returnSeqs[i - 1].getEndMS();
                LocalDate prevDate = TimeLib.ms2date(prevMs);
                assert prevDate.getYear() + 1 == year;
              }
              returnSeqs[i - 1].addData(m, ms);
            }
          } catch (NumberFormatException e) {
            System.err.printf("Error parsing CSV data: [%s]\n", line);
            break;
          }
        }
      }
    }

    Map<String, SimbaFund> name2fund = new TreeMap<>();
    for (int i = 0; i < symbols.length; ++i) {
      name2fund.put(descriptions[i], new SimbaFund(descriptions[i], symbols[i], returnSeqs[i]));
    }

    return name2fund;
  }

  public static void main(String[] args) throws IOException
  {
    Map<String, SimbaFund> data = loadSimbaData(Inflation.Real);
    System.out.printf("Asset Classes: %d\n", data.size());
    for (SimbaFund fund : data.values()) {
      if (fund.startYear <= 1871) {
        System.out.printf("%16s  %5s  [%d -> %s]\n", fund.name, fund.symbol, fund.startYear, fund.endYear);
      }
    }
  }
}
