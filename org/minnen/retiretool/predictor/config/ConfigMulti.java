package org.minnen.retiretool.predictor.config;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.IntPair;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;

public class ConfigMulti extends PredictorConfig
{
  public boolean                  defaultDecision;
  public Set<Integer>             contraryCodes;
  public Set<IntPair>             contraryPairs;
  private final PredictorConfig[] configs;

  public ConfigMulti(boolean defaultDecision, int exception, PredictorConfig... configs)
  {
    super(configs[0].iPredictIn, configs[0].iPredictOut);
    this.defaultDecision = defaultDecision;
    this.contraryCodes = new HashSet<Integer>();
    this.contraryCodes.add(exception);
    this.configs = Arrays.copyOf(configs, configs.length);
  }

  public ConfigMulti(boolean defaultDecision, Set<Integer> contraryCodes, PredictorConfig... configs)
  {
    super(configs[0].iPredictIn, configs[0].iPredictOut);
    this.defaultDecision = defaultDecision;
    this.contraryCodes = contraryCodes;
    this.configs = Arrays.copyOf(configs, configs.length);
  }

  public ConfigMulti(boolean defaultDecision, Set<Integer> contraryCodes, Set<IntPair> contraryPairs,
      PredictorConfig... configs)
  {
    super(configs[0].iPredictIn, configs[0].iPredictOut);
    this.defaultDecision = defaultDecision;
    this.contraryCodes = contraryCodes;
    this.contraryPairs = contraryPairs;
    this.configs = Arrays.copyOf(configs, configs.length);
  }

  public int size()
  {
    return configs.length;
  }

  @Override
  public boolean isValid()
  {
    // Make sure base configs are valid.
    for (int i = 0; i < configs.length; ++i) {
      if (!configs[i].isValid()) return false;
    }

    // Make sure all base configs have the same in/out values.
    for (int i = 1; i < configs.length; ++i) {
      assert configs[i].iPredictIn == configs[0].iPredictIn;
      assert configs[i].iPredictOut == configs[0].iPredictOut;
    }

    return true;
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    PredictorConfig[] perturbed = new PredictorConfig[configs.length];
    for (int i = 0; i < configs.length; ++i) {
      perturbed[i] = configs[i].genPerturbed();
    }
    return new ConfigMulti(defaultDecision, contraryCodes, contraryPairs, perturbed);
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    Predictor[] predictors = new Predictor[configs.length];
    for (int i = 0; i < configs.length; ++i) {
      predictors[i] = configs[i].build(brokerAccess, assetNames);
    }

    return new MultiPredictor(predictors, defaultDecision, contraryCodes, contraryPairs, assetNames[iPredictIn],
        assetNames[iPredictOut], brokerAccess);
  }

  @Override
  public String toString()
  {
    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {
      writer.write("MultiPredict default=%s", defaultDecision);
      for (int i = 0; i < configs.length; ++i) {
        writer.write(" %s%s", configs[i], i == configs.length - 1 ? "" : "\n");
      }
    } catch (IOException e) {}
    return sw.toString();
  }

  public static ConfigMulti buildTactical(int iPrice, int iPredictIn, int iPredictOut)
  {
    final boolean defaultDecision = true;
    final Set<Integer> contraryCodes = new HashSet<Integer>();
    contraryCodes.add(0);
    final long gap = 2 * TimeLib.MS_IN_DAY;
    PredictorConfig[] tacticalConfigs = new PredictorConfig[] {
        new ConfigSMA(20, 0, 240, 150, 25, iPrice, gap, iPredictIn, iPredictOut),
        new ConfigSMA(50, 0, 180, 30, 100, iPrice, gap, iPredictIn, iPredictOut),
        new ConfigSMA(10, 0, 220, 0, 200, iPrice, gap, iPredictIn, iPredictOut), };
    return new ConfigMulti(defaultDecision, contraryCodes, tacticalConfigs);
  }
}
