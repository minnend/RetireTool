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

  public List<PredictorConfig> getAll()
  {
    List<PredictorConfig> list = new ArrayList<>();
    while (true) {
      PredictorConfig config = get();
      if (config == null) break;
      assert config.isValid();
      list.add(config);
    }
    return list;
  }

  public boolean isDone()
  {
    return index >= size();
  }

  public double percent()
  {
    return (double) index / size();
  }

  protected void advance()
  {
    for (Scanner param : parameters) {
      param.advance();
      if (param.isDone()) {
        param.reset();
      } else {
        break;
      }
    }
    ++index;
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
