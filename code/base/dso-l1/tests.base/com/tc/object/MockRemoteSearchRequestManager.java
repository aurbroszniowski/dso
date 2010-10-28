/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.object;

import com.tc.net.NodeID;
import com.tc.object.msg.ClientHandshakeMessage;
import com.tc.object.session.SessionID;
import com.tc.search.SearchQueryResults;

import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class MockRemoteSearchRequestManager implements RemoteSearchRequestManager {

  public void addResponseForQuery(final SessionID sessionID, final SearchRequestID requestID,
                                  final SearchQueryResults results, final NodeID nodeID) {
    //
  }

  public boolean hasRequestID(SearchRequestID requestID) {
    return false;
  }

  public SearchQueryResults query(String cachename, LinkedList queryStack, boolean includeKeys,
                                  Set<String> attributeSet, Map<String, Boolean> sortAttributeMap,
                                  Map<String, String> attributeAggregatorMap) {
    return null;
  }

  public void initializeHandshake(NodeID thisNode, NodeID remoteNode, ClientHandshakeMessage handshakeMessage) {
    //
  }

  public void pause(NodeID remoteNode, int disconnected) {
    //
  }

  public void shutdown() {
    //
  }

  public void unpause(NodeID remoteNode, int disconnected) {
    //
  }

}
