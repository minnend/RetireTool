package org.minnen.retiretool.vanguard;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.predictor.daily.TimeCode;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;

public class VanguardReport
{
  public static final SequenceStore             store          = new SequenceStore();

  public static final VanguardFund.FundSet      fundSet        = VanguardFund.FundSet.All;
  public static final Slippage                  slippage       = Slippage.None;
  public static final String[]                  fundSymbols    = VanguardFund.getFundNames(fundSet);
  public static final String[]                  assetSymbols   = new String[fundSymbols.length + 1];
  public static final Map<String, VanguardFund> funds          = VanguardFund.getFundMap(fundSet);
  public static final int[]                     momentumMonths = new int[] { 36, 24, 18, 12, 9, 6, 5, 4, 3, 2, 1 };

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
    SummaryTools.fundSymbols = fundSymbols;
  }

  public static double calcMomentum(VanguardFund fund, int nMonths)
  {
    return calcMomentum(fund, nMonths, TimeLib.TIME_ERROR);
  }

  public static double calcMomentum(VanguardFund fund, int nMonths, long endTime)
  {
    Sequence seq = store.get(fund.symbol);

    if (endTime == TimeLib.TIME_ERROR) {
      endTime = seq.getEndMS();
    }
    LocalDate endDate = TimeLib.ms2date(endTime);

    int b = seq.getClosestIndex(endTime);
    int a = seq.getClosestIndex(TimeLib.toMs(endDate.minusMonths(1)));

    double now = seq.average(a, b, FinLib.AdjClose);

    a = seq.getClosestIndex(TimeLib.toMs(endDate.minusMonths(nMonths + 1)));
    b = seq.getClosestIndex(TimeLib.toMs(endDate.minusMonths(nMonths)));
    double before = seq.average(a, b, FinLib.AdjClose);

    double momentum = now / before;
    double r = FinLib.mul2ret(momentum);

    return r;
  }

  public static String getColorString(double r)
  {
    int red = 0;
    int green = 0;
    int blue = 0;
    double alpha = 1.0;

    final double maxr = 5.0;
    if (r >= 0.0) {
      green = 224;
      alpha = Math.min(1.0, r / maxr + 0.05);
    } else {
      red = 224;
      alpha = Math.min(1.0, -r / maxr + 0.05);
    }

    return String.format("rgba(%d, %d, %d, %.3f)", red, green, blue, alpha);
  }

  public static String getFundGraphUrl(String symbol)
  {
    return "http://stockcharts.com/h-sc/ui?s=" + symbol + "&p=D&yr=3&mn=0&dy=0&id=p53988847433";
  }

  public static void genReport(File file) throws IOException
  {
    final long endTime = TimeLib.getTime(); //TimeLib.toMs(2009, 5, 1);
    final String green = "1E2";
    final String red = "D12";
    final String sRowGap = "<td class=\"hgap\">&nbsp;</td>";

    try (Writer f = new Writer(file)) {
      f.write("<html><head>\n");
      f.write("<title>Vanguard Report</title>\n");
      f.write("<link rel=\"stylesheet\" href=\"vanguard-report.css\">\n");
      f.write("</head><body>\n");
      f.write("<div><b>End Date:</b> %s<br/><br/></div>\n", TimeLib.formatDate(endTime));
      f.write("<table>\n");
      f.write("<tr>\n");
      f.write("<th>Symbol</th>\n");
      f.write("<th>Description</th>\n");
      for (int iMom = 0; iMom < momentumMonths.length; ++iMom) {
        f.write("<th>%d</th>", momentumMonths[iMom]);
      }
      f.write("</tr>\n");

      for (int iSymbol = 0; iSymbol < fundSymbols.length; ++iSymbol) {
        VanguardFund fund = funds.get(fundSymbols[iSymbol]);

        f.write("<tr class=\"%s\">", iSymbol % 2 == 0 ? "evenRow" : "oddRow");
        f.write("<td><a href=\"%s\">%s</a></td>", getFundGraphUrl(fund.symbol), fund.symbol);
        f.write("<td class=\"desc\">%s</td>", fund.description.replace("&", "&amp;"));
        for (int iMom = 0; iMom < momentumMonths.length; ++iMom) {
          int nMonths = momentumMonths[iMom];
          double totalReturn = calcMomentum(fund, nMonths, endTime);
          double monthlyReturn = FinLib.mul2ret(Math.pow(FinLib.ret2mul(totalReturn), 1.0 / nMonths));
          f.write("<td style=\"background-color: %s\">%.2f</td>", getColorString(monthlyReturn), monthlyReturn);
        }
        f.write("</tr>\n");
      }
      f.write("</table>\n");
      f.write("</body></html>\n");
    }
  }

  public static void main(String[] args) throws IOException
  {
    File outputDir = new File("g:/web");
    File dataDir = new File("g:/research/finance/");
    assert dataDir.isDirectory();

    File yahooDir = new File(dataDir, "yahoo/");
    if (!yahooDir.exists()) yahooDir.mkdirs();

    // Make sure we have the latest data.
    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(yahooDir, symbol);
      DataIO.updateDailyDataFromYahoo(file, symbol, 8 * TimeLib.MS_IN_HOUR);
    }

    // Load data and trim to same time period.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(yahooDir, symbol);
      Sequence seq = DataIO.loadYahooData(file);
      seqs.add(seq);
    }
    seqs.sort(Sequence.getStartDateComparator());
    for (Sequence seq : seqs) {
      String symbol = seq.getName();
      System.out.printf("%5s [%s] -> [%s]  %s\n", symbol, TimeLib.formatDate2(seq.getStartMS()),
          TimeLib.formatDate2(seq.getEndMS()), funds.get(symbol).description);
    }

    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    // commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    // Extract common subsequence from each data sequence and add to the sequence store.
    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);
    }

    File file = new File(outputDir, "vanguard-report.html");
    genReport(file);
  }
}
