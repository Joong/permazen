
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.cli.cmd;

import java.util.EnumSet;
import java.util.Map;

import org.jsimpledb.SessionMode;
import org.jsimpledb.cli.CliSession;
import org.jsimpledb.parse.expr.Node;
import org.jsimpledb.util.ParseContext;

public class RegisterCommandCommand extends AbstractCommand {

    public RegisterCommandCommand() {
        super("register-command class:expr");
    }

    @Override
    public String getHelpSummary() {
        return "Instantiates a user-supplied class implementing the Command interface and registers it as an available command.";
    }

    @Override
    public EnumSet<SessionMode> getSessionModes() {
        return EnumSet.allOf(SessionMode.class);
    }

    @Override
    public CliSession.Action getAction(CliSession session, ParseContext ctx, boolean complete, Map<String, Object> params) {
        final Node expr = (Node)params.get("class");
        return new CliSession.Action() {
            @Override
            public void run(CliSession session) throws Exception {
                final Object result = expr.evaluate(session).get(session);
                if (!(result instanceof Class))
                    throw new Exception("invalid parameter: not a " + Class.class.getName() + " instance");
                final Class<?> cl = (Class<?>)result;
                if (!Command.class.isAssignableFrom(cl))
                    throw new Exception("invalid parameter: " + cl + " does not implement " + Command.class);
                final Command command = cl.asSubclass(Command.class).getConstructor().newInstance();
                session.registerCommand(command);
                session.getWriter().println("Registered command `" + command.getName() + "'");
            }
        };
    }
}

