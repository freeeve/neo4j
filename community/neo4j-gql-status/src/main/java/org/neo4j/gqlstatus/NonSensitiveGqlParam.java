/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.gqlstatus;

import java.util.List;

public class NonSensitiveGqlParam implements GqlParams.GqlParam {
    private final GqlParams.GqlParam param;

    // While this is currently unused,
    // it is required to make sure developers consider why a parameter is non-sensitive.
    private final List<NonSensitiveReason> reasons;

    NonSensitiveGqlParam(GqlParams.GqlParam param, List<NonSensitiveReason> reasons) {
        this.param = param;
        this.reasons = reasons;
    }

    @Override
    public String name() {
        return param.name();
    }

    @Override
    public String process(Object s) {
        return param.process(s);
    }

    public GqlParams.GqlParam getInnerParam() {
        return param;
    }
}
