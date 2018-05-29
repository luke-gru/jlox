package com.craftinginterpreters.test;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
//import org.junit.BeforeClass;
import com.craftinginterpreters.lox.Interpreter;
import com.craftinginterpreters.lox.Lox;

public class InterpreterTest {
    private Interpreter interp = null;

    @Test
    public void testEmptySrc() {
        String code = "";
        String output = runCode(code);
        assertEquals("", output);
    }

    @Test
    public void testNumberAddition() {
        String code = "print 1.1+1.0;";
        String output = runCode(code);
        assertEquals("2.1\n", output);
    }

    @Test
    public void testStringAddition() {
        String code = "print \"1\"+\"1\";";
        String output = runCode(code);
        assertEquals("11\n", output);
    }

    @Test
    public void testTruthiness() {
        String code = "print !!nil;\n" +
                      "print !!false;\n" +
                      "print !!true;\n" +
                      "print !!0;";
        String output = runCode(code);
        String expected = "false\n" +
                          "false\n" +
                          "true\n" +
                          "true\n";
        assertEquals(expected, output);
    }

    @Test
    public void testTopLevelVariablesResolve() {
        String code = "var i = 1.1; print i;";
        String output = runCode(code);
        String expected = "1.1\n";
        assertEquals(expected, output);
    }

    @Test
    public void testSimpleFunction() {
        String code = "fun add(a,b) { return a+b; }\nprint add(3,4);";
        String output = runCode(code);
        String expected = "7\n";
        assertEquals(expected, output);
    }

    @Test
    public void testFunctionsAsValues() {
        String code = "fun add(a,b) { return a+b; }\nvar add2 = add; print add2(3,4);";
        String output = runCode(code);
        String expected = "7\n";
        assertEquals(expected, output);
    }

    @Test
    public void testUncaughtError() {
        String code =  "throw \"UH OH\";";
        String output = runCode(code);
        String expected = "!error!";
        assertEquals(expected, output);
        assertUncaughtError();
    }

    @Test
    public void testSimpleIf() {
        // 0 is truthy
        String code = "if (0) { print \"It's alive!\"; } else { print \"Woops!\"; }";
        String output = runCode(code);
        String expected = "It's alive!\n";
        assertEquals(expected, output);
    }

    @Test
    public void testBuiltinObjectClass() {
        String code = "var o = Object(); print o;";
        String output = runCode(code);
        String pattern = "<instance Object #";
        assertMatches(pattern, output);
    }

    @Test
    public void testSettingPropertiesOnObjects() {
        String code = "var o = Object(); o.size = 2; print o.size;";
        String output = runCode(code);
        String expected = "2\n";
        assertEquals(expected, output);

    }

    @Test
    public void testBuiltinFunctionTypeOf() {
        String code = "print typeof(1); print typeof(\"str\");";
        String output = runCode(code);
        String expected = "number\nstring\n";
        assertEquals(expected, output);

        String code2 = "print typeof([]); print typeof(Object());";
        String output2 = runCode(code2);
        String expected2 = "array\ninstance\n";
        assertEquals(expected2, output2);

        String code3 = "print typeof(Object); print typeof(fun() { });";
        String output3 = runCode(code3);
        String expected3 = "class\nfunction\n";
        assertEquals(expected3, output3);

        String code4 = "print typeof(nil); print typeof(true);";
        String output4 = runCode(code4);
        String expected4 = "nil\nbool\n";
        assertEquals(expected4, output4);
    }

    @Test
    public void testBuiltinObjectMethod__class() {
        String code = "var o = Object();\n" +
                      "print o._class;";
        String output = runCode(code);
        String expected = "<class Object>\n";
        assertEquals(expected, output);
    }

    @Test
    public void testBuiltinObjectMethod_len() {
        String code1 = "print len([1,2,3]);";
        String output1 = runCode(code1);
        String expected1 = "3\n";
        assertEquals(expected1, output1);

        String code2 = "print len(\"gracie\");";
        String output2 = runCode(code2);
        String expected2 = "6\n";
        assertEquals(expected2, output2);

        String code3 = "fun add(a, b) { return a+b; }\n" +
                       "var arity = len(add); print arity;";
        String output3 = runCode(code3);
        String expected3 = "2\n";
        assertEquals(expected3, output3);

        String code4 = "var o = Object(); o.length = 5; print len(o);";
        String output4 = runCode(code4);
        String expected4 = "5\n";
        assertEquals(expected4, output4);
    }

    @Test
    public void testClosuresCaptureEnvironment() {
        String code = "fun multiplier(by) {\n" +
                      "  return fun(n) { return by*n; };" +
                      "}\n" +
                      "var tenTimes = multiplier(10);\n" +
                      "var fiveTimes = multiplier(5);\n" +
                      "print tenTimes(3); print fiveTimes(3);";
        String output = runCode(code);
        String expected = "30\n15\n";
        assertEquals(expected, output);
    }

    @Test
    public void testSimpleClass() {
        String code = "class Customer {\n" +
                      "  init(id) { this._id = id; }\n" +
                      "  getId { return this._id; }\n" +
                      "}\n" +
                      "var c = Customer(10);\n" +
                      "print c.getId; print c._class;";
        String output = runCode(code);
        String expected = "10\n<class Customer>\n";
        assertEquals(expected, output);
    }

    @Test
    public void testExampleFiles() throws IOException {
        File folder = new File("../../../examples");
        File[] listOfFiles = folder.listFiles();
        System.err.println("Example files: " + String.valueOf(listOfFiles.length));
        if (listOfFiles.length == 0) {
            assertEquals("example directory should contain files", "ERROR");
        }
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                String fname = listOfFiles[i].getPath();
                String fnameAbs = listOfFiles[i].getCanonicalPath();
                BufferedReader br = new BufferedReader(new FileReader(fname));
                System.err.println("Running example file " + fname);
                Lox.registerInitialScript(fnameAbs);
                try {
                    StringBuilder sbSrc = new StringBuilder();
                    StringBuilder sbExpected = new StringBuilder();
                    boolean inEnd = false;
                    boolean inExpected = false;
                    boolean expectRuntimeError = false;
                    boolean expectUncaughtThrow = false;
                    boolean expectParseError = false;
                    boolean skipFile = false;
                    String line = br.readLine();
                    while (line != null) {
                        if (line.matches("__END__")) {
                            inEnd = true;
                            line = br.readLine();
                            continue;
                        } else if (inEnd && line.matches("-- expect: --")) {
                            inExpected = true;
                            line = br.readLine();
                            continue;
                        } else if (inEnd && line.matches("-- noexpect: --")) {
                            skipFile = true;
                            break;
                        } else if (inEnd && line.matches("-- expect RuntimeError: --")) {
                            expectRuntimeError = true;
                            inExpected = true;
                            line = br.readLine();
                            continue;
                        } else if (inEnd && line.matches("-- expect UncaughtThrow: --")) {
                            expectUncaughtThrow = true;
                            inExpected = true;
                            line = br.readLine();
                            continue;
                        } else if (inEnd && line.matches("-- expect ParseError: --")) {
                            expectParseError = true;
                            break;
                        }
                        if (inExpected) {
                            sbExpected.append(line);
                            sbExpected.append(System.lineSeparator());
                        } else if (!inEnd) {
                            sbSrc.append(line);
                            sbSrc.append(System.lineSeparator());
                        } else {
                            throw new RuntimeException("should be unreachable");
                        }
                        line = br.readLine();
                    }
                    if (skipFile) {
                        System.err.println("  (Skipping example file " + fname + ")");
                        continue;
                    }
                    assertTrue(inEnd);
                    String src = sbSrc.toString();
                    String expected = sbExpected.toString();
                    String output = runCode(src);
                    if (expectRuntimeError) {
                        boolean gotErr = assertRuntimeError();
                        if (!gotErr) {
                            System.err.println("F");
                            continue;
                        }
                        if (expected.length() != 0) {
                            assertEquals(expected, this.interp.printBuf.toString());
                            if (expected.equals(this.interp.printBuf.toString())) {
                                System.err.println(".");
                            } else {
                                System.err.println("F");
                            }
                        }
                    } else if (expectUncaughtThrow) {
                        boolean gotUncaughtThrow = assertUncaughtError();
                        if (!gotUncaughtThrow) {
                            System.err.println("F");
                            continue;
                        }
                        if (expected.length() != 0) {
                            assertEquals(expected, this.interp.printBuf.toString());
                            if (expected.equals(this.interp.printBuf.toString())) {
                                System.err.println(".");
                            } else {
                                System.err.println("F");
                            }
                        }
                    } else if (expectParseError) {
                        boolean gotParseError = assertParseError();
                        if (gotParseError) {
                            System.err.println(".");
                        } else {
                            System.err.println("F");
                        }
                    } else {
                        assertEquals(expected, output);
                        if (expected.equals(output)) {
                            System.err.println(".");
                        } else {
                            System.err.println("F");
                        }
                    }
                } finally {
                    br.close();
                }
            }
        }

        Lox.initialScriptAbsolute = null;

    }

    private String runCode(String src) {
        HashMap<String, Object> opts = new HashMap<>();
        opts.put("usePrintBuf", (Boolean)true);
        opts.put("useErrorBuf", (Boolean)true);
        opts.put("filename", null);
        this.interp = new Interpreter(opts);
        boolean hasErr = !this.interp.interpret(src);
        if (hasErr) {
            return "!error!";
        } else {
            return this.interp.printBuf.toString();
        }
    }

    private boolean assertUncaughtError() {
        assertTrue(this.interp.hasUncaughtException());
        return this.interp.hasUncaughtException();
    }

    private boolean assertRuntimeError() {
        assertTrue(this.interp.hasRuntimeError());
        return this.interp.hasRuntimeError();
    }

    private boolean assertParseError() {
        assertTrue(this.interp.hasParseError());
        return this.interp.hasParseError();
    }

    private boolean assertMatches(String pattern, String output) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(output);
        if (m.find()) {
            assertTrue(true);
            return true;
        } else {
            assertEquals(pattern, output); // to show the pattern on failure
            return false;
        }
    }

}
