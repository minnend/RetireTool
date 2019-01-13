package org.minnen.retiretool.viz;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.NotImplementedException;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.IndexRange;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;
import org.minnen.retiretool.stats.ComparisonStats.Results;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class Chart
{
  public final static String  jquery        = "https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js";
  public final static boolean bVerticalLine = false;

  public static ChartConfig saveLineChart(File file, String title, String width, String height, ChartScaling scaling,
      ChartTiming timing, Sequence... seqs) throws IOException
  {
    ChartConfig config = ChartConfig.buildLine(file, title, width, height, scaling, timing, seqs);
    saveChart(config);
    return config;
  }

  public static ChartConfig saveLineChart(File file, String title, String width, String height, ChartScaling scaling,
      ChartTiming timing, List<Sequence> seqs) throws IOException
  {
    ChartConfig config = ChartConfig.buildLine(file, title, width, height, scaling, timing, seqs);
    saveChart(config);
    return config;
  }

  public static ChartConfig saveChart(File file, ChartConfig.Type chartType, String title, String[] labels,
      String[] colors, String width, String height, double ymin, double ymax, double minorTickIntervalY,
      ChartScaling scaling, ChartTiming timing, int dim, Sequence... seqs) throws IOException
  {
    ChartConfig config = ChartConfig.build(file, chartType, title, labels, colors, width, height, ymin, ymax,
        minorTickIntervalY, scaling, timing, dim, seqs);
    saveChart(config);
    return config;
  }

  public static void saveDataCSV(ChartConfig config, Sequence... seqs) throws IOException
  {
    File file = new File(config.file.getParentFile(), config.file.getName() + ".csv");
    try (Writer writer = new Writer(file)) {
      writer.write("Strategy,");
      if (config.labels != null) {
        assert config.labels.length == seqs[0].size();
        writer.write("%s\n", String.join(",", config.labels));
      } else {
        for (int i = 0; i < seqs[0].size(); ++i) {
          if (config.timing == ChartTiming.MONTHLY) {
            writer.write(TimeLib.formatMonth(seqs[0].getTimeMS(i)));
          } else if (config.timing == ChartTiming.DAILY) {
            writer.write(TimeLib.formatDate(seqs[0].getTimeMS(i)));
          } else if (config.timing == ChartTiming.INDEX) {
            writer.write("%d", i);
          } else {
            throw new NotImplementedException("Unknown chart timing: " + config.timing);
          }

          if (i < seqs[0].size() - 1) {
            writer.write(",");
          }
        }
      }
      writer.writeln();

      for (Sequence seq : seqs) {
        writer.write("%s,", seq.getName());
        for (int t = 0; t < seq.length(); ++t) {
          double x = seq.get(t, config.iDim);
          writer.write("%.6f%s", x, t == seq.size() - 1 ? "" : ",");
        }
        writer.writeln();
      }
    }
  }

  /** Adds text for each plot band to the given writer. */
  private static void addPlotBands(List<PlotBand> bands, Writer writer) throws IOException
  {
    if (bands == null || bands.isEmpty()) return;
    writer.write("   plotBands: [\n");
    for (PlotBand band : bands) {
      writer.write("    { from: %g, to: %g, color: '%s' },\n", band.from, band.to, band.color);
    }
    writer.write("   ],\n");
  }

  /** Adds text for each plot line to the given writer. */
  private static void addPlotLines(List<PlotLine> lines, Writer writer) throws IOException
  {
    if (lines == null || lines.isEmpty()) return;
    writer.write("   plotLines: [\n");
    for (PlotLine line : lines) {
      writer.write("    { value: %g, width: %g, color: '%s', dashStyle: '%s', },\n", line.value, line.width, line.color,
          line.dashStyle);
    }
    writer.write("   ],\n");
  }

  public static void saveChart(ChartConfig config) throws IOException
  {
    Sequence[] seqs = config.data;
    // for (Sequence seq : seqs) {
    // System.out.printf("%20s (%d): [%s] -> [%s]\n", seq.getName(), seq.length(), TimeLib.formatDate(seq.getStartMS()),
    // TimeLib.formatDate(seq.getEndMS()));
    // }

    // saveDataCSV(config, seqs);
    try (Writer writer = new Writer(config.file)) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script src=\"%s/js/highcharts.js\"></script>\n",
          config.pathToBase == null ? "." : config.pathToBase);
      writer.write("<script type=\"text/javascript\">\n");

      writer.write("$(function () {\n");
      writer.write(" $('#%s').highcharts({\n", config.containerName);
      writer.write("  title: { text: '%s' },\n", config.title == null ? "" : config.title);
      if (bVerticalLine) {
        writer.write("  tooltip: {\n");
        writer.write("    crosshairs: {\n");
        writer.write("      color: 'green',\n");
        writer.write("      dashStyle: 'solid'\n");
        writer.write("    },\n");
        writer.write("    shared: true,\n");
        writer.write("    enabled: false\n");
        writer.write("  },\n");
      }
      if (config.type != ChartConfig.Type.Line) {
        writer.write(String.format("  chart: { type: '%s' },\n", ChartConfig.chart2name(config.type)));
      }
      writer.write("  xAxis: {\n");

      if (config.labels != null || config.timing != ChartTiming.INDEX) {
        writer.write("  categories: [");
        if (config.labels != null) {
          assert config.labels.length == seqs[0].size();
          for (int i = 0; i < config.labels.length; ++i) {
            writer.write("'" + config.labels[i] + "'");
            if (i < config.labels.length - 1) {
              writer.write(",");
            }
          }
        } else {
          for (int i = 0; i < seqs[0].size(); ++i) {
            if (config.timing == ChartTiming.MONTHLY) {
              writer.write("'" + TimeLib.formatMonth(seqs[0].getTimeMS(i)) + "'");
            } else if (config.timing == ChartTiming.DAILY) {
              writer.write("'" + TimeLib.formatDate(seqs[0].getTimeMS(i)) + "'");
            } else {
              throw new NotImplementedException("Unknown chart timing: " + config.timing);
            }

            if (i < seqs[0].size() - 1) {
              writer.write(",");
            }
          }
        }
        writer.write("],\n"); // categories
      }

      addPlotBands(config.xBands, writer);
      addPlotLines(config.xLines, writer);
      writer.write("  },\n"); // xAxis

      writer.write("  yAxis: {\n");
      if (config.logarthimicYAxis) {
        writer.write("   type: 'logarithmic',\n");
      }
      if (!Double.isNaN(config.minorTickIntervalY)) {
        writer.write(String.format("   minorTickInterval: %.3f,\n", config.minorTickIntervalY));
      }
      if (!Double.isNaN(config.ymin)) {
        writer.write(String.format("   min: %.3f,\n", config.ymin));
      }
      if (!Double.isNaN(config.ymax)) {
        writer.write(String.format("   max: %.3f,\n", config.ymax));
      }
      writer.write("   title: { text: null },\n");
      addPlotBands(config.yBands, writer);
      addPlotLines(config.yLines, writer);
      writer.write("  },\n"); // yAxis

      if (config.type == ChartConfig.Type.Line) {
        writer.write("  chart: {\n");
        writer.write("   zoomType: 'xy',\n");
        writer.write("  },\n");
      }

      if (config.colors != null) {
        writer.write("  colors: [");
        for (int i = 0; i < config.colors.length; ++i) {
          writer.write(String.format("'%s'", config.colors[i]));
          if (i < config.colors.length - 1) {
            writer.write(',');
          }
        }
        writer.write("],\n");
      }

      writer.write("  plotOptions: {\n");
      if (config.type == ChartConfig.Type.Bar) {
        writer.write("   column: {\n");
        if (config.colors != null) {
          writer.write("    colorByPoint: true,\n");
        }
        writer.write("    pointPadding: 0,\n");
        writer.write("    groupPadding: 0.1,\n");
        writer.write("    borderWidth: 0\n");
        writer.write("   }\n");
      } else if (config.type == ChartConfig.Type.Area || config.type == ChartConfig.Type.PosNegArea) {
        writer.write("   area: {\n");
        writer.write("    fillColor: {\n");
        writer.write("      linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },\n");
        writer.write("      stops: [\n");
        if (config.type == ChartConfig.Type.Area) {
          writer.write("        [0, Highcharts.getOptions().colors[0]],\n");
          writer.write("        [1, Highcharts.Color(Highcharts.getOptions().colors[0]).setOpacity(0).get('rgba')]\n");
        } else {
          assert config.type == ChartConfig.Type.PosNegArea;
          FeatureVec vmin = seqs[0].getMin();
          FeatureVec vmax = seqs[0].getMax();
          for (int i = 1; i < seqs.length; ++i) {
            vmin._min(seqs[i].getMin());
            vmax._max(seqs[i].getMax());
          }
          double vzero = vmax.get(0) / (vmax.get(0) - vmin.get(0));

          writer.write("        [0, 'rgb(0,255,0)'],\n");
          writer.write(String.format("        [%f, 'rgba(0,255,0,0.5)'],\n", vzero));
          writer.write(String.format("        [%f, 'rgba(255,0,0,0.5)'],\n", vzero + 0.01));
          writer.write("        [1, 'rgb(255,0,0)'],\n");
        }
        writer.write("      ]},\n");
        writer.write("    marker: { radius: 2 },\n");
        writer.write("    lineWidth: 1\n");
        writer.write("    states: {\n");
        writer.write("      hover: { lineWidth: 1 }\n");
        writer.write("    },\n");
        // writer.write(" threshold: null\n");
        writer.write("   }\n");
      } else if (config.type == ChartConfig.Type.Line) {
        writer.write("    line: { marker: { enabled: false },\n");
        writer.write("            lineWidth: %d, },\n", config.lineWidth);
      }
      writer.write("  },\n");

      writer.write("  series: [\n");
      for (int i = seqs.length - 1; i >= 0; --i) {
        Sequence seq = seqs[i];
        writer.write("  { name: '" + seq.getName() + "',\n");
        writer.write("    data: [");
        for (int t = 0; t < seqs[i].length(); ++t) {
          double x = seqs[i].get(t, config.iDim);
          // x = FinLib.mul2ret(x); // TODO
          writer.write(String.format("%.6f%s", x, t == seqs[i].size() - 1 ? "" : ", "));
        }
        writer.write("],");
        int lineWidth = (Integer) seq.getMeta("lineWidth", 0);
        if (lineWidth > 0) {
          writer.write(" lineWidth: %d,\n", lineWidth);
        }
        writer.write(" },\n");
      }
      writer.write("  ]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body>\n");
      writer.write("<div id=\"%s\" style=\"width:%s; height:%s;\" />\n", config.containerName, config.width,
          config.height);
      writer.write("</body></html>\n");
    }
  }

  public static void saveScatter(File file, String title, String width, String height, int dim, Sequence returns1,
      Sequence returns2) throws IOException
  {
    assert returns1.length() == returns2.length();

    // Split return pairs into above / below y=x line.
    Sequence above = new Sequence();
    Sequence below = new Sequence();
    for (int i = 0; i < returns1.length(); ++i) {
      double x = returns1.get(i, dim);
      double y = returns2.get(i, dim);
      FeatureVec v = new FeatureVec(2, x, y);
      if (x >= y) {
        below.addData(v);
      } else {
        above.addData(v);
      }
    }

    // Find min/max values for drawing a y=x line.
    double vmin = Math.max(returns1.getMin().get(dim), returns2.getMin().get(dim));
    double vmax = Math.min(returns1.getMax().get(dim), returns2.getMax().get(dim));

    // Write HTML to generate the graph.
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");
      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      writer.write("  chart: {\n");
      writer.write("   type: 'scatter',\n");
      writer.write("   zoomType: 'xy'\n");
      writer.write("  },\n");
      writer.write("  xAxis: {\n");
      writer.write("   title: {\n");
      writer.write("    text: '" + returns1.getName() + "',\n");
      writer.write("    style: {\n");
      writer.write("     fontSize: '18px'\n");
      writer.write("    }\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  yAxis: {\n");
      writer.write("   title: {\n");
      writer.write("    text: '" + returns2.getName() + "',\n");
      writer.write("    style: {\n");
      writer.write("     fontSize: '18px'\n");
      writer.write("    }\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  legend: { enabled: false },\n");
      writer.write("  plotOptions: {\n");
      writer.write("   scatter: {\n");
      writer.write("    marker: { radius: 3, symbol: 'circle' },\n");
      writer.write("    tooltip: {\n");
      writer.write("     headerFormat: '',\n");
      writer.write("     pointFormat: '" + returns1.getName() + ": <b>{point.x}</b><br/>" + returns2.getName()
          + ": <b>{point.y}</b>'\n");
      writer.write("    }\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  series: [{\n");
      writer.write("   type: 'line',\n");
      writer.write(String.format("   data: [[%f, %f], [%f, %f]],\n", vmin, vmin, vmax, vmax));
      writer.write("   color: 'rgba(0,0,0,0.2)',\n");
      writer.write("   marker: { enabled: false },\n");
      writer.write("   states: { hover: { lineWidth: 0 } },\n");
      writer.write("   enableMouseTracking: false\n");
      writer.write("  },{\n");
      writer.write("   name: 'Returns (above)',\n");
      writer.write("   color: 'rgba(83, 223, 83, 0.5)',\n");
      writer.write("   data: [");
      for (int i = 0; i < above.length(); ++i) {
        double x = above.get(i, 0);
        double y = above.get(i, 1);
        writer.write(String.format("[%.3f,%.3f]", x, y));
        if (i < above.length() - 1) {
          writer.write(",");
        }
      }
      writer.write("]}, {\n");
      writer.write("   name: 'Returns (below)',\n");
      writer.write("   color: 'rgba(223, 83, 83, 0.5)',\n");
      writer.write("   data: [");
      for (int i = 0; i < below.length(); ++i) {
        double x = below.get(i, 0);
        double y = below.get(i, 1);
        writer.write(String.format("[%.3f,%.3f]", x, y));
        if (i < below.length() - 1) {
          writer.write(",");
        }
      }
      writer.write("  ]}]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body>\n");
      writer.write("<div id=\"chart\" style=\"width:%s; height:%s;\" />\n", width, height);
      writer.write("</body></html>\n");
    }
  }

  /**
   * Convenience function that takes individual arguments and builds a ChartConfig.
   */
  public static ChartConfig saveScatterPlot(File file, String title, String width, String height, double radius,
      String[] dimNames, Sequence... scatter) throws IOException
  {
    ChartConfig config = ChartConfig.buildScatter(file, title, width, height, radius, dimNames, scatter);
    config.showLegend(scatter.length > 1);
    saveScatterPlot(config);
    return config;
  }

  public static void saveScatterPlot(ChartConfig config) throws IOException
  {
    boolean hasNames = false;
    for (Sequence scatter : config.data) {
      assert scatter.getNumDims() >= 2;

      // Determine if any point has a name.
      if (!hasNames) {
        for (FeatureVec v : scatter) {
          if (v.getName() != null && !v.getName().isEmpty()) {
            hasNames = true;
            break;
          }
        }
      }
    }

    String[] dimNames = config.dimNames == null ? new String[] { "x", "y" } : config.dimNames;

    // Write HTML to generate the graph.
    try (Writer writer = new Writer(config.file)) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      if (config.type == ChartConfig.Type.Scatter) {
        writer.write("<script src=\"js/highcharts.js\"></script>\n");
      } else {
        writer.write("<script src=\"https://code.highcharts.com/highcharts.js\"></script>\n");
        writer.write("<script src=\"https://code.highcharts.com/highcharts-more.js\"></script>\n");
      }
      writer.write("  <script type=\"text/javascript\">\n");
      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: " + ChartConfig.getQuotedString(config.title) + " },\n");
      writer.write("  chart: {\n");
      writer.write("   type: '" + ChartConfig.chart2name(config.type) + "',\n");
      writer.write("   zoomType: 'xy'\n");
      writer.write("  },\n");
      writer.write("  xAxis: {\n");
      writer.write("   gridLineWidth: 1,\n");
      writer.write("   title: {\n");
      writer.write("    text: " + ChartConfig.getQuotedString(config.xAxisTitle) + ",\n");
      writer.write("    style: {\n");
      writer.write("     fontSize: '" + config.axisTitleFontSize + "px'\n");
      writer.write("    }\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  yAxis: {\n");
      writer.write("   title: {\n");
      writer.write("    text: " + ChartConfig.getQuotedString(config.yAxisTitle) + ",\n");
      writer.write("    style: {\n");
      writer.write("     fontSize: '" + config.axisTitleFontSize + "px'\n");
      writer.write("    }\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  legend: { enabled: " + config.showLegend + " },\n");
      writer.write("  plotOptions: {\n");
      if (config.type == ChartConfig.Type.Scatter) {
        writer.write("   scatter: {\n");
        writer.write(String.format("    marker: { radius: %.1f, symbol: 'circle' },\n", config.radius));
        writer.write("    dataLabels: {\n");
        writer.write("     enabled: " + config.showDataLabels + "\n");
        writer.write("    },\n");
        writer.write("   },\n");
        writer.write("   series: {\n");
        writer.write("    turboThreshold: 0\n");
        writer.write("   }\n");
      } else if (config.type == ChartConfig.Type.Bubble) {
        writer.write("   bubble: {\n");
        writer.write("    minSize: '" + config.minBubble + "',\n");
        writer.write("    maxSize:'" + config.maxBubble + "',\n");
        writer.write("   },\n");
      }
      writer.write("  },\n");
      writer.write("  tooltip: {\n");
      writer.write("   enabled: " + config.showToolTips + ",\n");
      if (config.showToolTips) {
        if (config.data.length <= 1) {
          writer.write("    headerFormat: '',\n");
        }
        String header = hasNames ? "<b>{point.name}</b><br/>" : "";
        StringBuilder zs = new StringBuilder();
        int zIndex = 0;
        for (int i = 0; i < dimNames.length; ++i) {
          if (i == config.xIndex || i == config.yIndex) continue;
          zs.append(String.format("<br/>%s: <b>{point.z%s}</b>", dimNames[i],
              zIndex == 0 ? "" : String.format("%d", zIndex + 1)));
          ++zIndex;
        }
        String format = String.format("    pointFormat: '%s%s: <b>{point.x}</b><br/>%s: <b>{point.y}</b>%s'\n", header,
            dimNames[config.xIndex], config.dimNames[config.yIndex], zs);
        writer.write(format);
      }
      writer.write("  },\n");
      writer.write("  series: [\n");
      for (Sequence scatter : config.data) {
        writer.write("   {\n");
        if (scatter.getName() != null && !scatter.getName().isEmpty()) {
          writer.write("  name: '%s',\n", scatter.getName());
        }
        writer.write("    data: [\n");
        for (FeatureVec v : scatter) {
          StringBuilder dataString = new StringBuilder(
              String.format("x:%.3f,y:%.3f", v.get(config.xIndex), v.get(config.yIndex)));
          int nDims = Math.min(v.getNumDims(), config.dimNames.length);
          int zIndex = 0;
          for (int i = 0; i < nDims; ++i) {
            if (i == config.xIndex || i == config.yIndex) continue;
            dataString.append(String.format(",z%s:%.3f", zIndex == 0 ? "" : String.format("%d", zIndex + 1), v.get(i)));
            ++zIndex;
          }
          if (hasNames) {
            String name = FinLib.getBaseName(v.getName());
            writer.write(String.format("{%s,name:'%s'},\n", dataString, FinLib.getBaseName(name)));
          } else {
            writer.write(String.format("{%s},\n", dataString));
          }
        }
        writer.write("]},\n");
      }
      writer.write(" ]});\n");
      writer.write("});\n");

      writer.write("</script></head><body>\n");
      writer.write("<div id=\"chart\" style=\"width:%s; height:%s;\" />\n", config.width, config.height);
      writer.write("</body></html>\n");
    }
  }

  public static void saveHighChartSplines(File file, String title, String width, String height, Sequence... splines)
      throws IOException
  {
    // Write HTML to generate the graph.
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");
      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      writer.write("  chart: { type: 'scatter' },\n");
      writer.write("  xAxis: {\n");
      writer.write("   title: {\n");
      writer.write("    text: 'Annual Volatility',\n");
      writer.write("    style: { fontSize: '18px' }\n");
      writer.write("   },\n");
      writer.write("   minorTickInterval: 0.5,\n");
      // writer.write(" min: 1.5,\n");
      // writer.write(" max: 5.0\n");
      writer.write("  },\n");
      writer.write("  yAxis: {\n");
      writer.write("   title: {\n");
      writer.write("    text: 'Annual Returns',\n");
      writer.write("    style: { fontSize: '18px' }\n");
      writer.write("   },\n");
      writer.write("   minorTickInterval: 1.0\n");
      writer.write("  },\n");
      writer.write("  legend: { enabled: true },\n");
      writer.write("  plotOptions: {\n");
      writer.write("   scatter: {\n");
      writer.write("    lineWidth: 2,\n");
      writer.write("    dataLabels: {\n");
      writer.write("     enabled: true,\n");
      writer.write("     formatter: function() { return this.point.name; }\n");
      writer.write("    }\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  series: [{\n");
      for (int iSpline = 0; iSpline < splines.length; ++iSpline) {
        Sequence spline = splines[iSpline];
        writer.write("    name: '" + spline.getName() + "',\n");
        writer.write("    data: [");
        for (int i = 0; i < spline.length(); ++i) {
          double cagr = spline.get(i, 0);
          double stdev = spline.get(i, 1);
          String name = spline.get(i).getName();
          writer.write(String.format("{x:%.3f, y:%.3f, name: '%s'}", stdev, cagr, name == null ? "" : name));
          if (i < spline.length() - 1) {
            writer.write(",");
          }
        }
        writer.write("]\n");
        writer.write(String.format("   }%s\n", iSpline == splines.length - 1 ? "" : ",{"));
      }
      writer.write("  ]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body>\n");
      writer.write("<div id=\"chart\" style=\"width:%s; height:%s\" />\n", width, height);
      writer.write("</body></html>\n");
    }
  }

  public static void saveBoxPlots(File file, String title, String width, String height, double minorTickInterval,
      List<DurationalStats> stats) throws IOException
  {

    saveBoxPlots(file, title, width, height, minorTickInterval, stats.toArray(new DurationalStats[stats.size()]));
  }

  public static void saveBoxPlots(File file, String title, String width, String height, double minorTickInterval,
      DurationalStats... returnStats) throws IOException
  {
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("<script src=\"http://code.highcharts.com/highcharts-more.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");

      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      writer.write("  chart: { type: 'boxplot' },\n");
      writer.write("  xAxis: { categories: [");
      for (int i = 0; i < returnStats.length; ++i) {
        writer.write(String.format("'%s'", FinLib.getNameWithBreak(returnStats[i].cumulativeReturns.getName())));
        if (i < returnStats.length - 1) {
          writer.write(",");
        }
      }
      writer.write("  ]},\n");
      writer.write("  yAxis: {\n");
      writer.write("   title: { text: null },\n");
      if (!Double.isNaN(minorTickInterval)) {
        writer.write(String.format("   minorTickInterval: %f,\n", minorTickInterval));
      }
      writer.write("  },\n");

      writer.write("  plotOptions: {\n");
      writer.write("    boxplot: {\n");
      writer.write("        fillColor: {\n");
      writer.write("          linearGradient: { x1: 0, y1: 0, x2: 1, y2: 1 },\n");
      writer.write("          stops: [\n");
      writer.write("            [0, 'rgba(200,220,255,0.8)'],\n");
      writer.write("            [1, 'rgba(180,200,240,0.8)'],\n");
      writer.write("          ]},\n");
      writer.write("        lineWidth: 2,\n");
      writer.write("        medianColor: '#0C5DA5',\n");
      writer.write("    }\n");
      writer.write("  },\n");

      writer.write("  series: [{\n");
      writer.write("    data: [\n");
      for (int i = 0; i < returnStats.length; ++i) {
        DurationalStats stats = returnStats[i];
        writer.write(String.format("     [%.2f,%.2f,%.2f,%.2f,%.2f]%s\n", stats.min, stats.percentile10, stats.median,
            stats.percentile90, stats.max, i < returnStats.length - 1 ? "," : ""));
      }
      writer.write("  ]}]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body>\n");
      writer.write("<div id=\"chart\" style=\"width:%s; height:%s;\" />\n", width, height);
      writer.write("</body></html>\n");
    }
  }

  /**
   * Generate an HTML file with a sortable table of strategy statistics.
   * 
   * Documentation for sortable table: http://tablesorter.com/docs/
   * 
   * @param file save HTML in this file
   * @param reduced true => hide some columns to save space
   * @param stats List of strategies to include
   * @throws IOException if there is a problem writing to the file
   */
  public static void saveStatsTable(File file, String width, boolean reduced, boolean includeRiskAdjusted,
      boolean includeQuartiles, List<CumulativeStats> stats) throws IOException
  {
    saveStatsTable(file, width, reduced, includeRiskAdjusted, includeQuartiles,
        stats.toArray(new CumulativeStats[stats.size()]));
  }

  /**
   * Generate an HTML file with a sortable table of strategy statistics.
   * 
   * Documentation for sortable table: http://tablesorter.com/docs/
   * 
   * @param file save HTML in this file
   * @param width width of table
   * @param reduced true => hide some columns to save space
   * @param strategyStats List of strategies to include
   * @throws IOException if there is a problem writing to the file
   */
  public static void saveStatsTable(File file, String width, boolean reduced, boolean includeRiskAdjusted,
      boolean includeQuartiles, CumulativeStats... strategyStats) throws IOException
  {
    String tableHtml = genStatsTable(reduced, includeRiskAdjusted, includeQuartiles, strategyStats);
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<title>Strategy Report</title>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#statsTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer.write(
          "<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write(String.format("</head><body style=\"width:%s\">\n", width));
      writer.write("<h2>Statistics for Different Strategies / Assets</h2>\n");

      writer.write(tableHtml);

      writer.write("<h2>Explanation of Each Column</h2>");
      writer.write("<div><ul>");
      writer.write(" <li><b>Strategy</b> - Name of the strategy or asset class</li>\n");
      writer.write(" <li><b>CAGR</b> - Compound Annual Growth Rate</li>\n");
      writer.write(" <li><b>Dev</b> - Standard deviation of annual returns</li>\n");
      if (!reduced && includeRiskAdjusted) {
        writer.write(" <li><b>Risk-Adjusted Return</b> - Adjusted CAGR after accounting for volatility</li>\n");
      }
      writer.write(" <li><b>Drawdown</b> - Maximum drawdown (largest gap from peak to trough)</li>\n");
      writer.write(" <li><b>Down 10%</b> - Percentage of time strategy is 10% or more below the previous peak</li>\n");
      if (!reduced) {
        writer.write(" <li><b>New High %</b> - Percentage of time strategy hits a new high</li>\n");
      }
      writer.write(" <li><b>Worst AR</b> - Return of worst year (biggest drop)</li>\n");
      if (!reduced && includeQuartiles) {
        writer.write(" <li><b>25% AR</b> - Return of year at the 25th percentile</li>\n");
      }
      writer.write(" <li><b>Median AR</b> - Median annual return (50th percentile)</li>\n");
      if (!reduced && includeQuartiles) {
        writer.write(" <li><b>75% AR</b> - Return of year at the 75th percentile</li>\n");
      }
      writer.write(" <li><b>Best AR</b> - Return of best year (biggest gain)</li>\n");
      if (!reduced) {
        writer.write(" <li><b>Speedup</b> - Percentage ahead of base returns per year</li>");
        writer.write(
            " <li><b>Score</b> - Semi-arbitrary combination of statistics used to rank strategies (higher is better)</li>\n");
      }
      writer.write("</ul></div>");
      writer.write("</body></html>\n");

    }
  }

  public static String genStatsTable(boolean reduced, boolean includeRiskAdjusted, boolean includeQuartiles,
      CumulativeStats... strategyStats) throws IOException
  {
    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {
      writer.write("<table id=\"statsTable\" class=\"tablesorter\" cellpadding=\"0\">\n");
      writer.write("<thead><tr>\n");
      writer.write(" <th>Strategy</th>\n");
      writer.write(" <th>CAGR</th>\n");
      writer.write(" <th>Drawdown</th>\n");
      writer.write(" <th>Dev</th>\n");
      if (!reduced && includeRiskAdjusted) {
        writer.write(" <th>Risk-Adjusted<br/>Return</th>\n");
      }
      writer.write(" <th>Down 10%</th>\n");

      if (!reduced) {
        writer.write(" <th>New High %</th>\n");
      }
      writer.write(" <th>Worst AR</th>\n");
      if (!reduced && includeQuartiles) {
        writer.write(" <th>25% AR</th>\n");
      }
      writer.write(" <th>Median AR</th>\n");
      if (!reduced && includeQuartiles) {
        writer.write(" <th>75% AR</th>\n");
      }
      writer.write(" <th>Best AR</th>\n");
      // if (!reduced) {
      // writer.write(" <th>Speedup</th>\n");
      // writer.write(" <th>Score</th>\n");
      // }

      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");

      int iRow = 0;
      for (CumulativeStats stats : strategyStats) {
        writer.write("<tr class=\"%s\">\n", iRow % 2 == 0 ? "evenRow" : "oddRow");
        String name = stats.name();
        Pattern p = Pattern.compile("(.+)\\s+\\(\\d.*\\)$");
        Matcher m = p.matcher(name);
        if (m.matches()) {
          name = m.group(1);
        }
        writer.write(String.format("<td><b>%s</b></td>\n", name));
        writer.write(String.format("<td>%.2f</td>\n", stats.cagr));
        writer.write(String.format("<td>%.2f</td>\n", stats.drawdown));
        writer.write(String.format("<td>%.2f</td>\n", stats.devAnnualReturn));
        if (!reduced && includeRiskAdjusted) {
          writer.write(
              String.format("<td>%.2f</td>\n", stats.cagr * strategyStats[0].devAnnualReturn / stats.devAnnualReturn));
        }
        writer.write(String.format("<td>%.2f</td>\n", stats.percentDown10));

        if (!reduced) {
          writer.write(String.format("<td>%.2f</td>\n", stats.percentNewHigh));
        }
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[0]));
        if (!reduced && includeQuartiles) {
          writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[1]));
        }
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[2]));
        if (!reduced && includeQuartiles) {
          writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[3]));
        }
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[4]));
        // if (!reduced) {
        // if (stats.cagr < baseReturn + 0.005) {
        // writer.write("<td>--</td>\n");
        // } else {
        // double speedup = FinLib.speedup(stats.cagr, baseReturn);
        // writer.write(String.format("<td>%.1f%%</td>\n", speedup * 100.0));
        // }
        // writer.write(String.format("<td>%.2f</td>\n", stats.scoreComplex()));
        // }

        writer.write("</tr>\n");
        ++iRow;
      }
      writer.write("</tbody>\n</table>\n");
    }
    return sw.toString();
  }

  /**
   * Generate an HTML file with a table of comparison statistics.
   * 
   * @param file save HTML in this file
   * @param stats comparison statistics to put in chart
   * @throws IOException if there is a problem writing to the file
   */
  public static void saveComparisonTable(File file, int width, ComparisonStats stats) throws IOException
  {
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<title>Comparison Statistics</title>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#myTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer.write(
          "<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write(String.format("</head><body style=\"width:%dpx\">\n", width));
      writer.write("<h2>Strategy Comparison</h2>\n");
      writer.write(String.format("<h3>%s</h3>\n", FinLib.getBaseName(stats.returns1.getName())));
      writer.write(String.format("<h3>%s</h3>\n", FinLib.getBaseName(stats.returns2.getName())));
      writer.write("<table id=\"comparisonTable\" class=\"tablesorter\">\n");
      writer.write("<thead><tr>\n");
      writer.write(" <th>Duration</th>\n");
      writer.write(" <th>Win Visualization</th>\n");
      writer.write(" <th>Win/Tie %</th>\n");
      writer.write(" <th>Win %</th>\n");
      writer.write(" <th>Lose %</th>\n");
      writer.write(" <th>Mean<br/>Excesss</th>\n");
      writer.write(" <th>Worst<br/>Excess</th>\n");
      writer.write(" <th>Median<br/>Excess</th>\n");
      writer.write(" <th>Best<br/>Excess</th>\n");
      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");

      for (Entry<Integer, Results> entry : stats.durationToResults.entrySet()) {
        int duration = entry.getKey();
        Results results = entry.getValue();
        writer.write("<tr>\n");

        if (duration < 12) {
          writer.write(String.format("<td>%d Month%s</td>\n", duration, duration > 1 ? "s" : ""));
        } else {
          int years = duration / 12;
          writer.write(String.format("<td>%d Year%s</td>\n", years, years > 1 ? "s" : ""));
        }

        writer.write("<td style=\"color: #3B3\">\n");
        writer.write(genWinBar(results.winPercent1, results.winPercent2));
        writer.write("</td>\n");

        writer.write(String.format("<td>%.1f</td>\n", 100.0 - results.winPercent2));
        writer.write(String.format("<td>%.1f</td>\n", results.winPercent1));
        writer.write(String.format("<td>%.1f</td>\n", results.winPercent2));
        writer.write(String.format("<td>%.2f</td>\n", results.meanExcess));
        writer.write(String.format("<td>%.2f</td>\n", results.worstExcess));
        writer.write(String.format("<td>%.2f</td>\n", results.medianExcess));
        writer.write(String.format("<td>%.2f</td>\n", results.bestExcess));
        writer.write("</tr>\n");
      }
      writer.write("</tbody>\n</table>\n");
      writer.write("</body></html>\n");
    }
  }

  public static void saveComparisonTable(File file, String width, List<ComparisonStats> stats) throws IOException
  {
    saveComparisonTable(file, width, stats.toArray(new ComparisonStats[stats.size()]));
  }

  public static void saveComparisonTable(File file, String width, ComparisonStats... allStats) throws IOException
  {
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<title>Comparison Statistics</title>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#myTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer.write(
          "<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write("</head><body style=\"width:%s\">\n", width);
      String defender = null;
      if (allStats[0].returns2 != null) {
        defender = FinLib.getBaseName(allStats[0].returns2.getName());
      } else {
        int n = allStats[0].defenders.length;
        if (n == 1) {
          defender = allStats[0].defenders[0].getName();
        } else {
          defender = String.format("%d Defenders", n);
        }
      }
      writer.write("<h2>Win Rate vs. %s</h2>\n", defender);
      writer.write("<table id=\"comparisonTable\" class=\"tablesorter\">\n");
      writer.write("<thead><tr>\n");

      double widthPercent = 100.0 / (allStats.length + 1);
      String th = String.format("<th style=\"width: %.2f%%\">", widthPercent);
      writer.write(th + "Duration</th>\n");
      for (ComparisonStats stats : allStats) {
        writer.write("%s%s</th>\n", th, FinLib.getBaseName(stats.returns1.getName()));
      }
      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");

      for (Entry<Integer, Results> entry : allStats[0].durationToResults.entrySet()) {
        int duration = entry.getKey();
        writer.write("<tr>\n");

        if (duration < 12) {
          writer.write("<td>%d Month%s</td>\n", duration, duration > 1 ? "s" : "");
        } else {
          int years = duration / 12;
          writer.write("<td>%d Year%s</td>\n", years, years > 1 ? "s" : "");
        }

        for (ComparisonStats stats : allStats) {
          Results results = stats.durationToResults.get(duration);
          writer.write("<td title=\"%.1f (%.1f + %.1f) | %.1f\" style=\"color: #3B3\">\n", 100 - results.winPercent2,
              results.winPercent1, 100 - (results.winPercent1 + results.winPercent2), results.winPercent2);
          // writer.write("%.1f\n", 100.0 - results.winPercent2);
          writer.write(genWinBar(results.winPercent1, results.winPercent2));
          writer.write("</td>\n");
          // writer.write("<td>%.1f (%.1f)</td>\n", results.winPercent1, results.winPercent2);
        }
        writer.write("</tr>\n");
      }
      writer.write("</tbody>\n</table>\n");
      writer.write("</body></html>\n");
    }
  }

  public static void saveBeatInflationTable(File file, String width, List<ComparisonStats> stats) throws IOException
  {
    saveBeatInflationTable(file, width, stats.toArray(new ComparisonStats[stats.size()]));
  }

  public static void saveBeatInflationTable(File file, String width, ComparisonStats... allStats) throws IOException
  {
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<title>Comparison Statistics</title>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#myTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer.write(
          "<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write(String.format("</head><body style=\"width:%s\">\n", width));
      writer.write("<h2>Beat Inflation</h2>\n");
      writer.write("<table id=\"comparisonTable\" class=\"tablesorter\">\n");
      writer.write("<thead><tr>\n");

      double widthPercent = 100.0 / (allStats.length + 1);
      String th = String.format("<th style=\"width: %.2f%%\">", widthPercent);
      writer.write(th + "Duration</th>\n");
      for (ComparisonStats stats : allStats) {
        writer.write(String.format("%s%s</th>\n", th, FinLib.getBaseName(stats.returns1.getName())));
      }
      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");

      for (Entry<Integer, Results> entry : allStats[0].durationToResults.entrySet()) {
        int duration = entry.getKey();
        writer.write("<tr>\n");

        if (duration < 12) {
          writer.write(String.format("<td>%d Month%s</td>\n", duration, duration > 1 ? "s" : ""));
        } else {
          int years = duration / 12;
          writer.write(String.format("<td>%d Year%s</td>\n", years, years > 1 ? "s" : ""));
        }

        for (ComparisonStats stats : allStats) {
          Results results = stats.durationToResults.get(duration);
          writer.write(String.format("<td title=\"%.1f | %.1f\" style=\"color: #3B3\">\n", results.winPercent1,
              results.winPercent2));
          // writer.write(String.format("%.1f\n", 100.0 - results.winPercent2));
          writer.write(genWinBar(results.winPercent1, results.winPercent2));
          writer.write("</td>\n");
          // writer.write(String.format("<td>%.1f (%.1f)</td>\n", results.winPercent1, results.winPercent2));
        }
        writer.write("</tr>\n");
      }
      writer.write("</tbody>\n</table>\n");
      writer.write("</body></html>\n");
    }
  }

  public static void printDecadeTable(Sequence cumulativeReturns)
  {
    int iStart = TimeLib.findStartofFirstDecade(cumulativeReturns, false);
    if (iStart < 0) {
      return;
    }

    System.out.printf("<table id=\"decadeTable\" class=\"tablesorter\"><thead>\n");
    System.out.printf(
        "<tr><th>Decade</th><th>CAGR</th><th>Dev</th><th>Drawdown</th><th>Down 10%%</th><th>Total<br/>Return</th></tr>\n");
    System.out.printf("</thead><tbody>\n");

    for (int i = iStart; i + 120 < cumulativeReturns.length(); i += 120) {
      LocalDate date = TimeLib.ms2date(cumulativeReturns.getTimeMS(i));
      Sequence decade = cumulativeReturns.subseq(i, 121);
      CumulativeStats stats = CumulativeStats.calc(decade);
      System.out.printf(" <tr><td>%ds</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2fx</td></tr>\n",
          date.getYear(), stats.cagr, stats.devAnnualReturn, stats.drawdown, stats.percentDown10, stats.totalReturn);
    }
    System.out.printf("</tbody>\n</table>\n");
  }

  /**
   * Generate an HTML chart comparing the returns from two strategies for each decade.
   * 
   * @param returns1 cumulative returns for Strategy #1
   * @param returns2 cumulative returns for Strategy #2
   */
  public static String genDecadeTable(Sequence returns1, Sequence returns2)
  {
    assert returns1.length() == returns2.length();
    int iStart = TimeLib.findStartofFirstDecade(returns1, false);
    if (iStart < 0) {
      return null;
    }

    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {
      writer.write("<table id=\"decadeComparisonTable\" class=\"tablesorter\" cellspacing=\"0\"><thead>\n");
      writer.write("<tr><th>Decade</th><th>%s<br/>CAGR</th><th>%s<br/>CAGR</th><th>Excess<br/>Returns</th>\n",
          FinLib.getBaseName(returns1.getName()), FinLib.getBaseName(returns2.getName()));
      writer.write("<th>%s<br/>Dev</th><th>%s<br/>Dev</th></tr>\n", FinLib.getBaseName(returns1.getName()),
          FinLib.getBaseName(returns2.getName()));
      writer.write("</thead><tbody>\n");

      final double eps = 0.01; // epsilon for larger cagr
      int iRow = 0;
      for (int i = iStart; i + 12 * 6 < returns1.length(); i += 120) {
        assert returns1.getTimeMS(i) == returns2.getTimeMS(i);
        LocalDate date = TimeLib.ms2date(returns1.getTimeMS(i));
        final int n = Math.min(121, returns1.length() - i);
        Sequence decade1 = returns1.subseq(i, n);
        Sequence decade2 = returns2.subseq(i, n);
        CumulativeStats stats1 = CumulativeStats.calc(decade1);
        CumulativeStats stats2 = CumulativeStats.calc(decade2);

        String excess = String.format("%.2f", stats1.cagr - stats2.cagr);
        if (stats1.cagr > stats2.cagr + eps) {
          excess = "<font color=\"#070\">" + excess + "</font>";
        } else if (stats2.cagr > stats1.cagr + eps) {
          excess = "<font color=\"#700\">" + excess + "</font>";
        }
        writer.write(
            " <tr class=\"%s\"><td>%d%s</td><td>%.2f</td><td>%.2f</td><td>%s</td><td>%.2f</td><td>%.2f</td></tr>\n",
            iRow % 2 == 0 ? "evenRow" : "oddRow", date.getYear(), n == 121 ? "" : "*", stats1.cagr, stats2.cagr, excess,
            stats1.devAnnualReturn, stats2.devAnnualReturn);
        ++iRow;
      }
      writer.write("</tbody>\n</table>\n");
    } catch (IOException e) {}
    return sw.toString();
  }

  /**
   * Generate HTML for a win bar that shows the win percents (and any ties) as green/yellow/red bars.
   * 
   * @param winPercent1 percentage of time first strategy wins
   * @param winPercent2 percentage of time second strategy wins
   * @return HTML that represents a win bar
   */
  public static String genWinBar(double winPercent1, double winPercent2)
  {
    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {

      double tiePercent = 100.0 - (winPercent1 + winPercent2);
      assert tiePercent >= 0.0;
      if (winPercent1 > 0) {
        writer.write(
            "<span style=\"float: left; width: %.2f%%; background: #53df53; white-space: nowrap;\">%.1f</span>\n",
            winPercent1, winPercent1 + tiePercent);
      }
      if (tiePercent > 0) {
        writer.write("<span style=\"float: left; width: %.2f%%; background: #dfdf53;\">&nbsp;</span>\n", tiePercent);
      }
      if (winPercent2 > 0) {
        writer.write("<span style=\"float: left; width: %.2f%%; background: #df5353;\">&nbsp;</span>\n", winPercent2);
      }
    } catch (IOException e) {}
    return sw.toString();
  }

  public static void saveAnnualStatsTable(File file, int width, boolean bCheckDate, int iPrice, List<Sequence> seqs)
      throws IOException
  {
    saveAnnualStatsTable(file, width, bCheckDate, iPrice, seqs.toArray(new Sequence[seqs.size()]));
  }

  public static void saveAnnualStatsTable(File file, int width, boolean bCheckDate, int iPrice, Sequence... seqs)
      throws IOException
  {
    final double diffMargin = 0.5;

    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<title>Annual Statistics</title>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#statsTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer.write(
          "<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write(String.format("</head><body style=\"width:%dpx\">\n", width));
      writer.write("<table id=\"statsTable\" class=\"tablesorter\">\n");
      writer.write("<thead><tr>\n");

      double widthPercent = 100.0 / (seqs.length + 1);
      String th = String.format("<th style=\"width: %.2f%%\">", widthPercent);
      writer.write(th + "Year</th>\n");
      for (Sequence seq : seqs) {
        writer.write(String.format("%s%s</th>\n", th, FinLib.getBaseName(seq.getName())));
      }
      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");

      LocalDate lastDate = TimeLib.ms2date(seqs[0].getEndMS());
      int iStart = TimeLib.findStartofFirstYear(seqs[0], bCheckDate);
      LocalDate date = TimeLib.ms2date(seqs[0].getTimeMS(iStart));
      double[] returns = new double[seqs.length];
      while (true) {
        LocalDate nextDate = date.with(TemporalAdjusters.firstDayOfNextYear());
        int iNext = seqs[0].getIndexAtOrBefore(TimeLib.toMs(nextDate.minusDays(1)));

        writer.write("<tr>\n");
        writer.write(String.format("<td><b>%d</b></td>", date.getYear()));

        for (int i = 0; i < seqs.length; ++i) {
          Sequence seq = seqs[i];
          double tr = FinLib.getTotalReturn(seq, Math.max(iStart - 1, 0), iNext, iPrice);
          double ar = FinLib.getAnnualReturn(tr, 12);
          returns[i] = ar;
        }
        int iBest = Library.argmax(returns);
        for (int i = 0; i < seqs.length; ++i) {
          boolean bold = (returns[iBest] - returns[i] < diffMargin);
          writer.write(String.format("<td>%s%.2f%s</td>", bold ? "<b>" : "", returns[i], bold ? "</b>" : ""));
        }
        writer.write("</tr>\n");

        // Find start of next year.
        date = nextDate;
        iStart = seqs[0].getIndexAtOrAfter(TimeLib.toMs(date));
        if (iStart < 0) break;

        // Make sure there is data for December of the next year.
        if (lastDate.getYear() == date.getYear() && lastDate.getMonthValue() < 12) break;
      }
      writer.write("</tbody>\n</table>\n");
      writer.write("</body></html>\n");
    }
  }

  public static void saveHoldings(File file, Map<LocalDate, DiscreteDistribution> holdings, SequenceStore store)
      throws IOException
  {
    final int width = 800;

    // Determine the max number of holdings.
    Set<String> names = new HashSet<>();
    for (DiscreteDistribution dist : holdings.values()) {
      for (String name : dist.names) {
        names.add(name);
      }
    }
    final String[] colors = Colors.getHex(names.size());

    // Generate HTML visualization.
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<title>Holdings</title>\n");
      writer.write("<link rel=\"stylesheet\" href=\"holdings.css\">\n");
      writer.write(String.format("</head><body style=\"width:%dpx\">\n", width));
      writer.write("<table id=\"holdingsTable\"  cellspacing=\"0\" style=\"width:100%\">\n");
      writer.write("<tbody>\n");

      int colorIndex = 0;
      Map<String, String> symbol2color = new HashMap<>();

      List<Map.Entry<LocalDate, DiscreteDistribution>> holdingsReversed = new ArrayList<>();
      holdingsReversed.addAll(holdings.entrySet());
      Collections.reverse(holdingsReversed);
      DiscreteDistribution prevDist = null;
      boolean bEvenRow = false;
      LocalDate prevDate = null;
      for (Map.Entry<LocalDate, DiscreteDistribution> entry : holdingsReversed) {
        LocalDate date = entry.getKey();
        DiscreteDistribution dist = alignDist(entry.getValue(), prevDist, date);
        prevDist = dist;
        writer.write(bEvenRow ? "<tr class=\"evenRow\">" : "<tr>");
        writer.write(String.format("<td class=\"date\">%s</td>\n", date));
        double combinedReturn = 0.0;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < dist.size(); ++i) {
          if (dist.weights[i] < 0.02) continue; // Skip very small allocations.
          String symbol = dist.names[i];
          if (!symbol2color.containsKey(symbol)) {
            symbol2color.put(symbol, colors[colorIndex]);
            colorIndex = (colorIndex + 1) % colors.length;
          }
          String returnString = "";
          if (prevDate != null) {
            Sequence seq = store.tryGet(dist.names[i]);
            if (seq != null) {
              IndexRange range = seq.getIndices(TimeLib.toMs(date), TimeLib.toMs(prevDate), EndpointBehavior.Closest);
              double tr = FinLib.getTotalReturn(seq, range.first, range.second, FinLib.AdjClose);
              double r = FinLib.mul2ret(tr);
              combinedReturn += r * dist.weights[i];
              returnString = String.format(" (%.2f)", r);
            }
          }
          sb.append(String.format(
              "<span style=\"float: left; width: %.4f%%; background: %s; white-space: nowrap;\">%s%s</span>\n",
              dist.weights[i] * 100 - 0.0001, symbol2color.get(symbol), symbol, returnString));
        }
        writer.write(String.format("<td class=\"combined-return\">%.2f</td>\n", combinedReturn));
        writer.write("<td class=\"viz\">\n");
        writer.write(sb.toString());
        writer.write("</td></tr>\n");
        bEvenRow = !bEvenRow;
        prevDate = date;
      }
      writer.write("</tbody>\n</table>\n");
      writer.write("</body></html>\n");
    }
  }

  private static DiscreteDistribution alignDist(DiscreteDistribution dist, DiscreteDistribution base, LocalDate date)
  {
    if (dist == null) return null;
    if (base == null) return dist;

    DiscreteDistribution aligned = new DiscreteDistribution(dist);
    boolean[] matchFrom = new boolean[dist.size()];
    boolean[] matchTo = new boolean[dist.size()];

    // Align all matching symbols.
    int iNext = dist.size() - 1;
    for (int iBase = base.size() - 1; iBase >= 0; --iBase) {
      int iDist = dist.find(base.names[iBase]);
      if (iDist < 0) continue;

      matchFrom[iDist] = true;
      if (iBase < iNext) {
        iNext = iBase;
      }
      assert !matchTo[iNext];
      matchTo[iNext] = true;
      aligned.names[iNext] = dist.names[iDist];
      aligned.weights[iNext] = dist.weights[iDist];
      --iNext;
    }

    // Fill in the gaps.
    for (int i = 0; i < dist.size(); ++i) {
      if (matchFrom[i]) continue;

      // Find first open spot.
      int index = 0;
      for (; index < dist.size(); ++index) {
        if (!matchTo[index]) break;
      }
      matchFrom[i] = true;
      matchTo[index] = true;
      aligned.names[index] = dist.names[i];
      aligned.weights[index] = dist.weights[i];
    }

    return aligned;
  }

  public static void saveHighChartKDE(File file, String title, double xstart, double xstep, int width, int height,
      Sequence... seqs) throws IOException
  {
    try (Writer writer = new Writer(file)) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"%s\"></script>\n", jquery);
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");

      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      writer.write(String.format("  chart: { type: 'area' },\n"));
      writer.write("  plotOptions: {\n");
      writer.write("   area: {\n");
      writer.write(String.format("    pointStart: %.4f,\n", xstart));
      writer.write(String.format("    pointInterval: %.4f,\n", xstep));
      writer.write("     marker: { enabled: false }\n");
      writer.write("   }\n");
      writer.write("  },\n");

      writer.write("  series: [\n");
      for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
        Sequence seq = seqs[iSeq];
        writer.write("  { name: '" + seq.getName() + "',\n");
        writer.write("    data: [");
        for (int i = 0; i < seqs[iSeq].length(); ++i) {
          writer.write(String.format("%.4f%s", seqs[iSeq].get(i, 0), i == seqs[iSeq].size() - 1 ? "" : ", "));
        }
        writer.write("] }");
        if (iSeq < seqs.length - 1) {
          writer.write(',');
        }
        writer.write("\n");
      }
      writer.write("  ]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body style=\"width:" + width + "px;\">\n");
      writer.write("<div id=\"chart\" style=\"width:100%; height:" + height + "px;\" />\n");
      writer.write("</body></html>\n");
    }
  }
}
