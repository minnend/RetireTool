package org.minnen.retiretool.predictor.optimize;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.predictor.config.PredictorConfig;

public abstract class ConfigScanner<T extends PredictorConfig>
{
  protected int                 index      = 0;
  protected int                 nConfigs   = -1;
  protected final List<Scanner> parameters = new ArrayList<Scanner>();

  public abstract T get();

  public boolean isDone()
  {
    return index >= size();
  }

  public double percent()
  {
    return (double) index / size();
  }

  protected boolean advance()
  {
    boolean bAdvancedSomething = false;
    for (Scanner param : parameters) {
      param.advance();
      if (param.isDone()) {
        param.reset();
      } else {
        bAdvancedSomething = true;
        break;
      }
    }
    ++index;
    return bAdvancedSomething;
  }

  public int size()
  {
    if (nConfigs < 0) {
      assert parameters.size() > 0;
      nConfigs = 1;
      for (Scanner scanner : parameters) {
        nConfigs *= scanner.size();
      }
    }
    return nConfigs;
  }

  public void reset()
  {
    index = 0;
    for (Scanner param : parameters) {
      param.reset();
    }
  }
}
