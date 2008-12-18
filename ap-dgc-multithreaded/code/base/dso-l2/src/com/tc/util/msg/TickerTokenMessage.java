/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.util.msg;

import com.tc.async.api.EventContext;
import com.tc.util.TickerToken;

public interface TickerTokenMessage<T extends TickerToken> extends EventContext {
  
  public void init(T tickerToken);
  
  public T getTickerToken();

}
