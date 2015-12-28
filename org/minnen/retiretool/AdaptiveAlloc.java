package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class AdaptiveAlloc
{
  public static final SequenceStore store       = new SequenceStore();
  public static final String[]      fundSymbols = new String[] { "SPY", "QQQ", "EWU", "EWG", "EWJ", "XLK", "XLE" };

  public static void main(String[] args) throws IOException
  {
    // Make sure we have the latest data.
    File dataDir = new File("g:/research/finance/yahoo/");
    if (!dataDir.exists()) {
      dataDir.mkdirs();
    }

    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(dataDir, symbol);
      DataIO.updateDailyDataFromYahoo(file, symbol, 8 * TimeLib.MS_IN_HOUR);
    }

    // Load data and trim to same time period.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(dataDir, symbol);
      Sequence seq = DataIO.loadYahooData(file);
      seqs.add(seq);
    }
    for (Sequence seq : seqs) {
      System.out.printf("%s: [%s] -> [%s]\n", seq.getName(), TimeLib.formatDate(seq.getStartMS()),
          TimeLib.formatDate(seq.getEndMS()));
    }
    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.toMs(2013, Month.DECEMBER, 31); // TimeLib.calcCommonEnd(seqs);
    System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatDate(commonStart), TimeLib.formatDate(commonEnd));

    long simStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusMonths(7).with(TemporalAdjusters.firstDayOfMonth()));
    double nSimMonths = TimeLib.monthsBetween(simStart, commonEnd);
    System.out.printf("Simulation Start: [%s] (%.1f months)\n", TimeLib.formatDate(simStart), nSimMonths);

    // TODO need to incorporate dividend payments explicitly.

    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      seq = seq.extractDims(FinLib.AdjClose);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);

      double tr = FinLib.getTotalReturn(seq, seq.getClosestIndex(simStart), -1, 0);
      double ar = FinLib.getAnnualReturn(tr, nSimMonths);
      System.out.printf("%s: %5.2f%%  (%.2fx)\n", seq.getName(), ar, tr);
    }

  }
}
