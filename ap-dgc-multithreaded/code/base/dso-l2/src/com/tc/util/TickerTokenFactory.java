/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.util;

import com.tc.util.msg.TickerTokenMessage;

public interface TickerTokenFactory {

  public TickerToken createTriggerToken(int id, int tickValue, int tokenCount);
  
  public TickerToken createToken(TickerTokenMessage message);

  public TickerTokenMessage createMessage(TickerToken token);
}
