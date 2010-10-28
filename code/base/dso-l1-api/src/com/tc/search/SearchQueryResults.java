/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.search;

import java.util.List;

public interface SearchQueryResults {

  public List<SearchQueryResult> getResults();

  public List<Integer> getAggregatorResults();

}
