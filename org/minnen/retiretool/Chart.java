package org.minnen.retiretool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.ComparisonStats.Results;

public class Chart
{
  public enum ChartType {
    Line, Bar, Area, PosNegArea
  };

  public static String getNameWithBreak(String name)
  {
    int i = name == null ? -1 : name.indexOf(" (");
    if (i >= 0) {
      return name.substring(0, i) + "<br/>" + name.substring(i + 1);
    } else {
      return name;
    }
  }

  public static String chart2name(ChartType chartType)
  {
    if (chartType == ChartType.Line) {
      return "line";
    } else if (chartType == ChartType.Bar) {
      return "column";
    } else if (chartType == ChartType.Area || chartType == ChartType.PosNegArea) {
      return "area";
    } else {
      return "ERROR";
    }
  }

  public static void saveLineChart(File file, String title, int width, int height, boolean logarithmic,
      Sequence... seqs) throws IOException
  {
    saveHighChart(file, ChartType.Line, title, null, null, width, height, Double.NaN, Double.NaN, logarithmic ? 0.5
        : Double.NaN, logarithmic, 0, seqs);
  }

  public static void saveHighChart(File file, ChartType chartType, String title, String[] labels, String[] colors,
      int width, int height, double ymin, double ymax, double minorTickInterval, boolean logarithmic, int dim,
      Sequence... seqs) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js\"></script>\n");
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");

      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      if (chartType != ChartType.Line) {
        writer.write(String.format("  chart: { type: '%s' },\n", chart2name(chartType)));
      }
      writer.write("  xAxis: { categories: [");
      if (labels != null) {
        assert labels.length == seqs[0].size();
        for (int i = 0; i < labels.length; ++i) {
          writer.write("'" + labels[i] + "'");
          if (i < labels.length - 1) {
            writer.write(",");
          }
        }
      } else {
        for (int i = 0; i < seqs[0].size(); ++i) {
          writer.write("'" + Library.formatMonth(seqs[0].getTimeMS(i)) + "'");
          if (i < seqs[0].size() - 1) {
            writer.write(",");
          }
        }
      }
      writer.write("] },\n");

      writer.write("  yAxis: {\n");
      if (logarithmic) {
        writer.write("   type: 'logarithmic',\n");
      }
      if (!Double.isNaN(minorTickInterval)) {
        writer.write(String.format("   minorTickInterval: %.3f,\n", minorTickInterval));
      }
      if (!Double.isNaN(ymin)) {
        writer.write(String.format("   min: %.3f,\n", ymin));
      }
      if (!Double.isNaN(ymax)) {
        writer.write(String.format("   max: %.3f,\n", ymax));
      }
      writer.write("   title: { text: null }\n");
      writer.write("  },\n");

      if (chartType == ChartType.Line) {
        writer.write("  chart: {\n");
        writer.write("   zoomType: 'x'\n");
        writer.write("  },\n");
      }

      if (colors != null) {
        writer.write("  colors: [");
        for (int i = 0; i < colors.length; ++i) {
          writer.write(String.format("'%s'", colors[i]));
          if (i < colors.length - 1) {
            writer.write(',');
          }
        }
        writer.write("],\n");
      }

      writer.write("  plotOptions: {\n");
      if (chartType == ChartType.Bar) {
        writer.write("   column: {\n");
        if (colors != null) {
          writer.write("    colorByPoint: true,\n");
        }
        writer.write("    pointPadding: 0,\n");
        writer.write("    groupPadding: 0.1,\n");
        writer.write("    borderWidth: 0\n");
        writer.write("   }\n");
      } else if (chartType == ChartType.Area || chartType == ChartType.PosNegArea) {
        writer.write("   area: {\n");
        writer.write("    fillColor: {\n");
        writer.write("      linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },\n");
        writer.write("      stops: [\n");
        if (chartType == ChartType.Area) {
          writer.write("        [0, Highcharts.getOptions().colors[0]],\n");
          writer.write("        [1, Highcharts.Color(Highcharts.getOptions().colors[0]).setOpacity(0).get('rgba')]\n");
        } else {
          assert chartType == ChartType.PosNegArea;
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
        writer.write("    lineWidth: 1,\n");
        writer.write("    states: {\n");
        writer.write("      hover: { lineWidth: 1 }\n");
        writer.write("    },\n");
        // writer.write("    threshold: null\n");
        writer.write("   }\n");
      }
      writer.write("  },\n");

      writer.write("  series: [\n");
      for (int i = 0; i < seqs.length; ++i) {
        Sequence seq = seqs[i];
        writer.write("  { name: '" + seq.getName() + "',\n");
        writer.write("    data: [");
        for (int t = 0; t < seqs[0].length(); ++t) {
          writer.write(String.format("%.4f%s", seqs[i].get(t, dim), t == seqs[i].size() - 1 ? "" : ", "));
        }
        writer.write("] }");
        if (i < seqs.length - 1) {
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

  public static void saveHighChartScatter(File file, String title, int width, int height, int dim, Sequence returns1,
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
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js\"></script>\n");
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

      writer.write("</script></head><body style=\"width:" + width + "px;\">\n");
      writer.write("<div id=\"chart\" style=\"width:100%; height:" + height + "px;\" />\n");
      writer.write("</body></html>\n");
    }
  }

  public static void saveHighChartSplines(File file, String title, int width, int height, Sequence... splines)
      throws IOException
  {
    // Write HTML to generate the graph.
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js\"></script>\n");
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
      writer.write("   min: 1.5,\n");
      writer.write("   max: 5.0\n");
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

      writer.write("</script></head><body style=\"width:" + width + "px;\">\n");
      writer.write("<div id=\"chart\" style=\"width:100%; height:" + height + "px;\" />\n");
      writer.write("</body></html>\n");
    }
  }

  public static void saveBoxPlots(File file, String title, int width, int height, double minorTickInterval,
      ReturnStats... returnStats) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js\"></script>\n");
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("<script src=\"http://code.highcharts.com/highcharts-more.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");

      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      writer.write("  chart: { type: 'boxplot' },\n");
      writer.write("  xAxis: { categories: [");
      for (int i = 0; i < returnStats.length; ++i) {
        writer.write(String.format("'%s'", getNameWithBreak(returnStats[i].sourceSeq.getName())));
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
        ReturnStats stats = returnStats[i];
        writer.write(String.format("     [%.2f,%.2f,%.2f,%.2f,%.2f]%s\n", stats.min, stats.percentile10, stats.median,
            stats.percentile90, stats.max, i < returnStats.length - 1 ? "," : ""));
      }
      writer.write("  ]}]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body style=\"width:" + width + "px;\">\n");
      writer.write("<div id=\"chart\" style=\"width:100%; height:" + height + "px;\" />\n");
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
   * @param strategyStats List of strategies to include
   * @throws IOException if there is a problem writing to the file
   */
  public static void saveStatsTable(File file, int width, boolean reduced, InvestmentStats... strategyStats)
      throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<title>Strategy Statistics</title>\n");
      writer.write("<script src=\"http://code.jquery.com/jquery.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#myTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer
          .write("<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write(String.format("</head><body style=\"width:%dpx\">\n", width));
      writer.write("<h2>Statistics for Different Strategies / Assets</h2>\n");
      writer.write("<table id=\"statsTable\" class=\"tablesorter\">\n");
      writer.write("<thead><tr>\n");
      writer.write(" <th>Strategy</th>\n");
      writer.write(" <th>CAGR</th>\n");
      writer.write(" <th>StdDev</th>\n");
      writer.write(" <th>Drawdown</th>\n");
      writer.write(" <th>Down 10%</th>\n");
      if (!reduced) {
        writer.write(" <th>New High %</th>\n");
      }
      writer.write(" <th>Worst AR</th>\n");
      if (!reduced) {
        writer.write(" <th>25% AR</th>\n");
      }
      writer.write(" <th>Median AR</th>\n");
      if (!reduced) {
        writer.write(" <th>75% AR</th>\n");
      }
      writer.write(" <th>Best AR</th>\n");
      if (!reduced) {
        writer.write(" <th>Speedup</th>\n");
        writer.write(" <th>Score</th>\n");
      }
      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");

      // Calculate base total returns.
      double baseReturn = 1.0;
      if (!reduced) {
        baseReturn = Double.POSITIVE_INFINITY;
        for (InvestmentStats stats : strategyStats) {
          if (stats.cagr < baseReturn) {
            baseReturn = stats.cagr;
          }
        }
        if (baseReturn < 1.0) {
          baseReturn = 1.0;
        }
      }

      for (InvestmentStats stats : strategyStats) {
        writer.write("<tr>\n");
        String name = stats.name();
        Pattern p = Pattern.compile("(.+)\\s+\\(\\d.*\\)$");
        Matcher m = p.matcher(name);
        if (m.matches()) {
          name = m.group(1);
        }
        writer.write(String.format("<td><b>%s</b></td>\n", name));
        writer.write(String.format("<td>%.2f</td>\n", stats.cagr));
        writer.write(String.format("<td>%.2f</td>\n", stats.devAnnualReturn));
        writer.write(String.format("<td>%.2f</td>\n", stats.maxDrawdown));
        writer.write(String.format("<td>%.2f</td>\n", stats.percentDown10));
        if (!reduced) {
          writer.write(String.format("<td>%.2f</td>\n", stats.percentNewHigh));
        }
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[0]));
        if (!reduced) {
          writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[1]));
        }
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[2]));
        if (!reduced) {
          writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[3]));
        }
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[4]));
        if (!reduced) {
          if (stats.cagr < baseReturn + 0.005) {
            writer.write("<td>--</td>\n");
          } else {
            double speedup = FinLib.speedup(stats.cagr, baseReturn);
            writer.write(String.format("<td>%.1f%%</td>\n", speedup * 100.0));
          }
          writer.write(String.format("<td>%.2f</td>\n", stats.calcScore()));
        }
        writer.write("</tr>\n");
      }
      writer.write("</tbody>\n</table>\n");
      writer.write("<h2>Explanation of Each Column</h2>");
      writer.write("<div><ul>");
      writer.write(" <li><b>Strategy</b> - Name of the strategy or asset class</li>\n");
      writer.write(" <li><b>CAGR</b> - Compound Annual Growth Rate</li>\n");
      writer.write(" <li><b>StdDev</b> - Standard deviation of annual returns</li>\n");
      writer.write(" <li><b>Drawdown</b> - Maximum drawdown (largest gap from peak to trough)</li>\n");
      writer.write(" <li><b>Down 10%</b> - Percentage of time strategy is 10% or more below the previous peak</li>\n");
      if (!reduced) {
        writer.write(" <li><b>New High %</b> - Percentage of time strategy hits a new high</li>\n");
      }
      writer.write(" <li><b>Worst AR</b> - Return of worst year (biggest drop)</li>\n");
      if (!reduced) {
        writer.write(" <li><b>25% AR</b> - Return of year at the 25th percentile</li>\n");
      }
      writer.write(" <li><b>Median AR</b> - Median annual return (50th percentile)</li>\n");
      if (!reduced) {
        writer.write(" <li><b>75% AR</b> - Return of year at the 75th percentile</li>\n");
      }
      writer.write(" <li><b>Best AR</b> - Return of best year (biggest gain)</li>\n");
      if (!reduced) {
        writer.write(" <li><b>Speedup</b> - Percentage ahead of base returns per year</li>");
        writer
            .write(" <li><b>Score</b> - Semi-arbitrary combination of statistics used to rank strategies (higher is better)</li>\n");
      }
      writer.write("</ul></div>");
      writer.write("</body></html>\n");
    }
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
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<title>Comparison Statistics</title>\n");
      writer.write("<script src=\"http://code.jquery.com/jquery.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer.write(" $(document).ready(function() { $(\"#myTable\").tablesorter( {widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer
          .write("<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write(String.format("</head><body style=\"width:%dpx\">\n", width));
      writer.write("<h2>Strategy Comparison</h2>\n");
      writer.write("<table id=\"comparisonTable\" class=\"tablesorter\">\n");
      writer.write("<thead><tr>\n");
      writer.write(" <th>Duration</th>\n");
      writer.write(String.format(" <th>%s<br/>Win %%</th>\n", stats.returns1.getName()));
      writer.write(String.format(" <th>%s<br/>Win %%</th>\n", stats.returns2.getName()));
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

  public static void printDecadeTable(Sequence cumulativeReturns)
  {
    int iStart = Library.FindStartofFirstDecade(cumulativeReturns);
    if (iStart < 0) {
      return;
    }

    System.out.printf("<table id=\"decadeTable\" class=\"tablesorter\"><thead>\n");
    System.out
        .printf("<tr><th>Decade</th><th>CAGR</th><th>StdDev</th><th>Drawdown</th><th>Down 10%%</th><th>Total<br/>Return</th></tr>\n");
    System.out.printf("</thead><tbody>\n");

    Calendar cal = Library.now();
    for (int i = iStart; i + 120 < cumulativeReturns.length(); i += 120) {
      cal.setTimeInMillis(cumulativeReturns.getTimeMS(i));
      Sequence decade = cumulativeReturns.subseq(i, 121);
      InvestmentStats stats = InvestmentStats.calcInvestmentStats(decade);
      System.out.printf(" <tr><td>%ds</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2fx</td></tr>\n",
          cal.get(Calendar.YEAR), stats.cagr, stats.devAnnualReturn, stats.maxDrawdown, stats.percentDown10,
          stats.totalReturn);
    }
    System.out.printf("</tbody>\n</table>\n");
  }

  public static void printDecadeTable(Sequence returns1, Sequence returns2)
  {
    assert returns1.length() == returns2.length();
    int iStart = Library.FindStartofFirstDecade(returns1);
    if (iStart < 0) {
      return;
    }

    System.out.printf("<table id=\"decadeTable\" class=\"tablesorter\" style=\"width:100%%\"><thead>\n");
    System.out.printf("<tr><th>Decade</th><th>%s<br/>CAGR</th><th>%s<br/>CAGR</th>\n", returns1.getName(),
        returns2.getName());
    System.out.printf("<th>%s<br/>StdDev</th><th>%s<br/>StdDev</th></tr>\n", returns1.getName(), returns2.getName());
    System.out.printf("</thead><tbody>\n");

    Calendar cal = Library.now();
    for (int i = iStart; i + 120 < returns1.length(); i += 120) {
      assert returns1.getTimeMS(i) == returns2.getTimeMS(i);
      cal.setTimeInMillis(returns1.getTimeMS(i));
      Sequence decade1 = returns1.subseq(i, 121);
      Sequence decade2 = returns2.subseq(i, 121);
      InvestmentStats stats1 = InvestmentStats.calcInvestmentStats(decade1);
      InvestmentStats stats2 = InvestmentStats.calcInvestmentStats(decade2);
      System.out.printf(" <tr><td>%ds</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td></tr>\n",
          cal.get(Calendar.YEAR), stats1.cagr, stats2.cagr, stats1.devAnnualReturn, stats2.devAnnualReturn);
    }
    System.out.printf("</tbody></table>\n");
  }
}
