/*
 * ================================================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved. This product is protected by
 * U.S. and international copyright and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 * ================================================================================================
 */
package org.apache.geode.management.internal.cli.shell;

import static org.apache.geode.management.internal.cli.i18n.CliStrings.LIST_MEMBER;
import static org.apache.geode.management.internal.cli.i18n.CliStrings.LIST_MEMBER__GROUP;
import static org.apache.geode.management.internal.cli.i18n.CliStrings.NO_MEMBERS_FOUND_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.ServerStarterRule;
import org.apache.geode.test.junit.categories.IntegrationTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;


@Category(IntegrationTest.class)
public class GfshMultilineCommandTest {

  @Rule
  public ServerStarterRule server = new ServerStarterRule().withJMXManager().startServer();


  @Rule
  public GfshShellConnectionRule gfsh = new GfshShellConnectionRule();

  @Test
  public void testMultiLineCommand() throws Exception {
    gfsh.connectAndVerify(server.getJmxPort(), GfshShellConnectionRule.PortType.jmxManger);
    // Execute a command
    CommandStringBuilder csb = new CommandStringBuilder(LIST_MEMBER);
    csb.addOption(LIST_MEMBER__GROUP, "nogroup");
    gfsh.executeAndVerifyCommand(csb.getCommandString());
    assertThat(gfsh.getGfshOutput().trim()).isEqualTo(NO_MEMBERS_FOUND_MESSAGE);

    // Now execute same command with a new Continuation on new line
    csb =
        new CommandStringBuilder(LIST_MEMBER).addNewLine().addOption(LIST_MEMBER__GROUP, "nogroup");
    gfsh.executeAndVerifyCommand(csb.getCommandString());
    assertThat(gfsh.getGfshOutput().trim()).isEqualTo(NO_MEMBERS_FOUND_MESSAGE);
  }

}
