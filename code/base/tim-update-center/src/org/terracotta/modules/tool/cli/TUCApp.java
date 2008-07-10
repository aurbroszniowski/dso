/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package org.terracotta.modules.tool.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.terracotta.modules.tool.commands.Loader;

import com.tc.util.ResourceBundleHelper;

import java.util.Arrays;
import java.util.List;

public class TUCApp {

  private static final ResourceBundleHelper bundleHelper = new ResourceBundleHelper(TUCApp.class);

  public static void main(String args[]) {
    Options options = new Options();
    options.addOption("h", "help", false, bundleHelper.getString("options.help"));
    try {
      CommandLineParser parser = new GnuParser();
      CommandLine cli = parser.parse(options, args);

      List<String> argList = cli.getArgList();
      String cmdname = argList.isEmpty() ? "help" : (String) argList.remove(0);

      List<String> cmdargs = Arrays.asList(args);
      if (!cmdargs.isEmpty()) cmdargs = cmdargs.subList(1, cmdargs.size());
      
      new Loader(cmdname, cmdargs.toArray(new String[0]));      
    } catch (Exception e) {
      System.err.println(e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

}
