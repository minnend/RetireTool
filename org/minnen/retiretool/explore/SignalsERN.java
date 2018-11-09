package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.data.quandl.QuandlSeries;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

/**
 * Visualize signals discussed in this macroeconomics article at ERN:
 * https://earlyretirementnow.com/2018/02/21/market-timing-and-risk-management-part-1-macroeconomics/
 * 
 * @author David Minnen
 *
 */
public class SignalsERN
{
  /** Align weekly unemployment data to monthly PMI data. */
  private static Sequence alignUnemployment(Sequence seqUnemployment, Sequence seqPmi)
  {
    Sequence seq = new Sequence(seqUnemployment.getName());
    LocalDate prev = null;
    int iStart = 0;
    for (FeatureVec x : seqPmi) {
      LocalDate dateX = TimeLib.ms2date(x.getTime());
      assert prev == null || dateX.getMonth() != prev.getMonth();
      // System.out.println("pmi: " + dateX);

      FeatureVec accum = null;
      int n = 0;
      while (iStart < seqUnemployment.length()) {
        FeatureVec y = seqUnemployment.get(iStart);
        // Data is dated for Saturday; check Thursday to ensure that most of week is in the PMI month.
        LocalDate dateY = TimeLib.ms2date(y.getTime()).minusDays(2);
        if (dateY.getMonth() == dateX.getMonth()) {
          // System.out.println("Include unemployment: " + dateY);
          ++iStart;
          if (accum == null) {
            accum = y.dup();
          } else {
            accum._add(y);
          }
          ++n;
        } else {
          // System.out.println("Skip unemployment: " + dateY);
          break;
        }
      }
      if (n < 3 || accum == null) {
        throw new RuntimeException(String.format("Not enough unemployment data for %s (%d)", dateX, n));
      }
      if (n > 1) accum._div(n);
      seq.addData(accum, x.getTime());
    }
    return seq;
  }

  private static Sequence alignDaily(Sequence daily, Sequence pmi)
  {
    Sequence seq = new Sequence(daily.getName());
    for (FeatureVec x : pmi) {
      LocalDate datePMI = TimeLib.ms2date(x.getTime());
      LocalDate endOfMonth = datePMI.with(TemporalAdjusters.lastDayOfMonth());
      int i = daily.getIndexAtOrBefore(TimeLib.toMs(endOfMonth));
      assert i >= 0;
      // System.out.printf("[%s] -> [%s] == [%s] %d\n", datePMI, endOfMonth, TimeLib.formatDate(daily.getTimeMS(i)), i);

      // Average over the last five days so long as they're in the same month.
      FeatureVec accum = daily.get(i);
      int n = 1;
      assert TimeLib.ms2date(accum.getTime()).getMonth() == datePMI.getMonth();
      for (int di = 1; di < 5 && i - di >= 0; ++di) {
        FeatureVec y = daily.get(i - di);
        if (TimeLib.ms2date(y.getTime()).getMonth() != datePMI.getMonth()) break;
        accum._add(y);
        ++n;
      }
      if (n > 1) accum._div(n);
      seq.addData(accum, x.getTime());
    }
    return seq;
  }

  private static void genScatterPlot(Sequence seqX, Sequence seqY) throws IOException
  {
    Sequence scatter = new Sequence();
    for (int i = 0; i < seqY.length(); ++i) {
      FeatureVec x = seqX.get(i);
      FeatureVec y = seqY.get(i);
      assert x.getTime() == y.getTime();
      scatter.addData(new FeatureVec(TimeLib.formatMonth(x.getTime()), 2, x.get(0), y.get(0)), x.getTime());
    }

    String[] dimNames = new String[] { seqX.getName(), seqY.getName() };
    File file = new File(DataIO.outputPath, String.format("%s vs %s.html", seqX.getName(), seqY.getName()));
    Chart.saveScatterPlot(file, seqX.getName() + " vs. " + seqY.getName(), 1200, 640, 3, dimNames, scatter);
  }

  public static void main(String[] args) throws IOException
  {
    // Load data sequences.
    String stockSymbol = "VFINX";
    Sequence treasury10 = FredSeries.fromName("10-year-treasury").data;
    Sequence treasury2 = FredSeries.fromName("2-year-treasury").data;
    Sequence initialClaims = FredSeries.fromName("unemployment initial claims").data;
    Sequence unemploymentRate = FredSeries.fromName("unemployment rate").data;
    Sequence pmi = QuandlSeries.fromName("ISM-PMI").data;
    Sequence stockFund = TiingoFund.fromSymbol(stockSymbol, true).data;

    System.out.printf("%30s: [%s] -> [%s]\n", treasury10.getName(), TimeLib.formatDate(treasury10.getStartMS()),
        TimeLib.formatDate(treasury10.getEndMS()));
    System.out.printf("%30s: [%s] -> [%s]\n", treasury2.getName(), TimeLib.formatDate(treasury2.getStartMS()),
        TimeLib.formatDate(treasury2.getEndMS()));
    System.out.printf("%30s: [%s] -> [%s]\n", initialClaims.getName(), TimeLib.formatDate(initialClaims.getStartMS()),
        TimeLib.formatDate(initialClaims.getEndMS()));
    System.out.printf("%30s: [%s] -> [%s]\n", unemploymentRate.getName(),
        TimeLib.formatDate(unemploymentRate.getStartMS()), TimeLib.formatDate(unemploymentRate.getEndMS()));
    System.out.printf("%30s: [%s] -> [%s]\n", pmi.getName(), TimeLib.formatDate(pmi.getStartMS()),
        TimeLib.formatDate(pmi.getEndMS()));
    System.out.printf("%30s: [%s] -> [%s]\n", stockFund.getName(), TimeLib.formatDate(stockFund.getStartMS()),
        TimeLib.formatDate(stockFund.getEndMS()));

    // Calculate treasury spread.
    long commonStart = TimeLib.calcCommonStart(treasury2, treasury10);
    long commonEnd = TimeLib.calcCommonEnd(treasury2, treasury10);
    treasury2 = treasury2.subseq(commonStart, commonEnd);
    treasury10 = treasury10.subseq(commonStart, commonEnd);
    Sequence treasurySpread = treasury10.sub(treasury2)._mul(10);
    treasurySpread.setName("2-10 Treasury Spread");
    System.out.printf("\n%s: [%s] -> [%s]\n", treasurySpread.getName(), TimeLib.formatDate(treasurySpread.getStartMS()),
        TimeLib.formatDate(treasurySpread.getEndMS()));

    // Clip sequences to common start/end time while respecting the monthly data in PMI.
    commonStart = TimeLib.calcCommonStart(treasurySpread, pmi, initialClaims, stockFund);
    commonEnd = TimeLib.calcCommonEnd(treasurySpread, pmi, initialClaims, stockFund);
    System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatDate(commonStart), TimeLib.formatDate(commonEnd));

    // ISM-PMI is monthly with date 1-<Month>-<Year>, which gives the PMI for <month> (data is only released at the
    // end of the month and back-dated). Make sure start date is the first of the month.
    LocalDate date = TimeLib.ms2date(commonStart);
    if (date.getDayOfMonth() != 1) {
      date = date.with(TemporalAdjusters.firstDayOfNextMonth());
    }
    commonStart = TimeLib.toMs(date);

    // For the end time, PMI goes through the end of the month even though it's dated as the 1st. Unemployment is weekly
    // and dated for Saturday (covering the previous week) so we should check the previous day (Friday) to see if it
    // falls in the right month. The stock and treasury data is daily.
    commonEnd = TimeLib.calcCommonEnd(treasurySpread, initialClaims, stockFund);
    int iPMI = pmi.length() - 1;
    while (iPMI >= 0) {
      LocalDate datePMI = TimeLib.ms2date(pmi.getTimeMS(iPMI));
      LocalDate endOfMonth = datePMI.with(TemporalAdjusters.lastDayOfMonth());
      long ms = TimeLib.toMs(endOfMonth);
      if (ms < commonEnd) {
        commonEnd = ms;
        break;
      }
      --iPMI;
    }
    System.out.printf(" Clean: [%s] -> [%s]\n", TimeLib.formatDate(commonStart), TimeLib.formatDate(commonEnd));
    treasurySpread = treasurySpread.subseq(commonStart, commonEnd);
    stockFund = stockFund.subseq(commonStart, commonEnd);
    initialClaims = initialClaims.subseq(commonStart, commonEnd);
    pmi = pmi.subseq(commonStart, commonEnd);

    System.out.printf("\n%20s: [%s] -> [%s]\n", pmi.getName(), TimeLib.formatDate(pmi.getStartMS()),
        TimeLib.formatDate(pmi.getEndMS()));
    System.out.printf("%20s: [%s] -> [%s]\n", treasurySpread.getName(), TimeLib.formatDate(treasurySpread.getStartMS()),
        TimeLib.formatDate(treasurySpread.getEndMS()));
    System.out.printf("%20s: [%s] -> [%s]\n", initialClaims.getName(), TimeLib.formatDate(initialClaims.getStartMS()),
        TimeLib.formatDate(initialClaims.getEndMS()));
    System.out.printf("%20s: [%s] -> [%s]\n", stockFund.getName(), TimeLib.formatDate(stockFund.getStartMS()),
        TimeLib.formatDate(stockFund.getEndMS()));

    // TODO may be better to align on a weekly basis and interpolate PMI data. Still awkward since monthly release
    // doesn't match week boundaries.

    // Average weekly unemployment data to align with monthly PMI.
    initialClaims = alignUnemployment(initialClaims, pmi);
    assert pmi.sameTimestamps(initialClaims);

    // Average last five days of treasury and stock data to align with monthly PMI.
    stockFund = alignDaily(stockFund, pmi);
    assert pmi.sameTimestamps(stockFund);

    treasurySpread = alignDaily(treasurySpread, pmi);
    assert pmi.sameTimestamps(treasurySpread);

    // Calculate future returns.
    Sequence[] returnsAfterMonths = new Sequence[13];
    for (int nMonths = 1; nMonths < returnsAfterMonths.length; ++nMonths) {
      Sequence returns = new Sequence(String.format("%d-month Future Return", nMonths));
      int iPrice = FinLib.AdjClose;
      for (int i = 0; i < stockFund.length() - nMonths; ++i) {
        FeatureVec x = stockFund.get(i);
        FeatureVec y = stockFund.get(i + nMonths);
        double r = FinLib.mul2ret(y.get(iPrice) / x.get(iPrice));
        returns.addData(r, x.getTime());
      }
      returnsAfterMonths[nMonths] = returns;
    }

    Sequence stockReturns = stockFund.extractDims(FinLib.AdjClose);
    stockReturns._div(stockReturns.getFirst(0));

    File file = new File(DataIO.outputPath, "ern-signals-vs-stock.html");
    Chart.saveLineChart(file, "Signals vs. Stock", 1200, 640, ChartScaling.LINEAR, ChartTiming.MONTHLY, pmi,
        treasurySpread, initialClaims.div(10000), stockReturns, returnsAfterMonths[6], returnsAfterMonths[3],
        returnsAfterMonths[12]);

    genScatterPlot(initialClaims, returnsAfterMonths[6]);
    genScatterPlot(pmi, returnsAfterMonths[6]);
    genScatterPlot(treasurySpread, returnsAfterMonths[6]);

    // // Normalize stock data for new start time and calculate durational returns.
    // int nMonths = 12 * 2;
    // Sequence stock = store.get(stockSymbol);
    // FinLib.normalizeReturns(stock);
    // Sequence durReturns = FinLib.calcReturnsForDays(stock, nMonths * 20, PriceModel.zeroModel, true);
    // durReturns.setName(String.format("%s (%s returns)", stockSymbol, TimeLib.formatDurationMonths(nMonths)));
    //
    // // Report: line chart showing spread and stock market growth.
    // String title = String.format("Treasury Spread: %s", spread.getName());
    // File file = new File(DataIO.outputPath, "treasury-spread.html");
    // Chart.saveLineChart(file, title, 1200, 640, false, false, spread, durReturns, stock);
  }
}
