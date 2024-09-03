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
package com.tc.text;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ConsoleParagraphFormatter implements ParagraphFormatter {

  private final StringFormatter sf;
  private final int             maxWidth;

  public ConsoleParagraphFormatter(int maxWidth, StringFormatter stringFormatter) {
    this.maxWidth = maxWidth;
    this.sf = stringFormatter;
  }

  @Override
  public String format(String in) {
    StringBuffer buf = new StringBuffer();
    if (in == null) throw new AssertionError();
    List words = parseWords(in);
    int lineWidth = 0;
    for (Iterator i = words.iterator(); i.hasNext();) {
      String currentWord = (String) i.next();
      if (lineWidth + currentWord.length() > maxWidth) {
        if (lineWidth > 0) {
          buf.append(sf.newline());
        }
        lineWidth = currentWord.length();
      } else {
        if (lineWidth > 0) {
          buf.append(" ");
        }
        lineWidth += currentWord.length();
      }
      buf.append(currentWord);
    }
    return buf.toString();
  }

  private List parseWords(String in) {
    String[] words = in.split("\\s+");
    return Arrays.asList(words);
  }

}
