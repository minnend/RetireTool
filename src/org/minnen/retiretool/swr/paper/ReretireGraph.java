package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.MarwoodMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodTable;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.PlotLine;

public class ReretireGraph
{
  /** Simulate re-retiring to boost withdrawals after the original retirement date. */
  public static void createReRetireCharts(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    List<Sequence> trajectories = new ArrayList<>();
    final int lookbackMonths = lookbackYears * 12;
    for (int iMarwood = lookbackMonths; iMarwood < SwrLib.lastIndex(retirementYears); ++iMarwood) {
      final long retireTime = SwrLib.time(iMarwood);

      // Don't plot every starting time.
      LocalDate date = TimeLib.ms2date(retireTime);
      if (date.getYear() % 10 != 0 || date.getMonth() != Month.JANUARY) continue;

      List<MonthlyInfo> trajectory = MarwoodMethod.reretire(retireTime, retirementYears, lookbackYears, percentStock,
          Inflation.Real, 1e6);

      Sequence seq = new Sequence(TimeLib.ms2date(trajectory.get(0).retireTime).format(TimeLib.dtfY));
      for (MonthlyInfo info : trajectory) {
        // seq.addData(info.swr / 100.0, info.currentTime);
        seq.addData(info.marwoodSalary, info.currentTime);
      }
      trajectories.add(seq);
    }

    String filename = String.format("reretire-%d-%d-%d.html", retirementYears, lookbackYears, percentStock);
    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), filename),
        "DMSWR Income Trajectories with Re-Retiring", "100%", "800px", ChartScaling.LINEAR, ChartTiming.INDEX,
        trajectories);

    final double bengenSWR = BengenTable.getSWR(retirementYears, percentStock) / 100.0;
    final double bengenSalary = bengenSWR / 100.0 * 1e6;
    config.addPlotLineY(new PlotLine(bengenSalary, 5.0, "#777", "dash"));

    String[] labels = new String[trajectories.get(0).size()];
    for (int i = 0; i < labels.length; ++i) {
      final long ms = trajectories.get(0).getTimeMS(i) - trajectories.get(0).getStartMS();
      if (ms == 0) {
        labels[i] = "";
      } else {
        labels[i] = TimeLib.formatDuration(ms).replace(".0", "");
      }
    }

    config.setLabels(labels);
    config.setAxisLabelFontSize(28);
    config.setLineWidth(5);
    config.setTickInterval(12, -1);
    // config.setMinMaxY(3.0, 16.0);
    config.setMinMaxY(30000, 160000);
    // config.setTickFormatter(null, "return this.value + '%';");
    config.setTickFormatter(null, "return '$' + this.value/1000 + 'k';");
    config.setMinorTickIntervalY(1.0);
    config.setTitleConfig("margin: 0, y: 45, style: { fontSize: 40 }");
    config.setLegendConfig(
        "align: 'center', verticalAlign: 'bottom', x: 0, y: 0, layout: 'horizontal', floating: false, itemStyle: {"
            + "fontSize: 28, }, backgroundColor: '#fff', borderWidth: 0, padding: 16, shadow: false, symbolWidth: 32,");
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

    MarwoodTable.genReRetireTable(retirementYears, lookbackYears, percentStock);
    createReRetireCharts(retirementYears, lookbackYears, percentStock);
  }
}
