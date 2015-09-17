package org.minnen.retiretool.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ TestBond.class, TestInvestmentStats.class, TestLibrary.class, TestFinLib.class, TestSequence.class,
    TestSequenceStore.class })
public class AllTests
{

}
