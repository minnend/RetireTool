package org.minnen.retiretool.explore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.IndexRange;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

// NOBL - based on dividend aristocrats
// https://www.suredividend.com/wp-content/uploads/2016/07/NOBL-Index-Historical-Constituents.pdf

class AnnualPortfolio
{
  public final int         year;
  public final Set<String> symbols = new HashSet<String>();

  public AnnualPortfolio(int year)
  {
    this.year = year;
  }
}

public class HistoricalAristocrats
{
  public static final SequenceStore store = new SequenceStore();

  public static List<AnnualPortfolio> loadAnnualData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read data file (%s)", file.getPath()));
    }
    System.out.printf("Loading data file: [%s]\n", file.getPath());

    Pattern yearPattern = Pattern.compile("^(\\d{4})\\s*-\\s*(\\d+)");

    List<AnnualPortfolio> years = new ArrayList<>();
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      int year = 0;
      // int count = 0;
      AnnualPortfolio portfolio = null;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        String[] toks = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)"); // don't split inside quotes
        Matcher m = yearPattern.matcher(toks[0]);
        if (m.find()) {
          year = Integer.parseInt(m.group(1));
          // count = Integer.parseInt(m.group(2));
          portfolio = new AnnualPortfolio(year);
          years.add(portfolio);
        } else if (toks.length == 2 && !toks[1].isEmpty() && !toks[1].equalsIgnoreCase("ticker")) {
          String symbol = toks[1].replace(".", "-");
          if (portfolio.symbols.contains(symbol)) {
            System.out.printf("Duplicate: %s (%d)\n", symbol, year);
          } else {
            portfolio.symbols.add(symbol);
          }
        } else if (year > 0 && !toks[0].equalsIgnoreCase("company")) {
          System.out.printf("Error (%d): %s\n", year, line);
        }
      }
    }
    return years;
  }

  public static void main(String[] args) throws IOException
  {
    TiingoIO.loadTickers();
    File file = new File(DataIO.getFinancePath(), "historical-aristocrats.txt");
    List<AnnualPortfolio> years = loadAnnualData(file);
    System.out.printf("Years (%d): %d -> %d\n", years.size(), years.get(0).year, years.get(years.size() - 1).year);

    Sequence cumulativeReturns = new Sequence("Historical Aristocrats");
    cumulativeReturns.addData(10000.0, TimeLib.toMs(years.get(0).year - 1, 12, 31));
    for (AnnualPortfolio portfolio : years) {
      double principle = cumulativeReturns.getLast(0);
      System.out.printf("%d: Starting balance = $%s\n", portfolio.year, FinLib.dollarFormatter.format(principle));

      // Calculate return ratio for the current year for each symbol.
      long ms1 = TimeLib.toMs(LocalDateTime.of(portfolio.year, 1, 1, 0, 0));
      long ms2 = TimeLib.toMs(LocalDateTime.of(portfolio.year, 12, 31, 23, 59));
      // System.out.printf("%d: [%s] -> [%s]\n", portfolio.year, TimeLib.formatTime(ms1), TimeLib.formatTime(ms2));
      Map<Long, Integer> days = new TreeMap<>();
      Map<Long, Double> returnSum = new TreeMap<>();
      int nSymbols = 0;
      for (String symbol : portfolio.symbols) {
        TiingoFund fund = TiingoFund.get(symbol);
        if (fund == null) {
          // System.err.printf("Unavailable: %s\n", symbol);
          continue;
        }
        if (!fund.loadData()) {
          System.err.printf("Failed to load data for %s.", fund.ticker);
          System.exit(1);
        }
        Sequence seq = fund.data;
        IndexRange indices = seq.getIndices(ms1, ms2, EndpointBehavior.Inside);
        if (indices == null) {
          // System.out.printf("Skip: %s\n", fund);
          continue;
        }
        ++nSymbols;
        double v1 = seq.get(indices.first, FinLib.AdjOpen);
        for (int i = indices.first; i <= indices.second; ++i) {
          FeatureVec x = seq.get(i);
          int n = days.getOrDefault(x.getTime(), 0);
          days.put(x.getTime(), n + 1);

          double v2 = seq.get(i, FinLib.AdjClose);
          double r = returnSum.getOrDefault(x.getTime(), 0.0);
          returnSum.put(x.getTime(), r + v2 / v1);
        }
      }

      for (Map.Entry<Long, Integer> entry : days.entrySet()) {
        int n = entry.getValue();
        assert (n <= nSymbols);
        if (n < nSymbols) continue;
        long ms = entry.getKey();
        double r = returnSum.get(ms) / days.get(ms);
        cumulativeReturns.addData(principle * r, ms);
        // System.out.printf("%s: $%.2f\n", TimeLib.formatDate(ms), principle * r);
      }
    }
    System.out.printf("[%s]: $%s\n", TimeLib.formatDate(cumulativeReturns.getEndMS()),
        FinLib.dollarFormatter.format(cumulativeReturns.getLast(0)));
    FinLib.normalizeReturns(cumulativeReturns);

    file = new File(DataIO.getOutputPath(), "historical-aristocrats-returns.txt");
    DataIO.saveDateValueCSV(file, cumulativeReturns, 0);
  }
}
