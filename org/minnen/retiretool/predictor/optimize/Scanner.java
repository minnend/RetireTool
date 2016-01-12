package org.minnen.retiretool.predictor.optimize;

public abstract class Scanner
{
  protected int index   = 0;
  protected int nValues = -1;

  public int size()
  {
    assert nValues > 0;
    return nValues;
  }

  public boolean isDone()
  {
    return index >= size();
  }

  public void advance()
  {
    ++index;
  }

  public void reset()
  {
    index = 0;
  }
}
