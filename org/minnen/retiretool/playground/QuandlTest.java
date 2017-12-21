package org.minnen.retiretool.playground;

import com.jimmoores.quandl.DataSetRequest;
import com.jimmoores.quandl.TabularResult;
import com.jimmoores.quandl.classic.ClassicQuandlSession;

public class QuandlTest
{
  public static void main(String[] args)
  {
    ClassicQuandlSession session = ClassicQuandlSession.create();
    TabularResult tabularResult = session.getDataSet(
      DataSetRequest.Builder.of("WIKI/VTSAX").build());
    System.out.println(tabularResult.toPrettyPrintedString());
  }
}
