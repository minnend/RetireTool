package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

public class TestSequenceStore
{
  @Test
  public void testReturns()
  {
    Sequence r1 = AllTests.buildMonthlySequence("r1", new double[] { 1, 2, 3 });
    Sequence r2 = AllTests.buildMonthlySequence("r2", new double[] { 4, 5, 6 });

    SequenceStore store = new SequenceStore();
    assertEquals(0, store.size());

    store.add(r1);
    assertEquals(1, store.size());

    store.add(r2);
    assertEquals(2, store.size());

    assertEquals(r1, store.get("r1"));
    assertEquals(r2, store.get("r2"));
  }

  @Test
  public void testAlias()
  {
    Sequence r1 = AllTests.buildMonthlySequence("r1", new double[] { 1, 2, 3 });
    Sequence r2 = AllTests.buildMonthlySequence("r2", new double[] { 4, 5, 6 });

    SequenceStore store = new SequenceStore();
    store.add(r1);
    store.add(r2);
    assertEquals(2, store.size());

    store.alias("test1", "r1");
    store.alias("test2", "r2");

    assertEquals(r1, store.get("test1"));
    assertEquals(r2, store.get("test2"));
  }
}
