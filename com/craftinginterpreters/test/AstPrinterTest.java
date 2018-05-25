package com.craftinginterpreters.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;
import org.junit.BeforeClass;
import com.craftinginterpreters.lox.AstPrinter;

public class AstPrinterTest {

    @BeforeClass
    public static void beforeAll() {
        AstPrinter.silenceErrorOutput = true;
    }

    @Test
    public void testEmpty() {
        String code = "";
        String ast = AstPrinter.print(code);
        assertEquals("", ast);
    }

    @Test
    public void testBinaryExpr() {
        String code = "1+1;";
        String ast = AstPrinter.print(code);
        assertEquals("(+ 1.0 1.0)\n", ast);
    }

    @Test
    public void testLogicalExpr() {
        String code = "var i = true and false;";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i (and true false))\n", ast);
    }

    @Test
    public void testErrorNoSemiColon() {
        String code = "1+1";
        String ast = AstPrinter.print(code);
        assertEquals("!error!", ast);
    }

    @Test
    public void testSimpleIfStmt() {
        String code = "if (true) { " +
                      "} else { } ";

        String ast = AstPrinter.print(code);
        assertEquals("(if true\n" +
                     "  (block)\n" +
                     "  (block)\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testSimpleWhileStmt() {
        String code = "while (true) {}";

        String ast = AstPrinter.print(code);
        assertEquals("(while true\n" +
                     "  (block)\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testSimpleForStmt() {
        String code = "for (var i = 0; i < 100; i = i + 1) { }";
        String ast = AstPrinter.print(code);
        assertEquals("(for (varDecl i 0.0) (< (var i) 100.0) (assign i (+ (var i) 1.0))\n" +
                     "  (block)\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testAllNopForStmt() {
        String code = "for (;;) {}";
        String ast = AstPrinter.print(code);
        assertEquals("(for (nop) (nop) (nop)\n" +
                     "  (block)\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testTryStmt() {
        String code = "try { someFunc(); } catch (\"Err\" err) { print \"hi\"; }";
        String ast = AstPrinter.print(code);
        assertEquals("(try\n" +
                     "  (block\n" +
                     "    (call (var someFunc))\n" +
                     "  )\n" +
                     "  (catch \"Err\" (var err)\n" +
                     "    (block\n" +
                     "      (print \"hi\")\n" +
                     "    )\n" +
                     "  )\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testThrowStringExpr() {
        String code = "throw \"Error\";";
        String ast = AstPrinter.print(code);
        assertEquals("(throw \"Error\")\n", ast);
    }

    @Test
    public void testThrowAnyExpr() {
        String code = "throw expr();";
        String ast = AstPrinter.print(code);
        assertEquals("(throw (call (var expr)))\n", ast);
    }

    @Test
    public void testBreakOnlyInLoopsError() {
        String code = "break;";
        String ast = AstPrinter.print(code);
        assertParseError(ast);
    }

    @Test
    public void testContinueOnlyInLoopsError() {
        String code = "continue;";
        String ast = AstPrinter.print(code);
        assertParseError(ast);
    }

    @Test
    public void testBreakInWhileLoop() {
        String code = "while (true) { break; }";
        String ast = AstPrinter.print(code);
        assertNotParseError(ast);
    }

    @Test
    public void testVarDecl() {
        String code = "var i = 0.0;";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i 0.0)\n", ast);
    }

    @Test
    public void testVarAssign() {
        String code = "var i; i = 0.0;";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i)\n(assign i 0.0)\n", ast);
    }

    @Test
    public void testSimpleCallExprWithArgs() {
        String code = "func(1, 2);";
        String ast = AstPrinter.print(code);
        assertEquals("(call (var func) 1.0 2.0)\n", ast);
    }

    @Test
    public void testSimpleCallExprWithoutArgs() {
        String code = "func();";
        String ast = AstPrinter.print(code);
        assertEquals("(call (var func))\n", ast);
    }

    @Test
    public void testSplatCallExpr() {
        String code = "func(1, 2, *expr);";
        String ast = AstPrinter.print(code);
        assertEquals("(call (var func) 1.0 2.0 *(var expr))\n", ast);
    }

    @Test
    public void testAnonFn() {
        String code = "var i = fun() { };";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i (fnAnon\n" +
                     "  (block)\n" +
                     "))\n",
                     ast);
    }

    @Test
    public void testEmptyFunDecl() {
        String code = "fun myFunc() { }";
        String ast = AstPrinter.print(code);
        assertEquals("(fnDecl myFunc\n" +
                     "  (block)\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testNonEmptyFunDecl() {
        String code = "fun two() {\n" +
                      "  return 2;" +
                      "}";
        String ast = AstPrinter.print(code);
        assertEquals("(fnDecl two\n" +
                     "  (block\n" +
                     "    (return 2.0)\n" +
                     "  )\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testFunDeclWithSplatParam() {
        String code = "fun printArgs(*args) {\n" +
                      "  for (var i = 0; i < args.size; i = i + 1) { print i; }" +
                      "}";
        String ast = AstPrinter.print(code);
        assertEquals("(fnDecl printArgs *args\n" +
                     "  (block\n" +
                     "    (for (varDecl i 0.0) (< (var i) (prop (var args) size)) (assign i (+ (var i) 1.0))\n" +
                     "      (block\n" +
                     "        (print (var i))\n" +
                     "      )\n" +
                     "    )\n" +
                     "  )\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testEmptyClassDecl() {
        String code = "class Invoice { }";
        String ast = AstPrinter.print(code);
        assertEquals("(classDecl Invoice)\n",
                     ast);
    }

    @Test
    public void testClassWithMethod() {
        String code = "class Invoice {\n" +
                      "  amount() { return 100.00; }" +
                      "}";
        String ast = AstPrinter.print(code);
        assertEquals("(classDecl Invoice\n" +
                     "  (fnDecl amount\n" +
                     "    (block\n" +
                     "      (return 100.0)\n" +
                     "    )\n" +
                     "  )\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testClassWithGetterMethod() {
        String code = "class Invoice {\n" +
                      "  amount { return 100.00; }" +
                      "}";
        String ast = AstPrinter.print(code);
        assertEquals("(classDecl Invoice\n" +
                     "  (getter amount\n" +
                     "    (block\n" +
                     "      (return 100.0)\n" +
                     "    )\n" +
                     "  )\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testClassWithSetterMethod() {
        String code = "class Invoice {\n" +
                      "  amount=(amt) { this.amt = amt; }\n" +
                      "}";
        String ast = AstPrinter.print(code);
        assertEquals("(classDecl Invoice\n" +
                     "  (setter amount amt\n" +
                     "    (block\n" +
                     "      (propSet (this) amt (var amt))\n" +
                     "    )\n" +
                     "  )\n" +
                     ")\n",
                     ast);
    }

    @Test
    public void testNil() {
        String code = "var i = nil;";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i nil)\n", ast);
    }

    @Test
    public void testArrayLiterals() {
        String code1 = "var a = [1,2,3];";
        String code2 = "var a = [1,2,3,];";
        String code3 = "var a = [];";
        String code4 = "var a = [,];";
        String ast1 = AstPrinter.print(code1);
        String ast2 = AstPrinter.print(code2);
        String ast3 = AstPrinter.print(code3);
        String ast4 = AstPrinter.print(code4);
        assertEquals("(varDecl a (array 1.0 2.0 3.0))\n", ast1);
        assertEquals("(varDecl a (array 1.0 2.0 3.0))\n", ast2);
        assertEquals("(varDecl a (array))\n", ast3);
        assertEquals("(varDecl a (array))\n", ast4);
    }

    @Test
    public void testIndexGetExpr() {
        String code = "identifier[expr()];";
        String ast = AstPrinter.print(code);
        assertEquals("(indexedget (var identifier) (call (var expr)))\n", ast);

    }

    @Test
    public void testIndexSetExpr() {
        String code = "identifier[expr()] = nil;";
        String ast = AstPrinter.print(code);
        assertEquals("(indexedset (var identifier) (call (var expr)) nil)\n", ast);
    }

    @Test
    public void testUnaryMinus() {
        String code = "var i = -1.0;";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i (- 1.0))\n", ast);
    }

    @Test
    public void testUnaryNot() {
        String code = "var i = !0.0;";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i (! 0.0))\n", ast);
    }

    @Test
    public void testGroupingExpr() {
        String code = "var i = (0.0);";
        String ast = AstPrinter.print(code);
        assertEquals("(varDecl i (group 0.0))\n", ast);
    }


    private void assertParseError(String ast) {
        assertEquals("!error!", AstPrinter.PARSE_ERROR);
    }

    private void assertNotParseError(String ast) {
        assertNotEquals(ast, AstPrinter.PARSE_ERROR);
    }
}
