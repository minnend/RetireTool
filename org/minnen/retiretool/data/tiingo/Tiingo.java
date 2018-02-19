package org.minnen.retiretool.data.tiingo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;

public class Tiingo
{
  public static Simulation setupSimulation(String[] symbols, Slippage slippage, SequenceStore store,
      Sequence... otherSeqs) throws IOException
  {
    return setupSimulation(symbols, 100000.0, 0.0, slippage, store, otherSeqs);
  }

  public static Simulation setupSimulation(String[] symbols, double startingBalance, double monthlyDeposit,
      Slippage slippage, SequenceStore store, Sequence... otherSeqs) throws IOException
  {
    // Load all supported symbols.
    TiingoIO.loadTickers();

    // Load EOD data and metadata for all Tiingo symbols.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : symbols) {
      TiingoFund fund = TiingoFund.get(symbol);
      if (fund == null) {
        System.out.printf("Unavailable: %s\n", symbol);
        continue;
      }

      if (!fund.loadData()) {
        System.err.printf("Failed to load data for %s.", fund.ticker);
        continue;
      }
      seqs.add(fund.data);
    }

    // Add non-Tiingo sequences to the list.
    for (Sequence seq : otherSeqs) {
      seqs.add(seq);
    }

    // Sort by start time and report fund metadata.
    seqs.sort(Sequence.getStartDateComparator());
    for (Sequence seq : seqs) {
      TiingoMetadata meta = TiingoMetadata.get(seq.getName());
      System.out.printf("%5s  [%s] -> [%s]  %s\n", seq.getName(), TimeLib.formatDate2(seq.getStartMS()),
          TimeLib.formatDate2(seq.getEndMS()), meta == null ? "" : meta.name);
    }

    // Find common start/end times (last start and first end).
    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    // commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31);

    store.addAll(seqs);
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    // Setup simulation.
    // TODO verify that adjClose/close * open = adjOpen for Tiingo data.
    Sequence guideSeq = store.get(symbols[0]).dup();
    PriceModel valueModel = PriceModel.adjCloseModel;
    PriceModel quoteModel = new PriceModel(PriceModel.Type.Open, true);
    SimFactory simFactory = new SimFactory(store, guideSeq, slippage, 0, startingBalance, monthlyDeposit, valueModel,
        quoteModel);
    return simFactory.build();
  }
}
