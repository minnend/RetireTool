package org.minnen.retiretool.tactical;

import java.util.HashSet;
import java.util.Set;

import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;

// TODO create GeneratorMultiSMA instead of explicit class for two and three.

/** Generator class for a multi-predictor that uses two SMA-based predictors. */
public class GeneratorThreeSMA extends ConfigGenerator
{
  private static final boolean      defaultDecision = true;
  private static final Set<Integer> contraryCodes   = new HashSet<Integer>();

  private final GeneratorSMA        generatorSMA    = new GeneratorSMA();

  // TODO old configs based on bug that duplicated config[1] to config[2]
  // "[5,0] / [184,44] m=353 | [14,0] / [254,42] m=989 | [13,0] / [263,43] m=955",
  // "[14,0] / [246,100] m=900 | [4,2] / [155,51] m=394 | [3,0] / [162,49] m=409",
  // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [7,0] / [21,14] m=1275",
  // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [8,0] / [22,16] m=1338",
  // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [8,0] / [21,15] m=1353",
  // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [9,0] / [21,14] m=1279",
  // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [9,0] / [21,14] m=1421",
  // "[19,0] / [147,92] m=885 | [12,0] / [205,16] m=3145 | [12,0] / [215,17] m=3402",
  // "[20,0] / [126,54] m=690 | [53,0] / [163,28] m=2119 | [54,0] / [166,29] m=2279",

  public static final String[]      knownParams     = new String[] {
      // "[14,0] / [246,100] m=900 | [4,2] / [155,51] m=394 | [28,0] / [218,192] m=664",
      // "[14,0] / [246,100] m=900 | [61,0] / [213,25] m=37 | [30,0] / [247,228] m=773",
      //
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [43,0] / [55,4] m=2025",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [13,0] / [122,30] m=562",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [5,0] / [178,50] m=145",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [46,0] / [138,53] m=556",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [48,0] / [208,47] m=33",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [51,0] / [180,3] m=2539",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [53,0] / [229,177] m=687",
      //
      // "[19,0] / [147,92] m=885 | [12,0] / [205,16] m=3145 | [39,0] / [33,18] m=685",
      //
      // "[36,0] / [185,87] m=181 | [43,0] / [148,89] m=746 | [18,0] / [233,229] m=1010",
      // "[12,0] / [26,18] m=1332 | [40,0] / [28,5] m=1217 | [38,0] / [100,46] m=640",
      // "[11,0] / [180,83] m=914 | [16,0] / [72,9] m=1749 | [36,0] / [162,18] m=2419",

      "[14,0] / [247,129] m=19 | [6,1] / [172,49] m=149 | [33,1] / [127,89] m=266",
      "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145 | [19,0] / [213,83] m=269",
      "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145 | [63,0] / [23,14] m=105",
      "[28,2] / [280,98] m=143 | [11,0] / [156,109] m=23 | [55,1] / [23,13] m=121",
      "[29,0] / [276,103] m=141 | [11,0] / [165,109] m=25 | [15,0] / [162,121] m=91", };

  public static final ConfigMulti[] knownConfigs    = new ConfigMulti[knownParams.length];

  static {
    contraryCodes.add(0);

    for (int i = 0; i < knownParams.length; ++i) {
      knownConfigs[i] = parse(knownParams[i]);
    }
  }

  public GeneratorThreeSMA(Mode mode)
  {
    super(mode);
  }

  @Override
  public PredictorConfig genRandom()
  {
    PredictorConfig[] configs = new PredictorConfig[3];

    if (mode == Mode.EXTEND) {
      // Start with a known-good double predictor.
      ConfigMulti multi = GeneratorTwoSMA.getRandomKnownConfig();
      configs[0] = multi.configs[0];
      configs[1] = multi.configs[1];

      // Add a random predictor.
      configs[2] = generatorSMA.genRandom();
    } else {
      assert mode == Mode.ALL_NEW;
      for (int i = 0; i < configs.length; ++i) {
        configs[i] = generatorSMA.genRandom();
      }
    }

    return new ConfigMulti(defaultDecision, contraryCodes, configs);
  }

  @Override
  public PredictorConfig genCandidate(PredictorConfig config)
  {
    ConfigMulti multi = (ConfigMulti) config;

    PredictorConfig[] perturbed = new PredictorConfig[3];
    if (mode == Mode.EXTEND) {
      perturbed[0] = multi.configs[0]; // no change to base configs
      perturbed[1] = multi.configs[1];
      perturbed[2] = generatorSMA.genCandidate(multi.configs[2]);
    } else {
      assert mode == Mode.ALL_NEW;
      for (int i = 0; i < perturbed.length; ++i) {
        perturbed[i] = generatorSMA.genCandidate(multi.configs[i]);
      }
    }

    return new ConfigMulti(defaultDecision, contraryCodes, perturbed);
  }

  public static ConfigMulti parse(String line)
  {
    // Example: "[5,0] / [184,44] m=353 | [3,0] / [200,180] m=123"
    String[] fields = line.split("\\s*\\|\\s*");
    if (fields.length != 3) {
      throw new IllegalArgumentException(String.format("Failed to parse three-SMA config: [%s]", line));
    }

    ConfigSMA[] configs = new ConfigSMA[3];
    for (int i = 0; i < configs.length; ++i) {
      configs[i] = GeneratorSMA.parse(fields[i]);
    }

    return new ConfigMulti(defaultDecision, contraryCodes, configs);
  }
}
