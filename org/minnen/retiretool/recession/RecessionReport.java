package org.minnen.retiretool.recession;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.PlotLine;

/**
 * Generate a report with multiple indicators that could be used to judge recession risk.
 */
public class RecessionReport
{
  public static void main(String[] args) throws IOException
  {
    File file;
    ChartConfig config;

    // Predict forward 10-year stock return.
    Predict10YearReturn.calculate();
    file = new File(DataIO.outputPath, "10-year-return-prediction.html");
    config = Chart.saveLineChart(file, "10-Year Return Prediction", 1000, 600, ChartScaling.LINEAR, ChartTiming.MONTHLY,
        Predict10YearReturn.equityAllocation, Predict10YearReturn.trueForwardReturns,
        Predict10YearReturn.predictedForwardReturns);
    config.addPlotLineY(new PlotLine(0.0, 2.0, "black"));
    Chart.saveChart(config);

    // 10-2 treasury yield spread.
    YieldSpread.calculate();
    file = new File(DataIO.outputPath, "treasury-10x2-yield-spread.html");
    config = Chart.saveLineChart(file, "10-2 Treasury Yield Spread", 1000, 600, ChartScaling.LINEAR,
        ChartTiming.MONTHLY, YieldSpread.spread);
    config.addPlotLineY(new PlotLine(0.0, 2.0, "black"));
    Chart.saveChart(config);

    // Unemployment rate.
    UnemploymentRate.calculate();
    Chart.saveLineChart(new File(DataIO.outputPath, "unrate.html"), "Unemployment Rate", 1000, 640, ChartScaling.LINEAR,
        ChartTiming.MONTHLY, UnemploymentRate.unrate, UnemploymentRate.unrateSMA);
  }
}
