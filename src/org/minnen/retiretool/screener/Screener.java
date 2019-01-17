package org.minnen.retiretool.screener;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.yahoo.YahooIO;
import org.minnen.retiretool.util.FinLib;

public class Screener
{

  public static void main(String[] args) throws IOException
  {
    List<StockInfo> stocks = DividendChampions.loadData();
    System.out.printf("Dividend Champions: %d\n", stocks.size());

    stocks = stocks.stream().filter(x -> x.nDivPaymentsPerYear == 4).collect(Collectors.toList());
    System.out.printf("Quarterly dividends: %d\n", stocks.size());

    int nMinYears = 1;
    stocks = stocks.stream().filter(x -> x.nYearsDivIncrease >= nMinYears).collect(Collectors.toList());
    System.out.printf("%d years dividend increase: %d\n", nMinYears, stocks.size());

    double minMarketCap = 300.0;
    stocks = stocks.stream().filter(x -> x.marketCap >= minMarketCap).collect(Collectors.toList());
    System.out.printf("Market Cap > %.0fM: %d\n", minMarketCap, stocks.size());

    double minYield = 2.0;
    stocks = stocks.stream().filter(x -> x.dividendYield >= minYield).collect(Collectors.toList());
    System.out.printf("Yield > %.2f%%: %d\n", minYield, stocks.size());

    // double maxPayout = 90.0;
    // stocks = stocks.stream().filter(x -> x.epsPayout <= maxPayout).collect(Collectors.toList());
    // System.out.printf("Payout < %.0f%%: %d\n", maxPayout, stocks.size());

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

    for (StockInfo stock : stocks) {
      Sequence prices = DataIO.loadSymbol(stock.symbol);
      Map<String, String> fundamentals = YahooIO.loadFundamentals(stock.symbol);
      String sFCF = fundamentals.get("Levered Free Cash Flow");
      double fcf = YahooIO.parseDouble(sFCF);
      if (fcf <= 0 || Double.isNaN(fcf)) {
        // System.out.printf("Negative FCF: %-8s %-5s %s\n", fcf, stock.symbol, stock.name);
        continue;
      }
      String sDiv = fundamentals.get("Forward Annual Dividend Rate");
      double div = YahooIO.parseDouble(sDiv);
      assert !Double.isNaN(div) : stock.symbol;
      // String yield = fundamentals.get("Forward Annual Dividend Yield");
      String sPayoutRatio = fundamentals.get("Payout Ratio");
      double payoutRatio = YahooIO.parseDouble(sPayoutRatio);
      String sEPS = fundamentals.get("Diluted EPS");
      double eps = YahooIO.parseDouble(sEPS);
      if (eps <= 0) continue;
      String shares = fundamentals.get("Shares Outstanding");

      double divCost = div * YahooIO.parseDouble(shares);
      double prEPS = div / eps;
      if (prEPS > 0.9) continue;
      double prFCF = divCost / YahooIO.parseDouble(sFCF);
      if (prFCF > 0.9) continue;
      // if (Math.min(prEPS, prFCF) > 0.9) continue;

      double price = prices.getLast(FinLib.Close);
      double yield = div / price * 100;

      System.out.printf(
          "%-5s %4.2f  Years=%2d  %6.2f  Div=%4.2f  EPS=%-5s  PR(EPS)=%-5.1f  PR(FCF)=%-5.1f (%5.1f)  %s\n",
          stock.symbol, yield, stock.nYearsDivIncrease, price, div, sEPS, prEPS * 100, prFCF * 100, payoutRatio,
          stock.name);
    }
  }
}
