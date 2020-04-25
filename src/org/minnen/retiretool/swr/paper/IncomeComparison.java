package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.MarwoodMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.swr.data.MarwoodTable;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class IncomeComparison
{
  /**
   * Generate graph covering the full time range (starting in 1871 + `lookbackYears`).
   */
  public static void createLongIncomeGraph(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    final int lookbackMonths = lookbackYears * 12;
    final int iFirstWithHistory = lookbackMonths;
    final int iStartSim = iFirstWithHistory;

    Sequence seqBengenSalary = new Sequence(String.format("MinSWR (%d years)", retirementYears));
    Sequence seqCrystalSalary = new Sequence(String.format("CBSWR (%d years)", retirementYears));
    Sequence seqMarwoodSalary = new Sequence(String.format("DMSWR (rd, rd+%d years)", retirementYears));

    final double firstNestEgg = SwrLib.getNestEgg(iStartSim, lookbackYears, percentStock);
    System.out.printf("First nest egg: $%.2f in [%s] = $%.2f in today's dollars\n", firstNestEgg,
        TimeLib.formatMonth(SwrLib.time(iStartSim)), firstNestEgg * SwrLib.inflation(iStartSim, -1));

    final double lastNestEgg = SwrLib.getNestEgg(SwrLib.length() - 1, lookbackYears, percentStock);
    System.out.printf("Last nest egg: $%.2f in [%s]\n", lastNestEgg,
        TimeLib.formatMonth(SwrLib.time(SwrLib.length() - 1)));

    List<MonthlyInfo> infos = MarwoodMethod.findDMSWR(retirementYears, lookbackYears, percentStock);
    assert infos.size() == (SwrLib.length() - iStartSim);

    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      final long now = SwrLib.time(iRetire);
      MonthlyInfo dmswr = infos.get(iRetire - iStartSim);

      if (iRetire == iStartSim || iRetire == SwrLib.length() - 1) System.out.printf("%d\n", dmswr.swr);
      seqMarwoodSalary.addData(dmswr.marwoodSalary, now);
      seqBengenSalary.addData(dmswr.bengenSalary, now);
      seqCrystalSalary.addData(dmswr.crystalSalary, now);
    }

    // Create DMSWR income chart vs. Bengen income.
    ChartConfig config = Chart.saveLineChart(
        new File(DataIO.getOutputPath(),
            String.format("dmswr-income-long-%d-%d-%d.html", retirementYears, lookbackYears, percentStock)),
        "Annualized Income (Nominal Dollars)", "100%", "800px", ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY,
        seqBengenSalary, seqMarwoodSalary);
    // config.setColors(new String[] { "#434348", "#272" });
    config.setLineWidth(5);
    config.setAxisLabelFontSize(28);
    config.setTitleConfig("margin: 0, y: 40, style: { fontSize: 36 }");
    config.setTickInterval(48, -1);
    config.setTickFormatter("return this.value.split(' ')[1];", "return '$' + this.value;");
    config.setLegendConfig(
        "align: 'left', verticalAlign: 'top', x: 160, y: 70, layout: 'vertical', floating: true, itemStyle: {"
            + "fontSize: 28, }, backgroundColor: '#fff', borderWidth: 1, padding: 12, shadow: true, symbolWidth: 32,");
    Chart.saveChart(config);
  }

  public static void createShortIncomeGraph(int startYear, int graphDurationYears, int retirementYears,
      int lookbackYears, int percentStock, boolean includeLegend) throws IOException
  {
    final int lookbackMonths = lookbackYears * 12;
    final int iStartSim = SwrLib.indexForTime(TimeLib.toMs(startYear, Month.JANUARY, 1));
    assert iStartSim >= lookbackMonths; // else not enough lookback data

    final int iEndSim = SwrLib.indexForTime(TimeLib.toMs(startYear + graphDurationYears - 1, Month.DECEMBER, 1));
    assert iEndSim <= SwrLib.length();

    Sequence seqBengenSalary = new Sequence("MinSWR");
    Sequence seqMarwoodSalary = new Sequence("DMSWR");

    List<MonthlyInfo> infos = MarwoodMethod.findDMSWR(iStartSim, iEndSim, retirementYears, lookbackYears, percentStock);
    assert infos.size() == (iEndSim - iStartSim + 1);

    for (int iRetire = iStartSim; iRetire <= iEndSim; ++iRetire) {
      final long now = SwrLib.time(iRetire);
      MonthlyInfo dmswr = infos.get(iRetire - iStartSim);
      seqMarwoodSalary.addData(dmswr.marwoodSalary, now);
      seqBengenSalary.addData(dmswr.bengenSalary, now);
    }

    // Create DMSWR income chart vs. Bengen income.
    File file = new File(DataIO.getOutputPath(),
        String.format("dmswr-income-%d-%d-%d-%d.html", startYear, retirementYears, lookbackYears, percentStock));
    String title = null; // "Annualized Income (Real Dollars)";
    ChartConfig config = Chart.saveLineChart(file, title, "100%", "800px", ChartScaling.LINEAR, ChartTiming.MONTHLY,
        seqBengenSalary, seqMarwoodSalary);
    config.setLineWidth(5);
    config.setAxisLabelFontSize(28);
    config.setTitleConfig("margin: 0, y: 40, style: { fontSize: 32 }");
    config.setTickInterval(12, -1);
    // config.setMinorTickIntervalY(5000);
    // config.setMinMaxY(21000, 93000);
    config.setTickFormatter("return this.value.split(' ')[1];",
        "return '$' + Highcharts.numberFormat(this.value, 0, '.', ',');");
    if (includeLegend) {
      config.setLegendConfig(
          "align: 'left', verticalAlign: 'top', x: 160, y: 20, layout: 'vertical', floating: true, itemStyle: {"
              + "fontSize: 28, }, backgroundColor: '#fff', borderWidth: 1, padding: 12, shadow: true, symbolWidth: 32,");
    }
    Chart.saveChart(config);
  }

  public static void main(String[] args) throws IOException
  {
    final int retirementYears = 30;
    final int lookbackYears = 20;
    final int percentStock = 75;

    SwrLib.setupWithDefaultFiles(Inflation.Nominal);
    System.out.printf("DMSWR entries: %d\n", MarwoodTable.marwoodMap.size());
    System.out.printf("DMSWR sequences: %d\n", MarwoodTable.marwoodSequences.size());

    // TODO long graph uses different nest egg calculation than short graph -- need to change code in findDMSWR().
    // createLongIncomeGraph(retirementYears, lookbackYears, percentStock);

    createShortIncomeGraph(1929, 10, retirementYears, lookbackYears, percentStock, true);
    createShortIncomeGraph(1960, 10, retirementYears, lookbackYears, percentStock, false);
    createShortIncomeGraph(2000, 10, retirementYears, lookbackYears, percentStock, false);
  }
}
