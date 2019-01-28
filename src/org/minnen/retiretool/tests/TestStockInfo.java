package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.screener.StockInfo;

public class TestStockInfo
{
  @Test
  public void testSerialize()
  {
    String serialized = "name|symbol|sector|industry|3|4|0.25|1.0|2.5|0.42|1234.5";
    StockInfo info = StockInfo.fromString(serialized);
    assertNotEquals(null, info);
    String s = info.serializeToString();
    StockInfo info2 = StockInfo.fromString(s);
    assertEquals(info, info2);
  }
}
