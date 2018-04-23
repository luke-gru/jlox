package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();

    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        String fname = null;
        int i = 0;

        while (i < args.length) {
            if (args[i].equals("-f")) {
                fname = args[i+1];
                i = i+2;
            } else {
                System.err.println("Usage: Lox [-f FILENAME]");
                System.exit(1);
            }
        }

        if (fname == null) {
            runPrompt();
        } else {
            runFile(fname);
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        runSrc(new String(bytes, Charset.defaultCharset()));
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
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
        parser.setNativeClassNames(interpreter.runtime.nativeClassNames());
        List<Stmt> statements = parser.parse();
        runStmts(statements);
    }

    private static void runStmts(List<Stmt> statements) {
        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);
        if (hadError) {
            return;
        }
        interpreter.interpret(statements);
    }

    static void error(Token tok, String message) {
        if (tok.type == TokenType.EOF) {
            report(tok.line, " at end", message);
        } else {
            report(tok.line, " at '" + tok.lexeme + "'", message);
        }
    }

    static void error(int line, String message) {
        System.err.println("[line " + line + "] Error: " + message);
        hadError = true;
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
