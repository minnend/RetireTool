package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class TreasurySpread
{
  public static void main(String[] args) throws IOException
  {
    // Load data sequences.
    int y1 = 10;
    int y2 = 2;
    String stockSymbol = "VFINX";
    int nMonths = 12 * 2;
    FredSeries t1 = FredSeries.fromName(String.format("%d-year-treasury", y1));
    FredSeries t2 = FredSeries.fromName(String.format("%d-year-treasury", y2));
    TiingoFund stockFund = TiingoFund.fromSymbol(stockSymbol, true);
    System.out.printf("%s: [%s] -> [%s]\n", t1.name, TimeLib.formatDate(t1.data.getStartMS()),
        TimeLib.formatDate(t1.data.getEndMS()));
    System.out.printf("%s: [%s] -> [%s]\n", t2.name, TimeLib.formatDate(t2.data.getStartMS()),
        TimeLib.formatDate(t2.data.getEndMS()));
    System.out.printf("%s: [%s] -> [%s]\n", stockFund.ticker, stockFund.start, stockFund.end);

    // Set up sequence store and clip sequences to common time range.
    SequenceStore store = new SequenceStore();
    store.addAll(t1.data, t2.data, stockFund.data);
    store.clipToCommonTimeRange();
    System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatDate(store.getCommonStartTime()),
        TimeLib.formatDate(store.getCommonEndTime()));

    // Normalize stock data for new start time and calculate durational returns.
    Sequence stock = store.get(stockSymbol);
    FinLib.normalizeReturns(stock);
    Sequence durReturns = FinLib.calcReturnsForDays(stock, nMonths * 20, PriceModel.zeroModel, true);
    durReturns.setName(String.format("%s (%s returns)", stockSymbol, TimeLib.formatDurationMonths(nMonths)));

    // Calculate spread.
    Sequence seq1 = store.get(t1.name);
    Sequence seq2 = store.get(t2.name);
    Sequence spread = seq1.sub(seq2)._mul(10);
    spread.setName(String.format("%d/%d treasury spread (10x)", y1, y2));

    // Report: line chart showing spread and stock market growth.
    String title = String.format("Treasury Spread: %s", spread.getName());
    File file = new File(DataIO.outputPath, "treasury-spread.html");
    Chart.saveLineChart(file, title, 1200, 640, false, true, spread, durReturns, stock);
  }
}
