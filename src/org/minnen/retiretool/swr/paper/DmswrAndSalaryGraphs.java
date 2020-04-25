package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.BengenMethod;
import org.minnen.retiretool.swr.MarwoodMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodTable;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class DmswrAndSalaryGraphs
{
  /**
   * Run a Marwood-Minnen SWR simulation and print results.
   * 
   * @param retirementYears duration of retirement in years
   * @param lookbackYears number of previous years to check for a better "virtual retirement" time
   * @pparam percentStock percent stock (vs. bonds) to hold (70 = 70%)
   */
  public static void createCharts(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    final int bengenSWR = BengenTable.getSWR(retirementYears, percentStock);
    final int lookbackMonths = lookbackYears * 12;
    final int iFirstWithHistory = lookbackMonths;
    final int iStartSim = iFirstWithHistory;

    Sequence seqMarwoodSWR = new Sequence(String.format("DMSWR (rd, rd+%d years)", retirementYears));
    Sequence seqBengenSalary = new Sequence(String.format("MinSWR (%d years)", retirementYears));
    Sequence seqCrystalSalary = new Sequence(String.format("CBSWR (%d years)", retirementYears));
    Sequence seqMarwoodSalary = new Sequence(String.format("DMSWR (rd, rd+%d years)", retirementYears));

    final double firstNestEgg = SwrLib.getNestEgg(iStartSim, lookbackYears, percentStock);
    System.out.printf("First nest egg: $%.2f in [%s] = $%.2f in today's dollars\n", firstNestEgg,
        TimeLib.formatMonth(SwrLib.time(iStartSim)), firstNestEgg * SwrLib.inflation(iStartSim, -1));

    final double lastNestEgg = SwrLib.getNestEgg(SwrLib.length() - 1, lookbackYears, percentStock);
    System.out.printf("Last nest egg: $%.2f in [%s]\n", lastNestEgg,
        TimeLib.formatMonth(SwrLib.time(SwrLib.length() - 1)));

    List<MonthlyInfo> infos = MarwoodMethod.findMarwoodSWR(retirementYears, lookbackYears, percentStock);
    assert infos.size() == (SwrLib.length() - iStartSim);

    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      final long now = SwrLib.time(iRetire);
      MonthlyInfo dmswr = infos.get(iRetire - iStartSim);
      // MarwoodEntry dmswr = MarwoodTable.get(SwrLib.time(iRetire), retirementYears, lookbackYears, percentStock);

      if (iRetire == iStartSim || iRetire == SwrLib.length() - 1) System.out.printf("%d\n", dmswr.swr);
      seqMarwoodSWR.addData(dmswr.swr / 100.0, now);
      seqMarwoodSalary.addData(dmswr.marwoodSalary, now);
      seqBengenSalary.addData(dmswr.bengenSalary, now);
      seqCrystalSalary.addData(dmswr.crystalSalary, now);
    }

    // The crystall ball SWR is the same as Bengen per retirement start date.
    Sequence seqCrystalSWR = BengenMethod.calcSwrAcrossTime(retirementYears, percentStock);
    seqCrystalSWR.setName(String.format("CBSWR (rd, rd+%d years)", retirementYears));
    seqCrystalSWR._div(100.0); // convert basis points to percentages (342 -> 3.42).
    int index = seqCrystalSWR.getIndexAtOrAfter(seqMarwoodSWR.getStartMS());
    seqCrystalSWR = seqCrystalSWR.subseq(index);

    final double bengenAsPercent = bengenSWR / 100.0;
    Sequence seqBengenSWR = new Sequence(String.format("MinSWR(%d years)", retirementYears));
    for (FeatureVec v : seqMarwoodSWR) {
      seqBengenSWR.addData(bengenAsPercent, v.getTime());
    }

    // Create DMSWR chart (withdrawal rates vs. CBSWR).
    String title = String.format("Safe Withdrawal Rates (%d years, %d%% stock / %d%% bonds)", retirementYears,
        percentStock, 100 - percentStock);
    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "dmswr.html"), title, "100%", "800px",
        ChartScaling.LINEAR, ChartTiming.MONTHLY, seqBengenSWR, seqCrystalSWR, seqMarwoodSWR);
    // config.addPlotLineY(new PlotLine(bengenSWR / 100.0, 3.0, "#272", "dash"));
    config.setColors(new String[] { "#272", "#7cb5ec", "#434348" });
    config.setLineWidth(3);
    config.setTitleConfig("margin: 0, y: 40, style: { fontSize: 40 }");
    config.setAxisLabelFontSize(32);
    config.setTickInterval(36, -1);
    config.setMinMaxY(3.0, 14.0);
    config.setAnimation(false);
    config.setTickFormatter("return this.value.split(' ')[1];", "return this.value + '%';");
    config.setAxisTitles("Retirement Date (rd)", null);
    config.setLegendConfig(
        "align: 'right', verticalAlign: 'top', x: 0, y: 60, layout: 'vertical', floating: true, itemStyle: {"
            + "fontSize: 32, }, backgroundColor: '#fff', borderWidth: 1, padding: 12, "
            + "itemMarginTop: 16, itemMarginBottom: -16, shadow: true, symbolWidth: 32,");
    config.setAnimation(false);
    Chart.saveChart(config);

    // Create DMSWR income chart vs. Bengen income.
    config = Chart.saveLineChart(
        new File(DataIO.getOutputPath(), String.format("marwood-salary-%d-%d.html", retirementYears, lookbackYears)),
        "Annualized Income (Nominal Dollars)", "100%", "800px", ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY,
        seqBengenSalary, seqMarwoodSalary);
    // config.setColors(new String[] { "#434348", "#272" });
    config.setLineWidth(5);
    config.setAxisLabelFontSize(28);
    config.setMinMaxY(32, 60000);
    config.setTitleConfig("margin: 0, y: 40, style: { fontSize: 36 }");
    config.setTickInterval(48, -1);
    config.setTickFormatter("return this.value.split(' ')[1];", "return '$' + this.value;");
    config.setLegendConfig(
        "align: 'left', verticalAlign: 'top', x: 160, y: 70, layout: 'vertical', floating: true, itemStyle: {"
            + "fontSize: 28, }, backgroundColor: '#fff', borderWidth: 1, padding: 12, "
            + "itemMarginTop: 16, itemMarginBottom: -16, shadow: true, symbolWidth: 32,");
    Chart.saveChart(config);
  }

  public static void main(String[] args) throws IOException
  {
    final int retirementYears = 30;
    final int lookbackYears = 20;
    final int percentStock = 75;

    SwrLib.setupWithDefaultFiles();
    System.out.printf("DMSWR entries: %d\n", MarwoodTable.marwoodMap.size());
    System.out.printf("DMSWR sequences: %d\n", MarwoodTable.marwoodSequences.size());

    createCharts(retirementYears, lookbackYears, percentStock);
  }
}
