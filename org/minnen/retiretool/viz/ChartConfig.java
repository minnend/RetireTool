package org.minnen.retiretool.viz;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.minnen.retiretool.data.Sequence;

public class ChartConfig
{
  public static enum Type {
    Unknown, Line, Bar, Area, PosNegArea, Scatter, Bubble
  };

  public enum ChartScaling {
    LINEAR, LOGARITHMIC
  };

  public enum ChartTiming {
    DAILY, MONTHLY, INDEX
  };

  public File           file;
  public Sequence[]     data;
  public Type           type               = Type.Unknown;
  public ChartTiming    timing             = ChartTiming.DAILY;
  public boolean        logarthimicYAxis;
  public double         minorTickIntervalY = Double.NaN;
  public String[]       labels;
  public String[]       colors;
  public String         title;
  public String         xAxisTitle;
  public String         yAxisTitle;
  public double         ymin               = Double.NaN;
  public double         ymax               = Double.NaN;
  public int            iDim               = 0;
  public int            xIndex             = 0;
  public int            yIndex             = 1;
  public String         width              = "100%";
  public String         height             = "600px";
  public int            axisTitleFontSize  = 16;
  public boolean        showLegend         = false;
  public boolean        showDataLabels     = false;
  public boolean        showToolTips       = true;
  public String[]       dimNames;
  public String         minBubble          = "7";
  public String         maxBubble          = "10%";
  public String         containerName      = "highcharts-container";
  public List<PlotBand> xBands             = new ArrayList<PlotBand>();
  public List<PlotBand> yBands             = new ArrayList<PlotBand>();
  public List<PlotLine> xLines             = new ArrayList<PlotLine>();
  public List<PlotLine> yLines             = new ArrayList<PlotLine>();
  public String         pathToBase;

  // Specific to scatter plots.
  public double         radius             = 3;

  public static String chart2name(Type chartType)
  {
    if (chartType == Type.Line) {
      return "line";
    } else if (chartType == Type.Bar) {
      return "column";
    } else if (chartType == Type.Area || chartType == Type.PosNegArea) {
      return "area";
    } else if (chartType == Type.Scatter) {
      return "scatter";
    } else if (chartType == Type.Bubble) {
      return "bubble";
    } else {
      return "ERROR";
    }
  }

  public static String getQuotedString(String s)
  {
    return getQuotedString(s, "null");
  }

  public static String getQuotedString(String s, String defaultIfNull)
  {
    if (s == null) return defaultIfNull;
    return "'" + s + "'";
  }

  public static ChartConfig buildLine(File file, String title, String width, String height, ChartScaling scaling,
      ChartTiming timing, List<Sequence> seqs) throws IOException
  {
    return buildLine(file, title, width, height, scaling, timing, seqs.toArray(new Sequence[seqs.size()]));
  }

  public static ChartConfig buildLine(File file, String title, String width, String height, ChartScaling scaling,
      ChartTiming timing, Sequence... seqs) throws IOException
  {
    return build(file, ChartConfig.Type.Line, title, null, null, width, height, Double.NaN, Double.NaN,
        scaling == ChartScaling.LOGARITHMIC ? 0.5 : Double.NaN, scaling, timing, 0, seqs);
  }

  public static ChartConfig buildScatter(File file, String title, String width, String height, double radius,
      String[] dimNames, Sequence... scatter)
  {
    return new ChartConfig(file).setType(ChartConfig.Type.Scatter).setTitle(title).setSize(width, height)
        .setRadius(radius).setDimNames(dimNames).setData(scatter);
  }

  public static ChartConfig build(File file, ChartConfig.Type chartType, String title, String[] labels, String[] colors,
      String width, String height, double ymin, double ymax, double minorTickIntervalY, ChartScaling scaling,
      ChartTiming timing, int dim, Sequence... seqs)
  {
    ChartConfig config = new ChartConfig(file).setType(chartType).setTitle(title).setLabels(labels).setColors(colors)
        .setSize(width, height).setMinMaxY(ymin, ymax).setMinorTickIntervalY(minorTickIntervalY)
        .setLogarthimicYAxis(scaling == ChartScaling.LOGARITHMIC).setTiming(timing).setIndexY(dim).setData(seqs);
    return config;
  }

  public ChartConfig(File file)
  {
    this.file = file;
  }

  public ChartConfig setFile(File file)
  {
    this.file = file;
    return this;
  }

  public ChartConfig setData(Sequence... data)
  {
    this.data = data;
    return this;
  }

  public ChartConfig setData(List<Sequence> data)
  {
    this.data = data.toArray(new Sequence[data.size()]);
    return this;
  }

  public ChartConfig setType(Type type)
  {
    this.type = type;
    return this;
  }

  public ChartConfig setMinY(double ymin)
  {
    this.ymin = ymin;
    return this;
  }

  public ChartConfig setMaxY(double ymax)
  {
    this.ymax = ymax;
    return this;
  }

  public ChartConfig setMinMaxY(double ymin, double ymax)
  {
    this.ymin = ymin;
    this.ymax = ymax;
    return this;
  }

  public ChartConfig setTiming(ChartTiming timing)
  {
    this.timing = timing;
    return this;
  }

  public ChartConfig setLogarthimicYAxis(boolean logarthimicYAxis)
  {
    this.logarthimicYAxis = logarthimicYAxis;
    return this;
  }

  public ChartConfig setMinorTickIntervalY(double minorTickIntervalY)
  {
    this.minorTickIntervalY = minorTickIntervalY;
    return this;
  }

  public ChartConfig setLabels(String[] labels)
  {
    this.labels = labels;
    return this;
  }

  public ChartConfig setColors(String[] colors)
  {
    this.colors = colors;
    return this;
  }

  public ChartConfig setTitle(String title)
  {
    this.title = title;
    return this;
  }

  public ChartConfig setXAxisTitle(String xAxisTitle)
  {
    this.xAxisTitle = xAxisTitle;
    return this;
  }

  public ChartConfig setYAxisTitle(String yAxisTitle)
  {
    this.yAxisTitle = yAxisTitle;
    return this;
  }

  public ChartConfig setAxisTitles(String xAxisTitle, String yAxisTitle)
  {
    this.xAxisTitle = xAxisTitle;
    this.yAxisTitle = yAxisTitle;
    return this;
  }

  public ChartConfig setWidth(int width)
  {
    this.width = String.format("%dpx", width);
    return this;
  }

  public ChartConfig setHeight(int height)
  {
    this.height = String.format("%dpx", height);
    return this;
  }

  public ChartConfig setSize(int width, int height)
  {
    setWidth(width);
    return setHeight(height);
  }

  public ChartConfig setWidth(String width)
  {
    this.width = width;
    return this;
  }

  public ChartConfig setHeight(String height)
  {
    this.height = height;
    return this;
  }

  public ChartConfig setSize(String width, String height)
  {
    setWidth(width);
    return setHeight(height);
  }

  public ChartConfig setRadius(double radius)
  {
    this.radius = radius;
    return this;
  }

  public ChartConfig setDimNames(String... names)
  {
    this.dimNames = names;
    return this;
  }

  public ChartConfig showDataLabels(boolean show)
  {
    this.showDataLabels = show;
    return this;
  }

  public ChartConfig showLegend(boolean show)
  {
    this.showLegend = show;
    return this;
  }

  public ChartConfig showToolTips(boolean show)
  {
    this.showToolTips = show;
    return this;
  }

  public ChartConfig setDimension(int iDim)
  {
    this.iDim = iDim;
    return this;
  }

  public ChartConfig setIndexX(int index)
  {
    this.xIndex = index;
    return this;
  }

  public ChartConfig setIndexY(int index)
  {
    this.yIndex = index;
    return this;
  }

  public ChartConfig setIndexXY(int xIndex, int yIndex)
  {
    this.xIndex = xIndex;
    this.yIndex = yIndex;
    return this;
  }

  public ChartConfig setMinBubble(String size)
  {
    this.minBubble = size;
    return this;
  }

  public ChartConfig setMaxBubble(String size)
  {
    this.maxBubble = size;
    return this;
  }

  public ChartConfig setBubbleSizes(String minSize, String maxSize)
  {
    this.minBubble = minSize;
    this.maxBubble = maxSize;
    return this;
  }

  public ChartConfig setContainerName(String name)
  {
    this.containerName = name;
    return this;
  }

  public ChartConfig addPlotBandX(PlotBand... bands)
  {
    for (PlotBand band : bands) {
      xBands.add(band);
    }
    return this;
  }

  public ChartConfig addPlotBandY(PlotBand... bands)
  {
    for (PlotBand band : bands) {
      yBands.add(band);
    }
    return this;
  }

  public ChartConfig addPlotBandX(Collection<? extends PlotBand> bands)
  {
    xBands.addAll(bands);
    return this;
  }

  public ChartConfig addPlotBandY(Collection<? extends PlotBand> bands)
  {
    yBands.addAll(bands);
    return this;
  }

  public ChartConfig addPlotLineX(PlotLine... Lines)
  {
    for (PlotLine Line : Lines) {
      xLines.add(Line);
    }
    return this;
  }

  public ChartConfig addPlotLineY(PlotLine... Lines)
  {
    for (PlotLine Line : Lines) {
      yLines.add(Line);
    }
    return this;
  }

  public ChartConfig addPlotLineX(Collection<? extends PlotLine> Lines)
  {
    xLines.addAll(Lines);
    return this;
  }

  public ChartConfig addPlotLineY(Collection<? extends PlotLine> Lines)
  {
    yLines.addAll(Lines);
    return this;
  }

  /** Set relative path to base dir that holds css and js subdirs. */
  public ChartConfig setPathToBase(String relPath)
  {
    pathToBase = relPath;
    return this;
  }
}
