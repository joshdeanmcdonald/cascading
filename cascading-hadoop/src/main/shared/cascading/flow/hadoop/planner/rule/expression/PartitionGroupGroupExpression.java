/*
 * Copyright (c) 2007-2014 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
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

package cascading.flow.hadoop.planner.rule.expression;

import cascading.flow.planner.iso.expression.ElementExpression;
import cascading.flow.planner.iso.expression.ExpressionGraph;
import cascading.flow.planner.iso.expression.FlowElementExpression;
import cascading.flow.planner.iso.expression.ScopeExpression;
import cascading.flow.planner.iso.expression.SyncPipeExpressionGraph;
import cascading.flow.planner.rule.RuleExpression;
import cascading.pipe.Group;
import cascading.pipe.Pipe;

/**
 *
 */
public class PartitionGroupGroupExpression extends RuleExpression
  {
  public PartitionGroupGroupExpression()
    {
    super(
      new SyncPipeExpressionGraph(),

      new ExpressionGraph()
        .arcs( new FlowElementExpression( Group.class ), new FlowElementExpression( Group.class ) ),

      new ExpressionGraph()
        .arc(
          new FlowElementExpression( ElementExpression.Capture.Primary, Pipe.class ),
          ScopeExpression.ANY,
          new FlowElementExpression( Group.class )
        )
    );
    }
  }
