/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tc.util;

import junit.framework.TestCase;

public class TickerTokenDelegateTest extends TestCase {
  
  public void testDelegate() {
    TickerTokenHandlerDelegate delegate = new TickerTokenHandlerDelegate();
    
    assertFalse(delegate.isDirtyAndClear());
    delegate.makeDirty();
    assertTrue(delegate.isDirtyAndClear());
    assertFalse(delegate.isDirtyAndClear());
    
    
    
  }

}
