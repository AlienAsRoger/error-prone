/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

package com.google.errorprone.refactors.objects_equal_self_comparison;

import static com.google.errorprone.BugPattern.Category.GUAVA;
import static com.google.errorprone.BugPattern.MaturityLevel.ON_BY_DEFAULT;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.methodSelect;
import static com.google.errorprone.matchers.Matchers.sameArgument;
import static com.google.errorprone.matchers.Matchers.staticMethod;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.refactors.RefactoringMatcher;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;

/**
 * @author alexeagle@google.com (Alex Eagle)
 */
@BugPattern(
    name = "Objects equals self comparison",
    category = GUAVA,
    severity = ERROR,
    maturity = ON_BY_DEFAULT,
    summary = "Objects.equals() used to compare object to itself",
    explanation =
        "The two arguments to Objects.equals() are the same object, so this call " +
        "always returns true.")
public class ObjectsEqualSelfComparison extends RefactoringMatcher<MethodInvocationTree> {

  @SuppressWarnings({"unchecked"})
  @Override
  public boolean matches(MethodInvocationTree methodInvocationTree, VisitorState state) {
    return allOf(
        methodSelect(staticMethod("com.google.common.base.Objects", "equal")),
        sameArgument(0, 1))
        .matches(methodInvocationTree, state);
  }

  @Override
  public Refactor refactor(MethodInvocationTree methodInvocationTree, VisitorState state) {
    // If we don't find a good field to use, then just replace with "true"
    SuggestedFix fix = new SuggestedFix().replace(methodInvocationTree, "true");

    JCExpression toReplace = (JCExpression) methodInvocationTree.getArguments().get(1);
    // Find containing block
    TreePath path = state.getPath();
    while(path.getLeaf().getKind() != Kind.BLOCK) {
      path = path.getParentPath();
    }
    JCBlock block = (JCBlock)path.getLeaf();
    for (JCStatement jcStatement : block.getStatements()) {
      if (jcStatement.getKind() == Kind.VARIABLE) {
        JCVariableDecl declaration = (JCVariableDecl) jcStatement;
        TypeSymbol variableTypeSymbol = declaration.getType().type.tsym;

        if (((JCIdent)toReplace).sym.isMemberOf(variableTypeSymbol, state.getTypes())) {
          fix = new SuggestedFix().prefixWith(toReplace, declaration.getName().toString() + ".");
        }
      }
    }

    return new Refactor(methodInvocationTree,
        "Objects.equal arguments must be different", fix);
  }

  public static class Scanner extends com.google.errorprone.Scanner {
    private final RefactoringMatcher<MethodInvocationTree> matcher =
        new ObjectsEqualSelfComparison();

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, VisitorState visitorState) {
      VisitorState state = visitorState.withPath(getCurrentPath());
      if (matcher.matches(node, state)) {
        reportMatch(matcher, node, state);
      }
      return null;
    }
  }
}
