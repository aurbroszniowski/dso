/*
 * All content copyright (c) 2003-2007 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.lang;

/**
 * The purpose of this class to execute a startup action (ie. "start the server", or "start the client", etc) in a
 * thread in the specified thread group. The side effect of doing this is that any more threads spawned by the startup
 * action will inherit the given thread group. It is somewhat fragile, and sometimes impossible (see java.util.Timer) to
 * be explicit about the thread group when spawning threads
 */
public class StartupHelper {

  private final StartupAction action;
  private final ThreadGroup threadGroup;

  public StartupHelper(ThreadGroup threadGroup, StartupAction action) {
    this.threadGroup = threadGroup;
    this.action = action;
  }

  public void startUp() {
    Runnable r = () -> {
      try {
        action.execute();
      } catch (Throwable t) {
        threadGroup.uncaughtException(Thread.currentThread(), t);
        throw new RuntimeException(t);
      }
    };

    Thread th = new Thread(threadGroup, r);
    th.start();

    try {
      th.join();
    } catch (InterruptedException ie) {
      throw new RuntimeException(ie);
    }
  }

  public interface StartupAction {
    void execute() throws Throwable;
  }

}
