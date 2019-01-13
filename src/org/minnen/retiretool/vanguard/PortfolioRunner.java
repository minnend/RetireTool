package org.minnen.retiretool.vanguard;

import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;

public interface PortfolioRunner
{
  public FeatureVec run(DiscreteDistribution portfolio);
}
