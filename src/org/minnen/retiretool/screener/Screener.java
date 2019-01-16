package org.minnen.retiretool.screener;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.minnen.retiretool.data.DataIO;

public class Screener
{

  public static void main(String[] args) throws IOException
  {
    List<StockInfo> stocks = DividendChampions.loadData();
    System.out.printf("Dividend Champions: %d\n", stocks.size());

    stocks = stocks.stream().filter(x -> x.nDivPaymentsPerYear == 4).collect(Collectors.toList());
    System.out.printf("Quarterly dividends: %d\n", stocks.size());

    int nMinYears = 14;
    stocks = stocks.stream().filter(x -> x.nYearsDivIncrease >= nMinYears).collect(Collectors.toList());
    System.out.printf("%d years dividend increase: %d\n", nMinYears, stocks.size());

    double minMarketCap = 300.0;
    stocks = stocks.stream().filter(x -> x.marketCap >= minMarketCap).collect(Collectors.toList());
    System.out.printf("Market Cap > %.0fM: %d\n", minMarketCap, stocks.size());

    double minYield = 2.5;
    stocks = stocks.stream().filter(x -> x.dividendYield >= minYield).collect(Collectors.toList());
    System.out.printf("Yield > %.2f%%: %d\n", minYield, stocks.size());

    // double maxPayout = 60.0;
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
      DataIO.loadSymbol(stock.symbol);
      System.out.printf("%-36s %-5s  %5.2f%%  %d\n", stock.name, stock.symbol, stock.dividendYield,
          stock.nYearsDivIncrease);
    }
  }
}
