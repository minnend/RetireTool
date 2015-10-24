package org.minnen.retiretool.predictor;

import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

public class Multi3Predictor extends AssetPredictor
{
  public enum Disposition {
    Defensive, Cautious, Moderate, Aggressive
  }

  private final AssetPredictor[] predictors;
  private final Disposition      disposition;
  private final int              assetMap;

  private Multi3Predictor(String name, AssetPredictor[] basePredictors, Disposition disposition, int assetMap,
      SequenceStore store)
  {
    super(name, store);
    assert basePredictors.length == 3;
    this.predictors = basePredictors;
    this.disposition = disposition;
    this.assetMap = assetMap;
  }

  public Multi3Predictor(String name, AssetPredictor[] basePredictors, Disposition disposition, SequenceStore store)
  {
    this(name, basePredictors, disposition, -1, store);
  }

  public Multi3Predictor(String name, AssetPredictor[] basePredictors, int assetMap, SequenceStore store)
  {
    this(name, basePredictors, Disposition.Defensive, assetMap, store);
  }

  @Override
  public int selectAsset(Sequence... seqs)
  {
    assert seqs.length >= 2;

    // Calculate code associated with base predictors.
    int code = 0;
    for (int i = 0; i < predictors.length; ++i) {
      code <<= 1;
      int prediction = predictors[i].selectAsset(seqs);
      if (prediction == 0) {
        ++code;
      }
    }

    return selectAsset(code);
  }

  private int selectAsset(int code)
  {
    assert code >= 0 && code <= 7;

    final int risky = 0;
    final int safe = 1;

    if (assetMap >= 0) {
      int x = (assetMap >> code) & 1;
      return (x == 1 ? risky : safe);
    } else {
      // Complete support or shortest + mid => always risky.
      if (code >= 6) {
        return risky;
      }

      // Shortest + support => only Defensive is safe.
      if (code == 5) {
        return disposition == Disposition.Defensive ? safe : risky;
      }

      // Not shortest and zero or one other => always safe.
      if (code <= 2) {
        return safe;
      }

      // Not shortest but both others.
      if (code == 3) {
        return disposition == Disposition.Defensive || disposition == Disposition.Cautious ? safe : risky;
      }

      // Only short-term support.
      assert code == 4;
      return disposition == Disposition.Aggressive ? risky : safe;
    }
  }
}
