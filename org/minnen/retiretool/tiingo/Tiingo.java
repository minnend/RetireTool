package org.minnen.retiretool.tiingo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;

public class Tiingo
{
  public static final String auth                 = System.getenv("tiingo.auth");

  public static final File   financePath          = new File("G:/research/finance");
  public static final File   tiingoPath           = new File(financePath, "tiingo");
  public static final File   metaPath             = new File(tiingoPath, "meta");
  public static final File   eodPath              = new File(tiingoPath, "eod");
  public static final File   supportedTickersPath = new File(tiingoPath, "tiingo_supported_tickers-20171219.csv");

  public static Simulation setupSimulation(String[] symbols, double startingBalance, double monthlyDeposit,
      Slippage slippage, SequenceStore store) throws IOException
  {
    // Load all supported symbols.
    List<TiingoFund> funds = TiingoIO.loadTickers(Tiingo.supportedTickersPath);
    System.out.printf("Funds: %d\n", funds.size());

    // Load EOD data and metadata for all symbols.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : symbols) {
      TiingoFund fund = TiingoFund.get(symbol);
      if (fund == null) {
        System.out.printf("Unavailable: %s\n", symbol);
        continue;
      }

      File file = new File(Tiingo.eodPath, fund.ticker + "-eod.csv");
      if (!file.exists()) {
        System.out.printf("Missing: %5s (%s)\n", symbol, fund.assetType);
        if (!TiingoIO.saveFundMetadata(fund, false) || !TiingoIO.saveFundEodData(fund, false)) continue;
      }

      fund.seq = TiingoIO.loadEodData(symbol);
      TiingoIO.loadMetadata(symbol);
      seqs.add(fund.seq);
    }

    // Sort by start time and report fund metadata.
    seqs.sort(Sequence.getStartDateComparator());
    for (Sequence seq : seqs) {
      TiingoMetadata meta = TiingoMetadata.get(seq.getName());
      System.out.printf("%5s  [%s] -> [%s]  %s\n", seq.getName(), TimeLib.formatDate2(seq.getStartMS()),
          TimeLib.formatDate2(seq.getEndMS()), meta.name);
    }

    // Find common start/end times (last start and first end).
    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    // commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31);
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
