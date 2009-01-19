/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.util;

public class TestTickerToken extends TickerTokenImpl implements TickerToken {

  public TestTickerToken(int primaryID, int primaryTickValue, int tokenCount) {
    super(primaryID, primaryTickValue, tokenCount);
  }

  @Override
  public void collectToken(int id, CollectContext context) {
   //
  }

  @Override
  public boolean evaluateComplete() {
   return true;
  }

}