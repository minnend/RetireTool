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
      "[14,0] / [246,100] m=900 | [4,2] / [155,51] m=394 | [28,0] / [218,192] m=664",
      "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [48,0] / [208,47] m=33",
      "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [53,0] / [229,177] m=687", };

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
