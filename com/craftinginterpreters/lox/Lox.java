package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();

    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    public static boolean silenceParseErrors = false;
    public static boolean silenceRuntimeErrors = false;
    // load path from command-line "-L" flag. Also, current dir is always in
    // the initial load path.
    public static List<String> initialLoadPath = new ArrayList<>(); // list of directories to load scripts from. Ex: "/home/luke/projects/lox_scripts"
    public static String initialScriptAbsolute = null; // filename given to command-line -f flag
    public static Map<String, String> scriptsLoadedOnce = new HashMap<>();
    public static Map<String, String> scriptsLoaded = new HashMap<>();

    public static void main(String[] args) throws IOException {
        String fname = null;
        String loadPathStr = null;
        List<String> loadPathExtra = new ArrayList<>();
        int i = 0;

        while (i < args.length) {
            if (args[i].equals("-f")) {
                fname = args[i+1];
                i += 2;
            } else if (args[i].equals("-L")) {
                loadPathStr = args[i+1];
                i += 2;
            } else {
                System.err.println("Usage: Lox [-f FILENAME]");
                System.exit(1);
            }
        }

        if (loadPathStr != null) {
            String[] splitAry = loadPathStr.split(":");
            for (String path : splitAry) {
                loadPathExtra.add(path);
            }
        }
        initLoadPath(loadPathExtra);
        if (fname == null) {
            runPrompt();
        } else {
            runFile(fname);
        }
    }

    public static void initLoadPath(List<String> extraPaths) {
        String curDir = null;
        try {
            curDir = new File(".").getCanonicalPath();
        } catch (IOException e) {
        }
        if (curDir != null) {
            //System.err.println("curDir: " + curDir);
            initialLoadPath.add(curDir);
        }
        String stdlibPath = LoxUtil.classPath(Lox.class) + "/stdlib";
        extraPaths.add(stdlibPath);
        for (String path : extraPaths) {
            File f = new File(path);
            if (f.exists() && f.isDirectory()) {
                try {
                    initialLoadPath.add(f.getCanonicalPath());
                } catch (IOException e) {
                    System.err.println("Load path directory '" + path + "' not found. Ignoring...");
                }
            } else {
                System.err.println("Load path directory '" + path + "' not found. Ignoring...");
            }
        }
    }

    public static void initLoadPath() {
        initLoadPath(new ArrayList<String>());
    }

    // fname is relative or absolute, and the path may or may not exist on the
    // computer. The actual load path is given to this method, because it's
    // stored in lox-land as the `LOAD_PATH` global variable.
    public static String fullPathToScript(String fname, List<String> actualLoadPath) {
        File f = null;
        if (fname.charAt(0) == '/') {
            f = new File(fname);
            if (f.exists() && !f.isDirectory()) {
                return fname;
            } else {
                f = new File(fname + ".lox");
                if (f.exists() && !f.isDirectory()) {
                    return fname + ".lox";
                }
            }
        } else {
            for (String path : actualLoadPath) {
                f = new File(path + "/" + fname);
                if (f.exists() && !f.isDirectory()) {
                    return path + "/" + fname;
                } else {
                    f = new File(path + "/" + fname + ".lox");
                    if (f.exists() && !f.isDirectory()) {
                        return path + "/" + fname + ".lox";
                    }
                }
            }
        }
        return null;
    }

    public static boolean hasLoadedScriptOnce(String fullPath) {
        Iterator iter = scriptsLoadedOnce.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry pair = (Map.Entry)iter.next();
            if (pair.getValue().equals(fullPath)) {
                return true;
            }
        }
        return false;
    }

    public static void loadScriptOnceAdd(String pathGiven, String fullPath) {
        scriptsLoadedOnce.put(pathGiven, fullPath);
    }

    public static void loadScriptAdd(String pathGiven, String fullPath) {
        scriptsLoaded.put(pathGiven, fullPath);
    }

    private static void runFile(String path) throws IOException {
        registerInitialScript(path);
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        runSrc(new String(bytes, Charset.defaultCharset()));
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    public static void registerInitialScript(String path) {
        String absPath = (new File(path)).getAbsolutePath();
        initialScriptAbsolute = absPath;
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        Scanner scanner = new Scanner("");
        Parser parser;
        List<Token> tokens;
        List<Stmt> statements;
        System.out.print("> ");
        for (;;) {
            String line = reader.readLine();
            scanner.appendSrc(line);
            tokens = scanner.scanUntilEnd();
            if (scanner.inBlock > 0) {
                System.out.print("> ");
                for (int i = 0; i < scanner.inBlock; i++) {
                    System.out.print("  "); // indent
                }
            } else {
                scanner.addEOF();
                parser = new Parser(tokens);
                parser.setNativeClassNames(interpreter.runtime.nativeClassNames());
                statements = parser.parse();
                if (hadError) {
                    hadError = false;
                    scanner = new Scanner("");
                    System.out.print("> ");
                    continue;
                }
                runStmts(statements);
                scanner = new Scanner("");
                System.out.print("> ");
            }
            hadError = false;
        }
    }

    static void runSrc(String src) {
        Scanner scanner = new Scanner(src);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        interpreter.runtime.init(interpreter);
        parser.setNativeClassNames(interpreter.runtime.nativeClassNames());
        List<Stmt> statements = parser.parse();
        runStmts(statements);
    }

    private static void runStmts(List<Stmt> statements) {
        interpreter.interpret(statements);
    }

    // parse error
    static void error(Token tok, String message) {
        if (tok.type == TokenType.EOF) {
            report(tok.line, " at end", message);
        } else {
            report(tok.line, " at '" + tok.lexeme + "'", message);
        }
    }

    // parse error
    static void error(int line, String message) {
        if (!silenceParseErrors) {
            System.err.println("[line " + line + "] Error: " + message);
        }
        hadError = true;
    }

    static void runtimeError(RuntimeError error) {
        if (!silenceRuntimeErrors) {
            String lineStr = "";
            if (error.token != null) {
                lineStr = "\n[line " + error.token.line + "]";
            }
            System.err.println(error.getMessage() + lineStr);
        }
        hadRuntimeError = true;
    }

    // parse error
    private static void report(int line, String where, String message) {
        if (!silenceParseErrors) {
            System.err.println("[line " + line + "] Error" + where + ": " + message);
        }
        hadError = true;
    }
}
