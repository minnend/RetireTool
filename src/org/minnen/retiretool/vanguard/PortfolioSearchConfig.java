package org.minnen.retiretool.vanguard;

/** Configuration settings for searching over funds in a portfolio. */
public class PortfolioSearchConfig
{
  /** Minimum number of assets allowed in a valid portfolio. */
  public final int minAssets;

  /** Maximum number of assets allowed in a valid portfolio. */
  public final int maxAssets;

  /** Minimum percentage for each fund. */
  public final int minWeight;

  /** Maximum percentage for each fund. */
  public final int maxWeight;

  /** Search step for asset percentage. */
  public final int weightStep;

  public PortfolioSearchConfig(int minAssets, int maxAssets, int minWeight, int maxWeight, int weightStep)
  {
    this.minAssets = minAssets;
    this.maxAssets = maxAssets;
    this.minWeight = minWeight;
    this.maxWeight = maxWeight;
    this.weightStep = weightStep;
  }
}
