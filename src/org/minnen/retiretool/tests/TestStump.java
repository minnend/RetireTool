package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.ml.Stump;

public class TestStump
{
  @Test
  public void testThresholdZero()
  {
    Stump stumpPos = new Stump(0, 0.0, false);
    Stump stumpNeg = new Stump(0, 0.0, true);

    double[] postPos = new double[2];
    double[] postNeg = new double[2];
    FeatureVec x = new FeatureVec(1, 3.0);
    FeatureVec y = new FeatureVec(1, -3.0);
    int kp = stumpPos.predict(x, postPos);
    int kn = stumpNeg.predict(y, postNeg);

    assertEquals(kp, kn);
    assertArrayEquals(postPos, postNeg, 1e-6);
  }

  @Test
  public void testThresholdPos()
  {
    Stump stumpPos = new Stump(0, 1.0, false);
    Stump stumpNeg = new Stump(0, 1.0, true);

    double[] postPos = new double[2];
    double[] postNeg = new double[2];
    FeatureVec x = new FeatureVec(1, 3.0);
    FeatureVec y = new FeatureVec(1, -1.0);
    int kp = stumpPos.predict(x, postPos);
    int kn = stumpNeg.predict(y, postNeg);

    assertEquals(kp, kn);
    assertArrayEquals(postPos, postNeg, 1e-6);
  }

  @Test
  public void testThresholdNeg()
  {
    Stump stumpPos = new Stump(0, -10.0, false);
    Stump stumpNeg = new Stump(0, -10.0, true);

    double[] postPos = new double[2];
    double[] postNeg = new double[2];
    FeatureVec x = new FeatureVec(1, -5.0);
    FeatureVec y = new FeatureVec(1, -15.0);
    int kp = stumpPos.predict(x, postPos);
    int kn = stumpNeg.predict(y, postNeg);

    assertEquals(kp, kn);
    assertArrayEquals(postPos, postNeg, 1e-6);
  }
}
