/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.Category.JDK;
import static com.google.errorprone.BugPattern.MaturityLevel.MATURE;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.enclosingPackage;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;

import com.google.common.base.Optional;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;

import javax.lang.model.element.ElementKind;

/**
 * @author eaftan@google.com (Eddie Aftandilian)
 */
@BugPattern(name = "CheckReturnValue",
    altNames = {"ResultOfMethodCallIgnored", "ReturnValueIgnored"},
    summary = "Ignored return value of method that is annotated with @CheckReturnValue",
    category = JDK, severity = ERROR, maturity = MATURE)
public class CheckReturnValue extends AbstractReturnValueIgnored
    implements MethodTreeMatcher, ClassTreeMatcher {

  private static Optional<Boolean> shouldCheckReturnValue(Symbol sym) {
    if (hasAnnotation(sym, CanIgnoreReturnValue.class)) {
      return Optional.of(false);
    }
    if (hasAnnotation(sym, javax.annotation.CheckReturnValue.class)) {
      return Optional.of(true);
    }
    return Optional.absent();
  }

  private static Optional<Boolean> checkEnclosingClasses(MethodSymbol method) {
    Symbol enclosingClass = enclosingClass(method);
    while (enclosingClass instanceof ClassSymbol) {
      Optional<Boolean> result = shouldCheckReturnValue(enclosingClass);
      if (result.isPresent()) {
        return result;
      }
      enclosingClass = enclosingClass.owner;
    }
    return Optional.absent();
  }

  private static Optional<Boolean> checkPackage(MethodSymbol method) {
    return shouldCheckReturnValue(enclosingPackage(method));
  }

  private static final Matcher<MethodInvocationTree> MATCHER =
      new Matcher<MethodInvocationTree>() {
        @Override
        public boolean matches(MethodInvocationTree tree, VisitorState state) {

          MethodSymbol method = ASTHelpers.getSymbol(tree);
          if (ASTHelpers.isVoidType(method.getReturnType(), state)) {
            return false;
          }

          Optional<Boolean> result = shouldCheckReturnValue(method);
          if (result.isPresent()) {
            return result.get();
          }

          result = checkEnclosingClasses(method);
          if (result.isPresent()) {
            return result.get();
          }

          result = checkPackage(method);
          if (result.isPresent()) {
            return result.get();
          }

          return false;
        }
      };

  /**
   * Return a matcher for method invocations in which the method being called has the
   * {@code @CheckReturnValue} annotation.
   */
  @Override
  public Matcher<MethodInvocationTree> specializedMatcher() {
    return MATCHER;
  }

  private static final String BOTH_ERROR =
      "@CheckReturnValue and @CanIgnoreReturnValue cannot both be applied to the same %s";

  /**
   * Validate {@link javax.annotation.CheckReturnValue} and {@link CanIgnoreReturnValue} usage on
   * methods.
   *
   * <p>The annotations should not both be appled to the same method.
   *
   * <p>The annotations should not be applied to void-returning methods. Doing so makes no sense,
   * because there is no return value to check.
   */
  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    MethodSymbol method = ASTHelpers.getSymbol(tree);

    boolean checkReturn = hasAnnotation(method, javax.annotation.CheckReturnValue.class);
    boolean canIgnore = hasAnnotation(method, CanIgnoreReturnValue.class);

    if (checkReturn && canIgnore) {
      return buildDescription(tree).setMessage(String.format(BOTH_ERROR, "method")).build();
    }

    String annotationToValidate;
    if (checkReturn) {
      annotationToValidate = javax.annotation.CheckReturnValue.class.getSimpleName();
    } else if (canIgnore) {
      annotationToValidate = CanIgnoreReturnValue.class.getSimpleName();
    } else {
      return Description.NO_MATCH;
    }
    if (method.getKind() != ElementKind.METHOD) {
      // skip contructors (which javac thinks are void-returning)
      return Description.NO_MATCH;
    }
    if (!ASTHelpers.isVoidType(method.getReturnType(), state)) {
      return Description.NO_MATCH;
    }
    String message =
        String.format("@%s may not be applied to void-returning methods", annotationToValidate);
    return buildDescription(tree).setMessage(message).build();
  }

  /**
   * Validate that at most one of {@link javax.annotation.CheckReturnValue} and
   * {@link CanIgnoreReturnValue} are applied to a class (or interface or enum).
   */
  @Override
  public Description matchClass(ClassTree tree, VisitorState state) {
    if (hasAnnotation(tree, javax.annotation.CheckReturnValue.class)
        && hasAnnotation(tree, CanIgnoreReturnValue.class)) {
      return buildDescription(tree).setMessage(String.format(BOTH_ERROR, "class")).build();
    }
    return Description.NO_MATCH;
  }
}
