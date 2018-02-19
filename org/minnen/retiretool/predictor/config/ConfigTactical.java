package org.minnen.retiretool.predictor.config;

import java.util.Arrays;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.TacticalPredictor;

public class ConfigTactical extends PredictorConfig
{
  public final int      iPrice;
  public final String[] assetChoices;

  public ConfigTactical(int iPrice, String... assetChoices)
  {
    this.iPrice = iPrice;
    this.assetChoices = assetChoices;
  }

  @Override
  public boolean isValid()
  {
    return (iPrice >= 0 && assetChoices != null && assetChoices.length >= 2);
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... allAssets)
  {
    List<String> assetList = Arrays.asList(allAssets);
    for (String symbol : assetChoices) {
      assert assetList.contains(symbol);
    }
    return new TacticalPredictor(this, brokerAccess, assetChoices);
  }

}
