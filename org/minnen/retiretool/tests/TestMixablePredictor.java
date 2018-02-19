package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.YahooIO;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.predictor.config.ConfigAdaptive;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.Weighting;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;

public class TestMixablePredictor
{
  public static final String[] fundSymbols  = new String[] { "VTSMX", "VBMFX", "VGENX", "WHOSX", "FAGIX", "DFGBX" };

  public final SequenceStore   store        = new SequenceStore();
  public Simulation            sim;

  public static final String[] assetSymbols = new String[fundSymbols.length + 1];

  static {
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
  }

  @Before
  public void setUp() throws IOException
  {
    assert assetSymbols[assetSymbols.length - 1].equals("cash");

    File dataDir = new File("g:/research/finance/");
    assert dataDir.isDirectory();

    // Load data and trim to same time period.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : fundSymbols) {
      File file = YahooIO.getFile(symbol);
      Sequence seq = YahooIO.loadData(file);
      seqs.add(seq);
    }

    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    long simStartMs = TimeLib
        .toMs(TimeLib.ms2date(commonStart).plusMonths(12).with(TemporalAdjusters.firstDayOfMonth()));

    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      seq = seq.extractDims(FinLib.AdjClose);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);
    }

    // Setup simulation.
    Sequence guideSeq = store.get(fundSymbols[0]);
    guideSeq = guideSeq.subseq(simStartMs, guideSeq.getEndMS(), EndpointBehavior.Closest);
    PriceModel priceModel = new PriceModel(PriceModel.Type.FixedIndex, false, 0, Double.NaN);
    sim = new Simulation(store, guideSeq, Slippage.None, 0, priceModel, priceModel);
  }

  private CumulativeStats runSim(PredictorConfig config)
  {
    Predictor predictor = config.build(sim.broker.accessObject, assetSymbols);
    Sequence returns = sim.run(predictor);
    return CumulativeStats.calc(returns);
  }

  private boolean checkWithMix(PredictorConfig baseConfig)
  {
    assert baseConfig.isValid();

    ConfigMixed mixedConfig = new ConfigMixed(new DiscreteDistribution(0.0, 1.0), baseConfig, baseConfig);
    assert mixedConfig.isValid();

    CumulativeStats cs1 = runSim(baseConfig);
    CumulativeStats cs2 = runSim(mixedConfig);
    return Math.abs(cs1.cagr - cs2.cagr) < 1e-3 && Math.abs(cs1.drawdown - cs2.drawdown) < 1e-3;
  }

  @Test
  public void testConst()
  {
    for (int i = 0; i < fundSymbols.length; ++i) {
      PredictorConfig config = new ConfigConst(fundSymbols[i]);
      assertTrue(checkWithMix(config));
    }
  }

  @Test
  public void testSMA()
  {
    PredictorConfig config;

    config = new ConfigSMA(10, 0, 60, 40, 1.0, 0, 2);
    assertTrue(checkWithMix(config));

    config = new ConfigSMA(50, 0, 100, 50, 0.0, 0, 0);
    assertTrue(checkWithMix(config));
  }

  @Test
  public void testAdaptive()
  {
    PredictorConfig config;

    config = new ConfigAdaptive(-1, -1, Weighting.Equal, 20, 100, 60, 0.5, 5, 5, TradeFreq.Weekly, 0);
    assertTrue(checkWithMix(config));

    config = new ConfigAdaptive(-1, -1, Weighting.Equal, 50, 100, 90, -1, 3, 5, TradeFreq.Monthly, 0);
    assertTrue(checkWithMix(config));

    config = new ConfigAdaptive(20, 1.0, Weighting.MinVar, 50, 100, 90, -1, 3, 5, TradeFreq.Weekly, 0);
    assertTrue(checkWithMix(config));

    config = new ConfigAdaptive(15, 0.5, Weighting.MinVar, 20, 50, 30, 0.8, 5, 5, TradeFreq.Monthly, 0);
    assertTrue(checkWithMix(config));
  }

  @Test
  public void testMulti()
  {
    long assetMap = 254;
    int iIn = 0;
    int iOut = 1;
    PredictorConfig config;
    PredictorConfig[] configs;

    configs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, 0, 2, iIn, iOut),
        new ConfigSMA(50, 0, 180, 30, 1.0, 0, 2, iIn, iOut), new ConfigSMA(10, 0, 220, 0, 2.0, 0, 2, iIn, iOut), };
    config = new ConfigMulti(assetMap, configs);
    assertTrue(checkWithMix(config));

    iIn = 1;
    iOut = 3;
    configs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, 0, 2, iIn, iOut),
        new ConfigSMA(50, 0, 180, 30, 1.0, 0, 2, iIn, iOut), new ConfigSMA(10, 0, 220, 0, 2.0, 0, 2, iIn, iOut), };
    config = new ConfigMulti(assetMap, configs);
    assertTrue(checkWithMix(config));
  }
}
