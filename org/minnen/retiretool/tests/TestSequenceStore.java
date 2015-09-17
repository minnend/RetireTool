package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.SequenceStore;
import org.minnen.retiretool.data.Sequence;

public class TestSequenceStore
{
  @Test
  public void testReturns()
  {
    Sequence r1 = new Sequence("r1", new double[] { 1, 2, 3 });
    Sequence r2 = new Sequence("r2", new double[] { 4, 5, 6 });

    SequenceStore store = new SequenceStore();
    assertEquals(0, store.size());

    store.add(r1);
    assertEquals(1, store.size());
    assertEquals(1, store.getNumReturns());

    store.add(r2);
    assertEquals(2, store.size());
    assertEquals(2, store.getNumReturns());

    assertEquals(0, store.getIndex("r1"));
    assertEquals(1, store.getIndex("r2"));

    assertEquals(r1, store.get("r1"));
    assertEquals(r2, store.get("r2"));

    assertEquals(0, store.getNumMisc());
  }

  @Test
  public void testAlias()
  {
    Sequence r1 = new Sequence("r1", new double[] { 1, 2, 3 });
    Sequence r2 = new Sequence("r2", new double[] { 4, 5, 6 });

    SequenceStore store = new SequenceStore();
    store.add(r1);
    store.add(r2);
    assertEquals(2, store.size());
    assertEquals(2, store.getNumReturns());

    assertEquals(0, store.getIndex("r1"));
    assertEquals(1, store.getIndex("r2"));

    store.alias("test1", "r1");
    store.alias("test2", "r2");

    assertEquals(0, store.getIndex("test1"));
    assertEquals(1, store.getIndex("test2"));

    assertEquals(r1, store.get("test1"));
    assertEquals(r2, store.get("test2"));
  }

  @Test
  public void testMiscSeqs()
  {
    Sequence r1 = new Sequence("r1", new double[] { 1, 2, 3 });
    Sequence r2 = new Sequence("r2", new double[] { 4, 5, 6 });
    Sequence r3 = new Sequence("r3", new double[] { 7, 8, 9 });

    SequenceStore store = new SequenceStore();
    assertEquals(0, store.getNumReturns());
    assertEquals(0, store.getNumMisc());

    store.addMisc(r1);
    store.addMisc(r2);
    store.add(r3);
    assertEquals(1, store.size());
    assertEquals(1, store.getNumReturns());
    assertEquals(2, store.getNumMisc());

    assertEquals(0, store.getMiscIndex("r1"));
    assertEquals(1, store.getMiscIndex("r2"));

    store.alias("test1", "r1");
    store.alias("test2", "r2");
    store.alias("test3", "r3");

    assertEquals(0, store.getMiscIndex("test1"));
    assertEquals(1, store.getMiscIndex("test2"));

    assertEquals(r1, store.getMisc("test1"));
    assertEquals(r2, store.getMisc("test2"));
  }
}
