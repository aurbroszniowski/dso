/*
 * Copyright Terracotta, Inc.
 * Copyright Super iPaaS Integration LLC, an IBM Company 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tc.objectserver.context;

import com.tc.async.api.EventContext;
import com.tc.net.ClientID;
import com.tc.object.ObjectRequestServerContext.LOOKUP_STATE;
import com.tc.util.ObjectIDSet;

import java.util.Collection;

public interface RespondToObjectRequestContext extends EventContext {

  public ClientID getRequestedNodeID();

  public Collection getObjs();

  public ObjectIDSet getRequestedObjectIDs();

  public ObjectIDSet getMissingObjectIDs();

  public LOOKUP_STATE getLookupState();

  public int getRequestDepth();
}
