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
package com.tc.object.idprovider.api;

import com.tc.net.GroupID;
import com.tc.object.ObjectID;
import com.tc.object.tx.ClientTransaction;

/**
 * Responsible for generating the next unique objectID across processes in an efficient manner
 */
public interface ObjectIDProvider {

  public ObjectID next(ClientTransaction txn, Object pojo, GroupID gid);

  public void reserve(int size, GroupID gid);

}