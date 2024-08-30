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
package com.tc.stats.counter.sampled;

import java.util.concurrent.atomic.AtomicLong;

public class SampledCumulativeCounterImpl extends SampledCounterImpl implements SampledCumulativeCounter {

  private AtomicLong cumulativeCount;

  public SampledCumulativeCounterImpl(SampledCounterConfig config) {
    super(config);
    cumulativeCount = new AtomicLong(config.getInitialValue());
  }

  @Override
  public long getCumulativeValue() {
    if (resetOnSample) {
      return cumulativeCount.get();
    } else {
      return getValue();
    }
  }

  @Override
  public long decrement() {
    cumulativeCount.decrementAndGet();
    return super.decrement();
  }

  @Override
  public long decrement(long amount) {
    cumulativeCount.addAndGet(amount * -1);
    return super.decrement(amount);
  }

  @Override
  public long increment() {
    cumulativeCount.incrementAndGet();
    return super.increment();
  }

  @Override
  public long increment(long amount) {
    cumulativeCount.addAndGet(amount);
    return super.increment(amount);
  }

}
