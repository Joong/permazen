
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package io.permazen.parse.expr;

/**
 * Parses shift expressions of the form {@code x << y}, {@code x >> y}, and {@code x >>> y}.
 */
public class ShiftExprParser extends BinaryExprParser {

    public static final ShiftExprParser INSTANCE = new ShiftExprParser();

    public ShiftExprParser() {
        super(AdditiveExprParser.INSTANCE, Op.LSHIFT, Op.URSHIFT, Op.RSHIFT);
    }
}

