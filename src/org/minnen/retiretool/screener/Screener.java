package org.minnen.retiretool.screener;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.sharesoutstandinghistory.SharesOutstandingIO;
import org.minnen.retiretool.data.stockpup.StockPupIO;
import org.minnen.retiretool.data.yahoo.YahooIO;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Chart;

public class Screener
{
  private static Set<String> blacklist;
  static {
    blacklist = new HashSet<String>();
    blacklist.add("FFG"); // TODO figure out why tiingo data is bad for these symbols
    blacklist.add("GBNK");
  }

  private static String genScreenerTable(List<StockInfo> stocks) throws IOException
  {
    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {
      writer.write("<table id=\"screenerTable\" class=\"tablesorter\" cellpadding=\"0\">\n");
      writer.write("<thead><tr>\n");
      String[] header = new String[] { "Symbol", "Price", "Dividend<br/>Yield", "Buyback<br/>Yield",
          "Net Payout<br/>Yield", "Dividend<br/>(Annual)", "Years Div<br/>Increased", "Payout Ratio<br/>(EPS)",
          "Payout Ratio<br/>(LFCF)", "Sector", "Company Name" };
      for (String s : header) {
        writer.writef(" <th>%s</th>\n", s);
      }
      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");

      int iRow = 0;
      for (StockInfo stock : stocks) {
        writer.writef("<tr class=\"%s\">\n", iRow % 2 == 0 ? "evenRow" : "oddRow");
        writer.writef("<td>%s</td>\n", stock.symbol);
        writer.writef("<td>%.2f</td>\n", stock.metrics.get("price"));
        writer.writef("<td>%.2f</td>\n", stock.metrics.get("dividend yield"));
        writer.writef("<td>%.2f</td>\n", stock.metrics.get("buyback yield"));
        writer.writef("<td>%.2f</td>\n", stock.metrics.get("net payout yield"));
        writer.writef("<td>%.2f</td>\n", stock.getFundamental("Forward Annual Dividend Rate"));
        writer.writef("<td>%d</td>\n", stock.nYearsDivIncrease);
        writer.writef("<td>%.2f</td>\n", stock.metrics.get("payout ratio (EPS)") * 100);
        writer.writef("<td>%.2f</td>\n", stock.metrics.get("payout ratio (LFCF)") * 100);
        writer.writef("<td>%s</td>\n", stock.sector);
        writer.writef("<td><a href=\"https://stockrow.com/%s\">%s</a></td>\n", stock.symbol, stock.name);
        writer.write("</tr>\n");
        ++iRow;
      }
      writer.write("</tbody>\n</table>\n");
    }
    return sw.toString();
  }

  private static void buildReport(File file, List<StockInfo> stocks) throws IOException
  {
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<title>Screener</title>\n");
      writer.writef("<script src=\"%s\"></script>\n", Chart.jquery);
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#screenerTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer.write("<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\"/>\n");
      writer.write("</head><body>\n");
      writer.write(genScreenerTable(stocks));
      writer.write("</body></html>\n");
    }
  }

  public static void main(String[] args) throws IOException
  {
    List<StockInfo> stocks = DividendChampions.loadData();
    System.out.printf("Dividend Champions: %d\n", stocks.size());

    stocks = stocks.stream().filter(x -> x.nDivPaymentsPerYear == 4).collect(Collectors.toList());
    System.out.printf("Quarterly dividends: %d\n", stocks.size());

    int nMinYears = 0;
    stocks = stocks.stream().filter(x -> x.nYearsDivIncrease >= nMinYears).collect(Collectors.toList());
    System.out.printf("%d years dividend increase: %d\n", nMinYears, stocks.size());

    double minMarketCap = 300.0;
    stocks = stocks.stream().filter(x -> x.marketCap >= minMarketCap).collect(Collectors.toList());
    System.out.printf("Market Cap > %.0fM: %d\n", minMarketCap, stocks.size());

    double minYield = 0.0;
    stocks = stocks.stream().filter(x -> x.dividendYield >= minYield).collect(Collectors.toList());
    System.out.printf("Yield > %.2f%%: %d\n", minYield, stocks.size());

    // double maxPayout = 90.0;
    // stocks = stocks.stream().filter(x -> x.epsPayout <= maxPayout).collect(Collectors.toList());
    // System.out.printf("Payout < %.0f%%: %d\n", maxPayout, stocks.size());

    stocks = stocks.stream().filter(x -> !blacklist.contains(x.symbol)).collect(Collectors.toList());

    stocks.sort(new Comparator<StockInfo>()
    {
      @Override
      public int compare(StockInfo a, StockInfo b)
      {
        if (a.dividendYield > b.dividendYield) return -1;
        if (a.dividendYield < b.dividendYield) return 1;
        return 0;
      }
    });

    // Load price data.
    System.out.println("Loading price data from Tiingo...");
    for (StockInfo stock : stocks) {
      Sequence prices = DataIO.loadSymbol(stock.symbol);
      double nMonths = TimeLib.monthsBetween(prices.getStartMS(), prices.getEndMS());
      if (nMonths < 48) {
        // System.out.printf("Not enough historical data [%s]: %.1f months %s\n", stock.symbol, nMonths,
        // prices.getDateRangeString());
        continue;
      }
      stock.prices = prices;
    }
    stocks = stocks.stream().filter(x -> x.prices != null).collect(Collectors.toList());
    System.out.printf("Historical price data: %d\n", stocks.size());

    // Load StockPup data and calculate derived values.
    // for (int i = 0; i < stocks.size(); ++i) {
    // StockInfo stock = stocks.get(i);
    // Sequence stockPup = StockPupIO.loadFundamentals(stock.symbol, TimeLib.TIME_END);
    // if (stockPup == null) {
    // // TODO if no stockpup data, assume buyback is zero? Look elsewhere?
    // // System.out.printf("No StockPup data: %s\n", stock.symbol);
    // stocks.set(i, null);
    // continue;
    // }
    //
    // int index = stockPup.getDim("Shares");
    // double[] shares = stockPup.extractDim(index);
    // stock.metrics.put("shares", shares[shares.length - 1]);
    //
    // index = stockPup.getDim("Shares split adjusted");
    // shares = stockPup.extractDim(index);
    // double nShares = shares[shares.length - 1]; // most recent data is at the end
    // double nPrevShares = shares[shares.length - 5];
    // stock.metrics.put("share diff (annual)", nShares - nPrevShares);
    // stock.metrics.put("buyback yield", FinLib.mul2ret(nPrevShares / nShares));
    // }
    // stocks = stocks.stream().filter(x -> x != null).collect(Collectors.toList());
    // System.out.printf("StockPup data: %d\n", stocks.size());

    // TODO if shares outstanding data is missing, use stockpup.
    System.out.println("Loading shares outstanding data...");
    for (int i = 0; i < stocks.size(); ++i) {
      StockInfo stock = stocks.get(i);
      Sequence sharesOut = SharesOutstandingIO.loadData(stock.symbol, TimeLib.TIME_END);
      if (sharesOut == null) {
        // System.out.printf("No shares outstanding data: %s\n", stock.symbol);
        stocks.set(i, null);
        continue;
      }
      if (sharesOut.length() < 5) {
        // System.out.printf("Too litte data: %s\n", stock.symbol);
        stocks.set(i, null);
        continue;
      }
      if (TimeLib.monthsBetween(sharesOut.getEndMS(), TimeLib.getTime()) > 5) {
        // System.out.printf("Data too old: %s [%s]\n", stock.symbol, TimeLib.formatDate(sharesOut.getEndMS()));
        stocks.set(i, null);
        continue;
      }

      stock.sharesOut = sharesOut;
      stock.metrics.put("shares", sharesOut.getLast(0));
      double nShares = sharesOut.get(-1, 0);
      double nPrevShares = sharesOut.get(-5, 0);
      stock.metrics.put("share diff (annual)", nShares - nPrevShares);
      stock.metrics.put("buyback yield", FinLib.mul2ret(nPrevShares / nShares));
    }
    stocks = stocks.stream().filter(x -> x != null).collect(Collectors.toList());
    System.out.printf("Shares outstanding data: %d\n", stocks.size());

    for (int i = 0; i < stocks.size(); ++i) {
      StockInfo stock = stocks.get(i);
      Map<String, String> fundamentals = YahooIO.loadFundamentals(stock.symbol, 5 * TimeLib.MS_IN_DAY);
      stock.fundamentals = fundamentals;

      double shares2 = stock.metrics.get("shares");
      // String shares = fundamentals.get("Shares Outstanding");
      // double shares1 = DataIO.parseDouble(shares);
      // double diff = Math.abs(shares1 - shares2);
      // double percent = 100 * diff / Math.min(shares1, shares2);
      // if (diff > 10000 && percent > 4.0) {
      // System.out.printf("Shares[%s]: %.0f vs. %.0f (diff=%.0f, pct=%.2f)\n", stock.symbol, shares1 / 1000,
      // shares2 / 1000, diff / 1000, percent);
      // continue;
      // }

      String sFCF = fundamentals.get("Levered Free Cash Flow");
      double fcf = DataIO.parseDouble(sFCF);
      if (fcf <= 0 || Double.isNaN(fcf)) {
        // System.out.printf("Negative FCF: %-8s %-5s %s\n", fcf, stock.symbol, stock.name);
        stocks.set(i, null);
        continue;
      }
      String sDiv = fundamentals.get("Forward Annual Dividend Rate");
      double divPerShare = DataIO.parseDouble(sDiv);
      assert !Double.isNaN(divPerShare) : stock.symbol;
      // String yield = fundamentals.get("Forward Annual Dividend Yield");
      String sPayoutRatio = fundamentals.get("Payout Ratio");
      double payoutRatio = DataIO.parseDouble(sPayoutRatio);
      String sEPS = fundamentals.get("Diluted EPS");
      double eps = DataIO.parseDouble(sEPS);
      if (eps <= 0) {
        stocks.set(i, null);
        continue;
      }

      // TODO payout ratio should include cost of buybacks?
      double totalDivPayment = divPerShare * shares2;
      double prEPS = divPerShare / eps;
      // if (prEPS > 0.9) continue;
      double prFCF = totalDivPayment / DataIO.parseDouble(sFCF);
      // if (prFCF > 0.9) continue;
      // if (Math.min(prEPS, prFCF) > 0.9) continue;

      double price = stock.prices.getLast(FinLib.Close);
      double divYield = divPerShare / price * 100;
      double buybackYield = stock.metrics.get("buyback yield");

      stock.metrics.put("price", price);
      stock.metrics.put("total dividend payment", totalDivPayment);
      stock.metrics.put("payout ratio (EPS)", prEPS);
      stock.metrics.put("payout ratio (LFCF)", prFCF);
      stock.metrics.put("dividend yield", divYield);
      stock.metrics.put("net payout yield", divYield + buybackYield);

      System.out.printf("%-5s %5.2f Years=%2d %6.2f Div=%5.2f EPS=%5.2f PR(EPS)=%-5.1f PR(FCF)=%-5.1f (%5.1f) %s\n",
          stock.symbol, divYield, stock.nYearsDivIncrease, price, divPerShare, eps, prEPS * 100, prFCF * 100,
          payoutRatio, stock.name);
    }

    stocks = stocks.stream().filter(x -> x != null).collect(Collectors.toList());
    System.out.printf("Final screen: %d\n", stocks.size());

    buildReport(new File(DataIO.getOutputPath(), "screener.html"), stocks);
  }
}
