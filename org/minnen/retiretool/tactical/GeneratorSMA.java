package org.minnen.retiretool.tactical;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.TimeLib;

/** Generator class for a single SMA predictor. */
public class GeneratorSMA extends ConfigGenerator
{
  public static final Random      rng          = new Random();
  public static final long        gap          = 2 * TimeLib.MS_IN_DAY;
  public static final Pattern     pattern      = Pattern.compile("\\[(\\d+),(\\d+)\\] / \\[(\\d+),(\\d+)\\] m=(\\d+)");

  // List of good parameters for single SMA.
  public static final int[][]     knownParams  = new int[][] { new int[] { 20, 240, 150, 25 },
      new int[] { 15, 259, 125, 21 }, new int[] { 34, 182, 82, 684 }, new int[] { 5, 184, 44, 353 },
      new int[] { 9, 176, 167, 1243 }, new int[] { 14, 246, 100, 900 }, new int[] { 21, 212, 143, 213 },
      new int[] { 3, 205, 145, 438 }, new int[] { 15, 155, 105, 997 }, new int[] { 20, 126, 54, 690 },
      new int[] { 32, 116, 94, 938 }, new int[] { 22, 124, 74, 904 }, new int[] { 19, 201, 143, 207 },
      new int[] { 13, 186, 177, 1127 }, new int[] { 19, 147, 92, 885 }, new int[] { 18, 213, 79, 723 }, };

  public static final ConfigSMA[] knownConfigs = new ConfigSMA[knownParams.length];

  static {
    for (int i = 0; i < knownParams.length; ++i) {
      int[] p = knownParams[i];
      knownConfigs[i] = new ConfigSMA(p[0], 0, p[1], p[2], p[3], FinLib.AdjClose, GeneratorSMA.gap);
    }
  }

  public GeneratorSMA()
  {
    super(Mode.ALL_NEW);
  }

  public static ConfigSMA getRandomKnownConfig()
  {
    final int i = rng.nextInt(knownConfigs.length);
    return knownConfigs[i];
  }

  @Override
  public PredictorConfig genRandom()
  {
    return ConfigSMA.genRandom(FinLib.AdjClose, gap);
  }

  @Override
  public PredictorConfig genCandidate(PredictorConfig baseConfig)
  {
    ConfigSMA config = (ConfigSMA) baseConfig;
    final int N = 1000;
    for (int i = 0; i < N; ++i) {
      int nLookbackTriggerA = ConfigSMA.perturbLookback(config.nLookbackTriggerA);
      int nLookbackTriggerB = 0;
      int nLookbackBaseA = ConfigSMA.perturbLookback(config.nLookbackBaseA);
      int nLookbackBaseB = ConfigSMA.perturbLookback(config.nLookbackBaseB);
      int margin = ConfigSMA.perturbMargin(config.margin);
      ConfigSMA perturbed = new ConfigSMA(nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB, margin,
          config.iPrice, config.minTimeBetweenFlips);
      if (perturbed.isValid()) return perturbed;
    }
    throw new RuntimeException(String.format("Failed to generate a valid perturbed config after %d tries.", N));
  }

  public static ConfigSMA parse(String line)
  {
    // Example: "[5,0] / [184,44] m=353"
    Matcher m = pattern.matcher(line);
    if (!m.matches()) {
      throw new IllegalArgumentException(String.format("Failed to parse ConfigSMA: [%s]", line));
    }

    final int nLookbackTriggerA = Integer.parseInt(m.group(1));
    final int nLookbackTriggerB = Integer.parseInt(m.group(2));
    final int nLookbackBaseA = Integer.parseInt(m.group(3));
    final int nLookbackBaseB = Integer.parseInt(m.group(4));
    final int margin = Integer.parseInt(m.group(5));
    return new ConfigSMA(nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB, margin, FinLib.AdjClose,
        GeneratorSMA.gap);
  }
}
