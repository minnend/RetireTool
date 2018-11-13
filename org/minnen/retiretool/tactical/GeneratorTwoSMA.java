package org.minnen.retiretool.tactical;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;

/** Generator class for a multi-predictor that uses two SMA-based predictors. */
public class GeneratorTwoSMA extends ConfigGenerator
{
  private static final boolean      defaultDecision = true;
  private static final Set<Integer> contraryCodes   = new HashSet<Integer>();
  public static final Pattern       pattern         = Pattern.compile("(.+?)\\s*\\|\\s*(.+)");

  private final GeneratorSMA        generatorSMA    = new GeneratorSMA();

  public static final String[]      knownParams     = new String[] { "[5,0] / [184,44] m=353 | [14,0] / [254,42] m=989",
      "[15,0] / [155,105] m=997 | [7,1] / [113,98] m=446", "[14,0] / [246,100] m=900 | [4,2] / [155,51] m=394",
      "[20,0] / [126,54] m=690 | [53,0] / [163,28] m=2119", "[21,0] / [212,143] m=213 | [18,0] / [63,51] m=2383",
      "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320", "[5,0] / [184,44] m=353 | [50,0] / [206,105] m=2995",
      "[19,0] / [147,92] m=885 | [12,0] / [205,16] m=3145", "[14,0] / [246,100] m=900 | [61,0] / [213,25] m=37", };

  public static final ConfigMulti[] knownConfigs    = new ConfigMulti[knownParams.length];

  static {
    contraryCodes.add(0);

    for (int i = 0; i < knownParams.length; ++i) {
      knownConfigs[i] = parse(knownParams[i]);
    }
  }

  @Override
  public PredictorConfig genRandom()
  {
    PredictorConfig[] configs = new PredictorConfig[2];

    // Start with a known-good single predictor.
    configs[0] = GeneratorSMA.getRandomKnownConfig();

    // Add a random predictor.
    configs[1] = generatorSMA.genRandom();

    return new ConfigMulti(defaultDecision, contraryCodes, configs);
  }

  @Override
  public PredictorConfig genCandidate(PredictorConfig config)
  {
    ConfigMulti multi = (ConfigMulti) config;

    PredictorConfig[] perturbed = new PredictorConfig[2];
    perturbed[0] = multi.configs[0]; // no change to base config
    perturbed[1] = generatorSMA.genCandidate(multi.configs[1]);

    return new ConfigMulti(defaultDecision, contraryCodes, perturbed);
  }

  public static ConfigMulti parse(String line)
  {
    // Example: "[5,0] / [184,44] m=353 | [3,0] / [200,180] m=123"
    Matcher m = pattern.matcher(line);
    if (!m.matches()) {
      throw new IllegalArgumentException(String.format("Failed to parse two-SMA config: [%s]", line));
    }

    ConfigSMA[] configs = new ConfigSMA[2];
    configs[0] = GeneratorSMA.parse(m.group(1));
    configs[1] = GeneratorSMA.parse(m.group(2));

    return new ConfigMulti(defaultDecision, contraryCodes, configs);
  }
}
