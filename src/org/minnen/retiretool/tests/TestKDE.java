package org.minnen.retiretool.tests;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.minnen.retiretool.ckde.KDE;
import org.minnen.retiretool.ckde.Neighbor;

public class TestKDE
{
  @Test
  public void testCDFOneSample()
  {
    List<Neighbor> neighbors = new ArrayList<>();
    neighbors.add(new Neighbor(0.0, 0.0, 1.0, 0L));
    KDE kde = new KDE(neighbors, 1.0);

    assertEquals(0.0, kde.cdf(-10.0), 0.001);
    assertEquals(1.0, kde.cdf(10.0), 0.001);
    assertEquals(0.5, kde.cdf(0.0), 0.001);

    double diff = kde.cdf(0.2) - (1.0 - kde.cdf(-0.2));
    assertEquals(0.0, diff, 0.001);
  }

  @Test
  public void testCDFTwoSamples()
  {
    List<Neighbor> neighbors = new ArrayList<>();
    neighbors.add(new Neighbor(-10.0, 0.0, 1.0, 0L));
    neighbors.add(new Neighbor(10.0, 0.0, 1.0, 0L));
    KDE kde = new KDE(neighbors, 1.0);

    assertEquals(0.0, kde.cdf(-100.0), 0.001);
    assertEquals(1.0, kde.cdf(100.0), 0.001);
    assertEquals(0.5, kde.cdf(0.0), 0.001);
    assertEquals(0.25, kde.cdf(-10.0), 0.001);
    assertEquals(0.75, kde.cdf(10.0), 0.001);

    double diff = kde.cdf(0.2) - (1.0 - kde.cdf(-0.2));
    assertEquals(0.0, diff, 0.001);
  }

  @Test
  public void testCDFTwoWeightedSamples()
  {
    List<Neighbor> neighbors = new ArrayList<>();
    neighbors.add(new Neighbor(-10.0, 0.0, 1.0, 0L));
    neighbors.add(new Neighbor(10.0, 0.0, 3.0, 0L));
    KDE kde = new KDE(neighbors, 1.0);

    assertEquals(0.0, kde.cdf(-100.0), 0.001);
    assertEquals(1.0, kde.cdf(100.0), 0.001);
    assertEquals(0.25, kde.cdf(0.0), 0.001);
    assertEquals(0.125, kde.cdf(-10.0), 0.001);
    assertEquals(0.25 + 0.75 / 2.0, kde.cdf(10.0), 0.001);
  }

  @Test
  public void testPDFOneSample()
  {
    List<Neighbor> neighbors = new ArrayList<>();
    neighbors.add(new Neighbor(0.0, 0.0, 1.0, 0L));
    KDE kde = new KDE(neighbors, 1.0);

    assertEquals(0.0, kde.density(-10.0), 0.001);
    assertEquals(0.0, kde.density(10.0), 0.001);
    assertEquals(0.3989422804, kde.density(0.0), 0.001);

    double diff = kde.density(0.2) - kde.density(0.2);
    assertEquals(0.0, diff, 0.001);
  }
}
