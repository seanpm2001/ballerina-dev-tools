/*
 *  Copyright (c) 2023, WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.workermodelgenerator.core.analyzer;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.BinaryExpressionNode;
import io.ballerina.compiler.syntax.tree.BracedExpressionNode;
import io.ballerina.compiler.syntax.tree.ElseBlockNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.IfElseStatementNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.StatementNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.workermodelgenerator.core.NodeBuilder;
import io.ballerina.workermodelgenerator.core.model.CodeLocation;
import io.ballerina.workermodelgenerator.core.model.properties.BalExpression;
import io.ballerina.workermodelgenerator.core.model.properties.NodeProperties;
import io.ballerina.workermodelgenerator.core.model.properties.SwitchCase;
import io.ballerina.workermodelgenerator.core.model.properties.SwitchDefaultCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Syntax tree analyzer to obtain information from a switch node.
 *
 * @since 2201.9.0
 */
public class SwitchAnalyzer extends Analyzer {

    private boolean processDefaultCase;
    private BalExpression expression;
    private final Map<BalExpression, List<String>> expressionToNodesMapper;
    private final List<String> defaultSwitchCaseNodes;

    public SwitchAnalyzer(NodeBuilder nodeBuilder,
                          SemanticModel semanticModel, ModulePartNode modulePartNode, Map<String, String> endpointMap) {
        super(nodeBuilder, semanticModel, modulePartNode, endpointMap);
        this.processDefaultCase = false;
        this.expressionToNodesMapper = new LinkedHashMap<>();
        this.defaultSwitchCaseNodes = new ArrayList<>();
    }

    @Override
    protected void analyzeSendAction(SimpleNameReferenceNode receiverNode, ExpressionNode expressionNode) {
        super.analyzeSendAction(receiverNode, expressionNode);
        String portIdStr = getPortId();
        if (processDefaultCase) {
            addDefaultSwitchCase(portIdStr);
            return;
        }
        addSwitchCase(portIdStr);
    }

    @Override
    public void visit(IfElseStatementNode ifElseStatementNode) {
        ifElseStatementNode.condition().accept(this);
        ifElseStatementNode.ifBody().accept(this);
        ifElseStatementNode.elseBody().ifPresent(elseBody -> elseBody.accept(this));
    }

    @Override
    public void visit(ElseBlockNode elseBlockNode) {
        StatementNode elseBody = elseBlockNode.elseBody();
        this.processDefaultCase = elseBody.kind() == SyntaxKind.BLOCK_STATEMENT;
        elseBody.accept(this);
    }

    @Override
    public void visit(BracedExpressionNode bracedExpressionNode) {
        initializeExpressionMap(bracedExpressionNode.expression());
    }

    @Override
    public void visit(BinaryExpressionNode binaryExpressionNode) {
        initializeExpressionMap(binaryExpressionNode);
    }

    private void addDefaultSwitchCase(String node) {
        this.defaultSwitchCaseNodes.add(node);
    }

    private void addSwitchCase(String node) {
        List<String> currentNodes = this.expressionToNodesMapper.get(this.expression);
        currentNodes.add(node);
    }

    private void initializeExpressionMap(ExpressionNode expressionNode) {
        this.expression = new BalExpression(expressionNode.toSourceCode(),
                new CodeLocation(expressionNode.lineRange().startLine(), expressionNode.lineRange().endLine()));
        this.expressionToNodesMapper.put(this.expression, new ArrayList<>());
    }

    @Override
    public NodeProperties buildProperties() {
        //TODO: Handle the error case when there are no conditional statements.
        List<SwitchCase> switchCases = new ArrayList<>();
        for (Map.Entry<BalExpression, List<String>> switchCaseEntry : this.expressionToNodesMapper.entrySet()) {
            switchCases.add(new SwitchCase(switchCaseEntry.getKey(), switchCaseEntry.getValue()));
        }
        NodeProperties.NodePropertiesBuilder nodePropertiesBuilder = new NodeProperties.NodePropertiesBuilder();
        return nodePropertiesBuilder
                .setSwitchCases(switchCases)
                .setDefaultSwitchCase(new SwitchDefaultCase(this.defaultSwitchCaseNodes))
                .build();

    }
}
